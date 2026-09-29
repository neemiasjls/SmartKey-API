package com.smartkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkey.domain.crypto.SignedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FASE 3 — o fluxo seguro completo, de ponta a ponta.
 *
 * Este teste faz o papel do celular: gera um par de chaves, guarda a privada
 * só para si, cadastra a pública no servidor e assina os desafios.
 *
 * É aqui que ficam os casos 9 e 10 da lista de testes:
 *   9.  reutilizar um desafio antigo  -> NEGADO
 *   10. alterar a assinatura          -> NEGADO
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DisplayName("Acesso seguro com assinatura")
class SecureAccessFlowTest {

    private static final String ADMIN_KEY = "demo123";

    /** Um momento no meio da hospedagem do João (18 a 21 de setembro de 2026). */
    private static final String DURANTE = "2026-09-19T13:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private KeyPair parDeChaves;
    private String credentialId;

    // ------------------------------------------------------------------
    // Preparação: monta uma reserva com o "celular" já registrado
    // ------------------------------------------------------------------

    @BeforeEach
    void prepararCenario() throws Exception {
        // 1. O CELULAR gera seu par de chaves. A privada nunca sai daqui.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        parDeChaves = generator.generateKeyPair();

        // 2. Cadastros no servidor
        String guestId = postJson("/api/admin/guests", """
                {"name":"Carlos Teste"}""", 201).get("id").asText();

        String reservationId = postJson("/api/admin/reservations", """
                {"guestId":"%s","unitLabel":"804",
                 "checkInAt":"2026-09-18T15:00:00-03:00",
                 "checkOutAt":"2026-09-21T11:00:00-03:00"}"""
                .formatted(guestId), 201).get("id").asText();

        String deviceId = postJson("/api/admin/devices", """
                {"guestId":"%s","platform":"ANDROID","label":"Celular do Carlos"}"""
                .formatted(guestId), 201).get("id").asText();

        // 3. Só a chave PÚBLICA é enviada ao servidor
        postJson("/api/admin/devices/%s/public-key".formatted(deviceId), """
                {"publicKey":"%s","algorithm":"ECDSA_P256"}"""
                .formatted(publicKeyBase64()), 200);

        credentialId = postJson("/api/admin/credentials", """
                {"reservationId":"%s","deviceId":"%s",
                 "accessPointCodes":["entrada_condominio","apartamento_804"]}"""
                .formatted(reservationId, deviceId), 201).get("id").asText();
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("x-admin-key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? objectMapper.createObjectNode()
                : objectMapper.readTree(content);
    }

    private String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(parDeChaves.getPublic().getEncoded());
    }

    /** Pede um desafio novo ao servidor, como faria o leitor. */
    private JsonNode pedirDesafio(String readerCode) throws Exception {
        return postJson("/api/access/challenge",
                """
                {"readerCode":"%s"}""".formatted(readerCode), 200);
    }

