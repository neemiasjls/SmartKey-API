package com.smartkey.domain.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da verificação de assinatura.
 *
 * Aqui simulamos o que o celular faz: geramos um par de chaves, assinamos uma
 * mensagem com a chave privada e conferimos com a chave pública.
 */
@DisplayName("Verificação de assinatura")
class SignatureVerifierTest {

    private static KeyPair parDoJoao;
    private static KeyPair parDeOutraPessoa;

    @BeforeAll
    static void gerarChaves() throws Exception {
        parDoJoao = gerarParEcdsaP256();
        parDeOutraPessoa = gerarParEcdsaP256();
    }

    private static KeyPair gerarParEcdsaP256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String publicKeyBase64(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    /** Assina no formato DER, que é o que o Android Keystore produz. */
    private static String assinarDer(KeyPair pair, byte[] message) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(message);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /** Converte DER para o formato cru de 64 bytes, que é o que o navegador produz. */
    private static String derParaCru(String derBase64) {
        byte[] der = Base64.getDecoder().decode(derBase64);

        // SEQUENCE(30) len INTEGER(02) lenR R INTEGER(02) lenS S
        int i = 2;
        if ((der[1] & 0xFF) > 0x80) {
            i += (der[1] & 0x7F);   // comprimento em formato longo
        }
        i++;                        // pula o marcador do primeiro INTEGER
        int lenR = der[i++] & 0xFF;
        byte[] r = Arrays.copyOfRange(der, i, i + lenR);
        i += lenR;
        i++;                        // pula o marcador do segundo INTEGER
        int lenS = der[i++] & 0xFF;
        byte[] s = Arrays.copyOfRange(der, i, i + lenS);

        byte[] raw = new byte[64];
        copiarAlinhadoADireita(new BigInteger(1, r), raw, 0);
        copiarAlinhadoADireita(new BigInteger(1, s), raw, 32);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static void copiarAlinhadoADireita(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }

    // ==================================================================

    private static final byte[] MENSAGEM = SignedMessage.bytes(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "bm9uY2UtZGUtdGVzdGU=",
            "reader_apto_804",
            UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Test
    @DisplayName("Assinatura correta, no formato DER (Android), é aceita")
    void assinaturaDerValida() throws Exception {
        String assinatura = assinarDer(parDoJoao, MENSAGEM);

        assertThat(SignatureVerifier.verify(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256,
                MENSAGEM, assinatura)).isTrue();
    }

    @Test
    @DisplayName("Assinatura correta, no formato cru (navegador), também é aceita")
    void assinaturaCruaValida() throws Exception {
        String assinatura = derParaCru(assinarDer(parDoJoao, MENSAGEM));

        // 64 bytes secos, sem o embrulho ASN.1
        assertThat(Base64.getDecoder().decode(assinatura)).hasSize(64);

        assertThat(SignatureVerifier.verify(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256,
                MENSAGEM, assinatura)).isTrue();
    }

    @Test
    @DisplayName("CASO 10 — assinatura alterada é RECUSADA")
    void assinaturaAlteradaEhRecusada() throws Exception {
        byte[] assinatura = Base64.getDecoder().decode(assinarDer(parDoJoao, MENSAGEM));

        // Troca um único bit. Basta isso para invalidar tudo.
        assinatura[assinatura.length - 1] ^= 0x01;

        assertThat(SignatureVerifier.verify(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256,
                MENSAGEM, Base64.getEncoder().encodeToString(assinatura))).isFalse();
    }

    @Test
    @DisplayName("Assinatura de OUTRA pessoa é recusada")
    void assinaturaDeOutraChaveEhRecusada() throws Exception {
        String assinatura = assinarDer(parDeOutraPessoa, MENSAGEM);

        assertThat(SignatureVerifier.verify(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256,
                MENSAGEM, assinatura)).isFalse();
    }

    @Test
    @DisplayName("Assinatura feita para OUTRA PORTA é recusada")
    void assinaturaDeOutraPortaEhRecusada() throws Exception {
        // O celular assinou uma mensagem que mencionava a portaria...
        byte[] mensagemDaPortaria = SignedMessage.bytes(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "bm9uY2UtZGUtdGVzdGU=",
                "reader_portaria",
                UUID.fromString("22222222-2222-2222-2222-222222222222"));

        String assinatura = assinarDer(parDoJoao, mensagemDaPortaria);

        // ...e alguém tenta usá-la na porta do apartamento.
        // É por isso que o código do leitor entra no texto assinado.
        assertThat(SignatureVerifier.verify(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256,
                MENSAGEM, assinatura)).isFalse();
    }

    @Test
    @DisplayName("Mensagem diferente da assinada é recusada")
    void mensagemDiferenteEhRecusada() throws Exception {
        String assinatura = assinarDer(parDoJoao, MENSAGEM);

        byte[] outraMensagem = "qualquer outra coisa".getBytes(StandardCharsets.UTF_8);

        assertThat(SignatureVerifier.verify(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256,
                outraMensagem, assinatura)).isFalse();
    }

    @Test
    @DisplayName("Entradas inválidas são recusadas sem estourar exceção")
    void entradasInvalidasNaoQuebram() {
        String chaveValida = publicKeyBase64(parDoJoao);

        // Nenhum destes pode lançar exceção: todos devem simplesmente recusar.
        assertThat(SignatureVerifier.verify(
                chaveValida, SignatureAlgorithm.ECDSA_P256, MENSAGEM, "isso-nao-e-base64!!"))
                .isFalse();

        assertThat(SignatureVerifier.verify(
                chaveValida, SignatureAlgorithm.ECDSA_P256, MENSAGEM, "AAAA"))
                .isFalse();

        assertThat(SignatureVerifier.verify(
                "chave-invalida", SignatureAlgorithm.ECDSA_P256, MENSAGEM, "AAAA"))
                .isFalse();
    }

    @Test
    @DisplayName("Chave pública é validada no cadastro")
    void validacaoDeChavePublica() {
        assertThat(SignatureVerifier.isValidPublicKey(
                publicKeyBase64(parDoJoao), SignatureAlgorithm.ECDSA_P256)).isTrue();

        assertThat(SignatureVerifier.isValidPublicKey(
                "não é uma chave", SignatureAlgorithm.ECDSA_P256)).isFalse();

        assertThat(SignatureVerifier.isValidPublicKey(
                "", SignatureAlgorithm.ECDSA_P256)).isFalse();
    }

    @Test
    @DisplayName("Ed25519 também funciona, para quando os aparelhos suportarem")
    void ed25519Funciona() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(MENSAGEM);
        String assinatura = Base64.getEncoder().encodeToString(signer.sign());

        assertThat(SignatureVerifier.verify(
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                SignatureAlgorithm.ED25519, MENSAGEM, assinatura)).isTrue();
    }

    @Test
    @DisplayName("Nomes de algoritmo são reconhecidos em suas variações")
    void parseDeAlgoritmos() {
        assertThat(SignatureAlgorithm.parse("ECDSA_P256")).isEqualTo(SignatureAlgorithm.ECDSA_P256);
        assertThat(SignatureAlgorithm.parse("ecdsa")).isEqualTo(SignatureAlgorithm.ECDSA_P256);
        assertThat(SignatureAlgorithm.parse("ES256")).isEqualTo(SignatureAlgorithm.ECDSA_P256);
        assertThat(SignatureAlgorithm.parse(null)).isEqualTo(SignatureAlgorithm.ECDSA_P256);
        assertThat(SignatureAlgorithm.parse("Ed25519")).isEqualTo(SignatureAlgorithm.ED25519);
    }
}
