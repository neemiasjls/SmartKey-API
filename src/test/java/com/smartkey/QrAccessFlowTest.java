package com.smartkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkey.domain.crypto.QrPayload;
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
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O canal QR de ponta a ponta.
 *
 * Este teste faz o papel do celular do hóspede: recebe o link de ativação,
 * cria o par de chaves, registra a pública e passa a montar códigos assinados.
 *
 * Também confirma que o hóspede consegue ativar a chave SEM ter a chave de
 * administração — se precisasse dela, o hóspede teria poder de criar reservas
 * e abrir qualquer porta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DisplayName("Acesso por QR Code")
class QrAccessFlowTest {

    private static final String ADMIN_KEY = "demo123";
    /**
     * A estadia de teste é montada em torno do instante atual: começou ontem e
     * termina em três dias. Datas fixas envelhecem — um teste escrito com
     * "setembro de 2026" passaria a falhar sozinho em outubro.
     */
    private static final String CHECK_IN = Instant.now().minus(Duration.ofDays(1)).toString();
    private static final String CHECK_OUT = Instant.now().plus(Duration.ofDays(3)).toString();

    /** Agora: dentro da estadia. */
    private static final String DURANTE = Instant.now().toString();

    private static final Base64.Encoder URL64 = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final SecureRandom random = new SecureRandom();

    private KeyPair parDeChaves;
    private UUID credentialId;

    // ------------------------------------------------------------------

    @BeforeEach
    void prepararCenario() throws Exception {
        String guestId = adminPost("/api/admin/guests", """
                {"name":"Rita do QR"}""", 201).get("id").asText();

        String reservationId = adminPost("/api/admin/reservations", """
                {"guestId":"%s","unitLabel":"804",
                 "checkInAt":"%s",
                 "checkOutAt":"%s"}"""
                .formatted(guestId, CHECK_IN, CHECK_OUT), 201).get("id").asText();

        String deviceId = adminPost("/api/admin/devices", """
                {"guestId":"%s","platform":"WEB"}""".formatted(guestId), 201)
                .get("id").asText();

        JsonNode credencial = adminPost("/api/admin/credentials", """
                {"reservationId":"%s","deviceId":"%s",
                 "accessPointCodes":["entrada_condominio","apartamento_804"]}"""
                .formatted(reservationId, deviceId), 201);

        credentialId = UUID.fromString(credencial.get("id").asText());

        // O celular cria o par e ATIVA usando só o token do link.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        parDeChaves = generator.generateKeyPair();

        ativar(credencial.get("enrollmentToken").asText());
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    private JsonNode adminPost(String url, String body, int expected) throws Exception {
        return doPost(url, body, expected, ADMIN_KEY);
    }

    /** Requisição SEM chave administrativa — como o celular do hóspede faz. */
    private JsonNode guestPost(String url, String body, int expected) throws Exception {
        return doPost(url, body, expected, null);
    }

    private JsonNode doPost(String url, String body, int expected, String adminKey)
            throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (adminKey != null) {
            request = request.header("x-admin-key", adminKey);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(expected))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? objectMapper.createObjectNode()
                : objectMapper.readTree(content);
    }

    private JsonNode ativar(String token) throws Exception {
        String publicKey = Base64.getEncoder()
                .encodeToString(parDeChaves.getPublic().getEncoded());

        return guestPost("/api/keys/enroll", """
                {"token":"%s","publicKey":"%s","algorithm":"ECDSA_P256",
                 "deviceLabel":"iPhone da Rita"}"""
                .formatted(token, publicKey), 200);
    }

