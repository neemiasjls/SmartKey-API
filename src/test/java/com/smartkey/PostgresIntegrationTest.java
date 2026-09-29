package com.smartkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkey.domain.crypto.QrPayload;
import com.smartkey.service.MaintenanceJobs;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A aplicação rodando sobre um PostgreSQL DE VERDADE — o mesmo banco do Supabase.
 *
 * POR QUE ESTE TESTE EXISTE
 *
 * Todos os outros testes usam o H2, um banco em memória que "finge" ser
 * PostgreSQL, e onde as tabelas são criadas direto das classes Java. Isso
 * deixava dois pontos cegos:
 *
 *   1. As migrações do Flyway (V1 a V5) nunca eram executadas. Um erro de SQL
 *      só apareceria no primeiro deploy, em produção.
 *
 *   2. O PostgreSQL trata erros dentro de uma transação de um jeito que o H2
 *      não reproduz: depois de QUALQUER erro, a transação inteira fica
 *      inutilizável. É exatamente o mecanismo em que a proteção contra
 *      repetição do QR se apoia — e ele precisava ser provado no banco real.
 *
 * O Postgres aqui é um binário real, baixado como dependência do Maven e
 * executado num diretório temporário. Não usa Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pgtest")
@DisplayName("Integração com PostgreSQL real")
class PostgresIntegrationTest {

    private static final String ADMIN_KEY = "chave-de-teste-postgres";
    private static final String READER_804 = "demo_reader_reader_apto_804";

    private static EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        postgres = EmbeddedPostgres.start();

        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");

        // Exatamente a configuração de produção: Flyway cria o schema
        // "smartkey" e aplica as migrações; o Hibernate só VALIDA que as
        // classes Java batem com as tabelas que o Flyway criou.
        registry.add("smartkey.admin-api-key", () -> ADMIN_KEY);
        registry.add("smartkey.seed-demo", () -> "true");
        registry.add("smartkey.allow-time-travel", () -> "false");
        registry.add("smartkey.allow-insecure-check", () -> "false");
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ------------------------------------------------------------------

    @Test
    @DisplayName("As cinco migrações rodam no Postgres e batem com as classes Java")
    void migracoesAplicadas() {
        // Se o contexto subiu, o Hibernate já validou cada tabela e coluna.
        Integer aplicadas = jdbc.queryForObject(
                "SELECT count(*) FROM smartkey.flyway_schema_history WHERE success AND type = 'SQL'",
                Integer.class);

        assertThat(aplicadas).isEqualTo(5);
    }