    /** O que o celular faz: assina o desafio com a chave privada. */
    private String assinar(JsonNode desafio, String readerCode) throws Exception {
        byte[] mensagem = SignedMessage.bytes(
                UUID.fromString(desafio.get("challengeId").asText()),
                desafio.get("nonce").asText(),
                readerCode,
                UUID.fromString(credentialId));

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(parDeChaves.getPrivate());
        signer.update(mensagem);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private JsonNode verificar(String challengeId, String assinatura) throws Exception {
        return postJson("/api/access/verify", """
                {"challengeId":"%s","credentialId":"%s","signature":"%s","at":"%s"}"""
                .formatted(challengeId, credentialId, assinatura, DURANTE), 200);
    }

    // ==================================================================
    // O CAMINHO FELIZ
    // ==================================================================

    @Test
    @DisplayName("Assinatura válida na porta autorizada LIBERA o acesso")
    void fluxoCompletoLibera() throws Exception {
        JsonNode desafio = pedirDesafio("reader_apto_804");

        assertThat(desafio.get("nonce").asText()).isNotBlank();
        assertThat(desafio.get("algorithm").asText()).isEqualTo("ECDSA_P256");

        JsonNode resultado = verificar(
                desafio.get("challengeId").asText(),
                assinar(desafio, "reader_apto_804"));

        assertThat(resultado.get("decision").asText()).isEqualTo("GRANTED");
    }

    @Test
    @DisplayName("Cada desafio traz um número diferente")
    void cadaDesafioEhUnico() throws Exception {
        String primeiro = pedirDesafio("reader_apto_804").get("nonce").asText();
        String segundo = pedirDesafio("reader_apto_804").get("nonce").asText();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    // ==================================================================
    // CASO 9 — REPETIÇÃO (replay)
    // ==================================================================

    @Test
    @DisplayName("CASO 9 — reutilizar um desafio já usado é NEGADO")
    void desafioNaoPodeSerUsadoDuasVezes() throws Exception {
        JsonNode desafio = pedirDesafio("reader_apto_804");
        String challengeId = desafio.get("challengeId").asText();
        String assinatura = assinar(desafio, "reader_apto_804");

        // Primeira apresentação: tudo certo.
        assertThat(verificar(challengeId, assinatura).get("decision").asText())
                .isEqualTo("GRANTED");

        // Alguém gravou essa resposta e tenta reapresentá-la.
        // A assinatura continua matematicamente válida - mas o desafio
        // já foi queimado, e é isso que barra o ataque.
        JsonNode repetida = verificar(challengeId, assinatura);

        assertThat(repetida.get("decision").asText()).isEqualTo("DENIED");
        assertThat(repetida.get("reason").asText()).isEqualTo("CHALLENGE_ALREADY_USED");
    }

    @Test
    @DisplayName("Desafio inexistente é NEGADO")
    void desafioInexistente() throws Exception {
        JsonNode resultado = verificar(
                "99999999-9999-9999-9999-999999999999", "QUFBQQ==");

        assertThat(resultado.get("reason").asText()).isEqualTo("CHALLENGE_NOT_FOUND");
    }

    // ==================================================================
    // CASO 10 — ASSINATURA ALTERADA
    // ==================================================================

    @Test
    @DisplayName("CASO 10 — assinatura alterada é NEGADA")
    void assinaturaAlteradaEhNegada() throws Exception {
        JsonNode desafio = pedirDesafio("reader_apto_804");

        byte[] assinatura = Base64.getDecoder().decode(assinar(desafio, "reader_apto_804"));
        assinatura[assinatura.length - 1] ^= 0x01;   // um bit trocado

        JsonNode resultado = verificar(
                desafio.get("challengeId").asText(),
                Base64.getEncoder().encodeToString(assinatura));

        assertThat(resultado.get("decision").asText()).isEqualTo("DENIED");
        assertThat(resultado.get("reason").asText()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Assinatura de outro aparelho é NEGADA")
    void assinaturaDeOutroAparelho() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair impostor = generator.generateKeyPair();

        JsonNode desafio = pedirDesafio("reader_apto_804");

        byte[] mensagem = SignedMessage.bytes(
                UUID.fromString(desafio.get("challengeId").asText()),
                desafio.get("nonce").asText(),
                "reader_apto_804",
                UUID.fromString(credentialId));

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(impostor.getPrivate());
        signer.update(mensagem);

        JsonNode resultado = verificar(
                desafio.get("challengeId").asText(),
                Base64.getEncoder().encodeToString(signer.sign()));

        assertThat(resultado.get("reason").asText()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Assinatura feita para OUTRA porta não serve nesta")
    void assinaturaDeOutraPortaNaoServe() throws Exception {
        // O desafio é da porta do apartamento...
        JsonNode desafio = pedirDesafio("reader_apto_804");

        // ...mas o celular assina um texto que menciona a portaria.
        // Isso simula alguém tentando reaproveitar uma resposta entre portas.
        String assinatura = assinar(desafio, "reader_portaria");

        JsonNode resultado = verificar(desafio.get("challengeId").asText(), assinatura);

        assertThat(resultado.get("reason").asText()).isEqualTo("INVALID_SIGNATURE");
    }

    // ==================================================================
    // AS REGRAS DE NEGÓCIO CONTINUAM VALENDO
    // ==================================================================

    @Test
    @DisplayName("Assinatura válida na porta ERRADA continua negada")
    void assinaturaValidaEmPortaNaoAutorizada() throws Exception {
        // O Carlos tem permissão para o 804, não para o 805.
        JsonNode desafio = pedirDesafio("reader_apto_805");

        JsonNode resultado = verificar(
                desafio.get("challengeId").asText(),
                assinar(desafio, "reader_apto_805"));

        // A identidade foi provada; o que faltou foi permissão.
        assertThat(resultado.get("reason").asText()).isEqualTo("DOOR_NOT_AUTHORIZED");
    }

    @Test
    @DisplayName("Assinatura válida ANTES do check-in continua negada")
    void assinaturaValidaAntesDoCheckIn() throws Exception {
        JsonNode desafio = pedirDesafio("reader_apto_804");

        JsonNode resultado = postJson("/api/access/verify", """
                {"challengeId":"%s","credentialId":"%s","signature":"%s",
                 "at":"2026-09-17T13:00:00Z"}"""
                .formatted(desafio.get("challengeId").asText(), credentialId,
                        assinar(desafio, "reader_apto_804")), 200);

        assertThat(resultado.get("reason").asText()).isEqualTo("BEFORE_CHECK_IN");
    }

    @Test
    @DisplayName("Aparelho sem chave pública cadastrada é negado")
    void aparelhoSemChavePublica() throws Exception {
        String guestId = postJson("/api/admin/guests", """
                {"name":"Sem Chave"}""", 201).get("id").asText();

        String reservationId = postJson("/api/admin/reservations", """
                {"guestId":"%s","unitLabel":"804",
                 "checkInAt":"2026-09-18T15:00:00-03:00",
                 "checkOutAt":"2026-09-21T11:00:00-03:00"}"""
                .formatted(guestId), 201).get("id").asText();

        String deviceId = postJson("/api/admin/devices", """
                {"guestId":"%s","platform":"ANDROID"}"""
                .formatted(guestId), 201).get("id").asText();

        String semChave = postJson("/api/admin/credentials", """
                {"reservationId":"%s","deviceId":"%s","accessPointCodes":["apartamento_804"]}"""
                .formatted(reservationId, deviceId), 201).get("id").asText();

        JsonNode desafio = pedirDesafio("reader_apto_804");

        JsonNode resultado = postJson("/api/access/verify", """
                {"challengeId":"%s","credentialId":"%s","signature":"QUFBQQ==","at":"%s"}"""
                .formatted(desafio.get("challengeId").asText(), semChave, DURANTE), 200);

        assertThat(resultado.get("reason").asText()).isEqualTo("DEVICE_KEY_MISSING");
    }

    @Test
    @DisplayName("A simulação de data/hora NÃO consegue ressuscitar um desafio usado")
    void relogioSimuladoNaoBurlaAProtecaoContraRepeticao() throws Exception {
        JsonNode desafio = pedirDesafio("reader_apto_804");
        String challengeId = desafio.get("challengeId").asText();
        String assinatura = assinar(desafio, "reader_apto_804");

        assertThat(verificar(challengeId, assinatura).get("decision").asText())
                .isEqualTo("GRANTED");

        // Mesmo informando outra data, o desafio continua queimado.
        // O relógio simulado serve para testar as regras da hospedagem;
        // ele nunca pode afrouxar a criptografia.
        for (String outraData : new String[]{
                "2026-09-18T16:00:00Z", "2026-09-20T10:00:00Z", "2026-09-19T13:00:01Z"}) {

            JsonNode resultado = postJson("/api/access/verify", """
                    {"challengeId":"%s","credentialId":"%s","signature":"%s","at":"%s"}"""
                    .formatted(challengeId, credentialId, assinatura, outraData), 200);

            assertThat(resultado.get("reason").asText()).isEqualTo("CHALLENGE_ALREADY_USED");
        }
    }

    @Test
    @DisplayName("Chave pública malformada é recusada já no cadastro")
    void chavePublicaMalformadaEhRecusada() throws Exception {
        String guestId = postJson("/api/admin/guests", """
                {"name":"Chave Ruim"}""", 201).get("id").asText();

        String deviceId = postJson("/api/admin/devices", """
                {"guestId":"%s","platform":"ANDROID"}""".formatted(guestId), 201)
                .get("id").asText();

        postJson("/api/admin/devices/%s/public-key".formatted(deviceId), """
                {"publicKey":"isso-nao-e-uma-chave","algorithm":"ECDSA_P256"}""", 400);
    }
}