    /** Monta um código QR assinado, exatamente como a página do hóspede faz. */
    private String montarQr(long issuedAtEpochSeconds) throws Exception {
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = URL64.encodeToString(nonceBytes);

        String message = QrPayload.messageToSign(
                credentialId, nonce, issuedAtEpochSeconds);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(parDeChaves.getPrivate());
        signer.update(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return new QrPayload(credentialId, nonce, issuedAtEpochSeconds,
                Base64.getEncoder().encodeToString(signer.sign())).encode();
    }

    private String montarQrAgora() throws Exception {
        return montarQr(Instant.now().getEpochSecond());
    }

    private JsonNode apresentar(String qr, String readerCode) throws Exception {
        return adminPost("/api/access/qr-verify", """
                {"readerCode":"%s","qrContent":"%s","at":"%s"}"""
                .formatted(readerCode, qr, DURANTE), 200);
    }

    // ==================================================================
    // ATIVAÇÃO
    // ==================================================================

    @Test
    @DisplayName("O hóspede ativa a chave SEM a chave de administração")
    void hospedeAtivaSemPoderAdministrativo() throws Exception {
        // O cenário já foi ativado no @BeforeEach, usando guestPost —
        // ou seja, sem enviar o cabeçalho x-admin-key.
        // Confirmamos aqui que o hóspede realmente NÃO tem poder administrativo:
        mockMvc.perform(post("/api/admin/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invasor"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("O link de ativação vale UMA vez só")
    void linkDeAtivacaoQueima() throws Exception {
        JsonNode credencial = adminPost("/api/admin/credentials", """
                {"reservationId":"%s","deviceId":"%s","accessPointCodes":["academia"]}"""
                .formatted(novaReservaId(), novoDeviceId()), 201);

        String token = credencial.get("enrollmentToken").asText();
        String publicKey = Base64.getEncoder()
                .encodeToString(parDeChaves.getPublic().getEncoded());

        String corpo = """
                {"token":"%s","publicKey":"%s","algorithm":"ECDSA_P256"}"""
                .formatted(token, publicKey);

        guestPost("/api/keys/enroll", corpo, 200);      // primeira vez: funciona
        guestPost("/api/keys/enroll", corpo, 404);      // segunda: recusada
    }

    @Test
    @DisplayName("O link de uma chave revogada não ativa mais nada")
    void linkDeChaveRevogadaNaoAtiva() throws Exception {
        JsonNode credencial = adminPost("/api/admin/credentials", """
                {"reservationId":"%s","deviceId":"%s","accessPointCodes":["academia"]}"""
                .formatted(novaReservaId(), novoDeviceId()), 201);

        // A administração revoga ANTES de o hóspede ativar.
        adminPost("/api/admin/credentials/" + credencial.get("id").asText() + "/revoke",
                """
                {"reason":"reserva desfeita"}""", 200);

        String publicKey = Base64.getEncoder()
                .encodeToString(parDeChaves.getPublic().getEncoded());

        guestPost("/api/keys/enroll", """
                {"token":"%s","publicKey":"%s","algorithm":"ECDSA_P256"}"""
                .formatted(credencial.get("enrollmentToken").asText(), publicKey), 409);
    }

    @Test
    @DisplayName("Depois de ativada, a credencial não expõe mais o token")
    void tokenSomeDepoisDeUsado() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/admin/credentials/" + credentialId)
                                .header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode credencial = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(credencial.get("enrollmentToken").isNull()).isTrue();
    }

    // ==================================================================
    // ABRIR A PORTA
    // ==================================================================

    @Test
    @DisplayName("QR assinado na porta autorizada LIBERA")
    void qrValidoLibera() throws Exception {
        JsonNode resultado = apresentar(montarQrAgora(), "reader_apto_804");

        assertThat(resultado.get("decision").asText()).isEqualTo("GRANTED");
    }

    @Test
    @DisplayName("QR assinado na porta NÃO autorizada é negado")
    void qrEmPortaNaoAutorizada() throws Exception {
        JsonNode resultado = apresentar(montarQrAgora(), "reader_apto_805");

        assertThat(resultado.get("reason").asText()).isEqualTo("DOOR_NOT_AUTHORIZED");
    }

    // ==================================================================
    // ATAQUES
    // ==================================================================

    @Test
    @DisplayName("Repetir o mesmo QR é NEGADO (print de tela não serve)")
    void mesmoQrDuasVezes() throws Exception {
        String qr = montarQrAgora();

        assertThat(apresentar(qr, "reader_apto_804").get("decision").asText())
                .isEqualTo("GRANTED");

        JsonNode repetido = apresentar(qr, "reader_apto_804");

        assertThat(repetido.get("decision").asText()).isEqualTo("DENIED");
        assertThat(repetido.get("reason").asText()).isEqualTo("CHALLENGE_ALREADY_USED");
    }

    @Test
    @DisplayName("QR antigo é NEGADO, mesmo com assinatura perfeita")
    void qrVencido() throws Exception {
        // Assinado corretamente, mas emitido dez minutos atrás.
        String qr = montarQr(Instant.now().minusSeconds(600).getEpochSecond());

        JsonNode resultado = apresentar(qr, "reader_apto_804");

        assertThat(resultado.get("reason").asText()).isEqualTo("CHALLENGE_EXPIRED");
    }

    @Test
    @DisplayName("QR com data no futuro também é NEGADO")
    void qrComDataNoFuturo() throws Exception {
        String qr = montarQr(Instant.now().plusSeconds(600).getEpochSecond());

        JsonNode resultado = apresentar(qr, "reader_apto_804");

        assertThat(resultado.get("reason").asText()).isEqualTo("CHALLENGE_EXPIRED");
    }

    @Test
    @DisplayName("QR com assinatura alterada é NEGADO")
    void qrComAssinaturaAlterada() throws Exception {
        String qr = montarQrAgora();

        // Troca o último caractere da assinatura.
        char[] chars = qr.toCharArray();
        chars[chars.length - 1] = (chars[chars.length - 1] == 'A') ? 'B' : 'A';

        JsonNode resultado = apresentar(new String(chars), "reader_apto_804");

        assertThat(resultado.get("reason").asText()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("QR assinado por outro aparelho é NEGADO")
    void qrDeOutroAparelho() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair impostor = generator.generateKeyPair();

        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = URL64.encodeToString(nonceBytes);
        long issuedAt = Instant.now().getEpochSecond();

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(impostor.getPrivate());
        signer.update(QrPayload.messageToSign(credentialId, nonce, issuedAt)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String qr = new QrPayload(credentialId, nonce, issuedAt,
                Base64.getEncoder().encodeToString(signer.sign())).encode();

        assertThat(apresentar(qr, "reader_apto_804").get("reason").asText())
                .isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Qualquer QR de rua é ignorado sem quebrar nada")
    void qrQualquerNaoQuebra() throws Exception {
        for (String lixo : new String[]{
                "https://exemplo.com", "SK1.a.b", "", "   ",
                "SK1.!!!.???.abc.###", "SK2.aaa.bbb.111.ccc"}) {

            JsonNode resultado = adminPost("/api/access/qr-verify", """
                    {"readerCode":"reader_apto_804","qrContent":"%s","at":"%s"}"""
                    .formatted(lixo.isBlank() ? "x" : lixo, DURANTE), 200);

            assertThat(resultado.get("decision").asText()).isEqualTo("DENIED");
        }
    }

    // ==================================================================
    // As regras normais continuam valendo
    // ==================================================================

    @Test
    @DisplayName("Credencial revogada é NEGADA mesmo com QR perfeito")
    void credencialRevogada() throws Exception {
        adminPost("/api/admin/credentials/" + credentialId + "/revoke", """
                {"reason":"teste"}""", 200);

        JsonNode resultado = apresentar(montarQrAgora(), "reader_apto_804");

        assertThat(resultado.get("reason").asText()).isEqualTo("CREDENTIAL_REVOKED");
    }

    // ------------------------------------------------------------------

    private String novaReservaId() throws Exception {
        String guestId = adminPost("/api/admin/guests", """
                {"name":"Outro Hóspede"}""", 201).get("id").asText();

        lastGuestId = guestId;

        return adminPost("/api/admin/reservations", """
                {"guestId":"%s","unitLabel":"901",
                 "checkInAt":"%s",
                 "checkOutAt":"%s"}"""
                .formatted(guestId, CHECK_IN, CHECK_OUT), 201).get("id").asText();
    }

    private String lastGuestId;

    private String novoDeviceId() throws Exception {
        return adminPost("/api/admin/devices", """
                {"guestId":"%s","platform":"WEB"}""".formatted(lastGuestId), 201)
                .get("id").asText();
    }
}