    @Test
    @DisplayName("Nossas tabelas ficam isoladas no schema 'smartkey'")
    void tabelasNoSchemaIsolado() {
        Integer noPublic = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN ('guests','reservations','credentials','readers')
                """, Integer.class);

        assertThat(noPublic)
                .as("nenhuma tabela nossa pode cair no schema public")
                .isZero();
    }

    @Test
    @DisplayName("O banco recusa, sozinho, uma reserva com checkout antes do check-in")
    void regraDoBancoProtegeOPeriodo() {
        UUID guest = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO smartkey.guests (id, name, created_at, updated_at)
                VALUES (?, 'Teste', now(), now())""", guest);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO smartkey.reservations
                    (id, guest_id, unit_label, check_in_at, check_out_at, status,
                     created_at, updated_at)
                VALUES (?, ?, '1', now(), now() - interval '1 day', 'CONFIRMED',
                        now(), now())""", UUID.randomUUID(), guest))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("QR repetido é barrado no Postgres real, e a porta segue respondendo")
    void protecaoContraRepeticaoNoBancoReal() throws Exception {
        KeyPair par = gerarPar();
        UUID credencial = ativarChaveDoJoao(par);

        String qr = montarQr(par, credencial);

        // Primeira leitura: abre.
        assertThat(apresentar(qr).get("decision").asText()).isEqualTo("GRANTED");

        // Segunda leitura do MESMO código: a chave primária recusa a gravação.
        // No Postgres, esse erro inutilizaria a transação principal — por isso
        // a reserva do código roda numa transação própria. Se não rodasse, a
        // resposta abaixo seria um erro 500, e não uma recusa limpa.
        JsonNode repetido = apresentar(qr);
        assertThat(repetido.get("decision").asText()).isEqualTo("DENIED");
        assertThat(repetido.get("reason").asText()).isEqualTo("CHALLENGE_ALREADY_USED");

        // E a recusa ficou registrada no histórico, o que prova que a
        // transação principal sobreviveu ao erro da secundária.
        Integer registradas = jdbc.queryForObject("""
                SELECT count(*) FROM smartkey.access_events
                 WHERE credential_id = ? AND reason = 'CHALLENGE_ALREADY_USED'""",
                Integer.class, credencial);
        assertThat(registradas).isEqualTo(1);

        // Um código NOVO continua funcionando normalmente.
        assertThat(apresentar(montarQr(par, credencial)).get("decision").asText())
                .isEqualTo("GRANTED");
    }

    @Test
    @DisplayName("Desafio do NFC é queimado de forma atômica no Postgres")
    void desafioNfcNoBancoReal() throws Exception {
        KeyPair par = gerarPar();
        UUID credencial = ativarChaveDoJoao(par);

        JsonNode desafio = objectMapper.readTree(mockMvc.perform(post("/api/access/challenge")
                        .header("x-reader-key", READER_804)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"readerCode":"reader_apto_804"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String challengeId = desafio.get("challengeId").asText();
        String mensagem = "SMARTKEY-ACCESS-v1|" + challengeId + "|"
                + desafio.get("nonce").asText() + "|reader_apto_804|" + credencial;

        String corpo = """
                {"challengeId":"%s","credentialId":"%s","signature":"%s"}"""
                .formatted(challengeId, credencial, assinar(par, mensagem));

        assertThat(verificar(corpo).get("decision").asText()).isEqualTo("GRANTED");
        assertThat(verificar(corpo).get("reason").asText()).isEqualTo("CHALLENGE_ALREADY_USED");
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    /**
     * Cada teste usa um hóspede novo, criado em torno de "agora", para não
     * depender da ordem em que os testes rodam.
     */
    private UUID ativarChaveDoJoao(KeyPair par) throws Exception {
        String guestId = admin("/api/admin/guests", """
                {"name":"Hóspede Postgres"}""").get("id").asText();

        String reservationId = admin("/api/admin/reservations", """
                {"guestId":"%s","unitLabel":"804","checkInAt":"%s","checkOutAt":"%s"}"""
                .formatted(guestId,
                        Instant.now().minusSeconds(3600),
                        Instant.now().plusSeconds(3 * 86400))).get("id").asText();

        String deviceId = admin("/api/admin/devices", """
                {"guestId":"%s","platform":"ANDROID"}""".formatted(guestId)).get("id").asText();

        JsonNode credencial = admin("/api/admin/credentials", """
                {"reservationId":"%s","deviceId":"%s","accessPointCodes":["apartamento_804"]}"""
                .formatted(reservationId, deviceId));

        mockMvc.perform(post("/api/keys/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","publicKey":"%s","algorithm":"ECDSA_P256"}"""
                                .formatted(credencial.get("enrollmentToken").asText(),
                                        Base64.getEncoder().encodeToString(
                                                par.getPublic().getEncoded()))))
                .andExpect(status().isOk());

        return UUID.fromString(credencial.get("id").asText());
    }

    private JsonNode admin(String url, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(url)
                        .header("x-admin-key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode apresentar(String qr) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/access/qr-verify")
                        .header("x-reader-key", READER_804)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"readerCode":"reader_apto_804","qrContent":"%s"}"""
                                .formatted(qr)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode verificar(String corpo) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/access/verify")
                        .header("x-reader-key", READER_804)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static KeyPair gerarPar() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String assinar(KeyPair par, String mensagem) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(par.getPrivate());
        signer.update(mensagem.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static String montarQr(KeyPair par, UUID credencial) throws Exception {
        byte[] sorteio = new byte[16];
        new SecureRandom().nextBytes(sorteio);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(sorteio);
        long agora = Instant.now().getEpochSecond();

        String assinatura = assinar(par, QrPayload.messageToSign(credencial, nonce, agora));
        return new QrPayload(credencial, nonce, agora, assinatura).encode();
    }

    @Autowired
    private MaintenanceJobs maintenanceJobs;

    @Test
    @DisplayName("A limpeza periódica apaga o que venceu e preserva o que vale")
    void limpezaPeriodica() {
        jdbc.update("""
                INSERT INTO smartkey.challenges (id, nonce, reader_code, created_at, expires_at)
                VALUES (?, 'nonce-velho-de-teste', 'reader_apto_804',
                        now() - interval '3 hours', now() - interval '2 hours')""",
                UUID.randomUUID());
        jdbc.update("""
                INSERT INTO smartkey.challenges (id, nonce, reader_code, created_at, expires_at)
                VALUES (?, 'nonce-valido-de-teste', 'reader_apto_804',
                        now(), now() + interval '1 minute')""",
                UUID.randomUUID());

        maintenanceJobs.purgeExpiredCodes();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM smartkey.challenges WHERE nonce = 'nonce-velho-de-teste'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM smartkey.challenges WHERE nonce = 'nonce-valido-de-teste'",
                Integer.class)).isEqualTo(1);
    }
}
