package com.smartkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes da API inteira, de ponta a ponta.
 *
 * Roda no perfil "demo": o banco fica na memoria do proprio teste, entao NAO
 * precisa de Docker, de Supabase nem de internet.
 *
 * POR QUE ESTES TESTES EXISTEM:
 *
 * Os testes do AuthorizationEngine cobrem a regra de decisao, mas passavam
 * mesmo com um erro grave nos endpoints de consulta: os dados do hospede sao
 * buscados no banco somente quando alguem pede, e a conexao ja estava fechada
 * na hora de montar o JSON (LazyInitializationException, erro 500).
 *
 * Esse erro so aparece quando a requisicao HTTP e feita de verdade - que e
 * exatamente o que os testes abaixo fazem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DisplayName("API completa")
class ApiIntegrationTest {

    private static final String ADMIN_KEY = "demo123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("x-admin-key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Pergunta ao sistema se uma credencial abre determinada porta. */
    private JsonNode tentarAcesso(String credentialId, String readerCode, String quando)
            throws Exception {
        String body = """
                {"readerCode":"%s","credentialId":"%s","at":"%s"}"""
                .formatted(readerCode, credentialId, quando);
        return postJson("/api/access/check", body, 200);
    }

    /**
     * Id da credencial do João, criada automaticamente pelo perfil demo.
     *
     * Buscamos PELO NOME, e não pegando a primeira da lista.
     *
     * Motivo: o banco em memória é compartilhado por todas as classes de
     * teste, e as outras criam credenciais próprias. "A primeira da lista"
     * depende da ordem em que os testes rodam - o tipo de teste que passa
     * dez vezes e falha na décima primeira, sem ninguém entender por quê.
     */
    private String credencialDoJoao() throws Exception {
        JsonNode credenciais = getJson("/api/admin/credentials");

        for (JsonNode credencial : credenciais) {
            if ("João da Silva".equals(credencial.get("guestName").asText())) {
                return credencial.get("id").asText();
            }
        }
        throw new AssertionError("A credencial de exemplo do João não foi encontrada.");
    }

    // ==================================================================
    // ENDPOINTS DE CONSULTA
    //
    // Cada um destes quebrava com erro 500 antes da correcao dos
    // relacionamentos nas consultas (JOIN FETCH).
    // ==================================================================

    @Test
    @DisplayName("Todas as listagens respondem sem erro e com os dados completos")
    void listagensFuncionam() throws Exception {
        for (String url : new String[]{
                "/api/admin/guests",
                "/api/admin/readers",
                "/api/admin/reservations",
                "/api/admin/devices",
                "/api/admin/credentials",
                "/api/access/events"}) {

            mockMvc.perform(get(url).header("x-admin-key", ADMIN_KEY))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("A credencial vem com o nome do hóspede e as permissões")
    void credencialTrazDadosRelacionados() throws Exception {
        JsonNode credencial = getJson("/api/admin/credentials/" + credencialDoJoao());

        // Estes tres campos vem de tabelas diferentes. Se qualquer um deles
        // nao for carregado junto, a resposta vira erro 500.
        assertThat(credencial.get("guestName").asText()).isEqualTo("João da Silva");
        assertThat(credencial.get("unitLabel").asText()).isEqualTo("804");
        assertThat(credencial.get("grants")).hasSize(3);
    }

    @Test
    @DisplayName("A reserva vem com o nome do hóspede")
    void reservaTrazNomeDoHospede() throws Exception {
        JsonNode reservas = getJson("/api/admin/reservations");

        // Mesma precaução: o banco é compartilhado entre as classes de teste.
        boolean achou = false;
        for (JsonNode reserva : reservas) {
            if ("João da Silva".equals(reserva.get("guestName").asText())) {
                assertThat(reserva.get("unitLabel").asText()).isEqualTo("804");
                achou = true;
            }
        }
        assertThat(achou).as("reserva de exemplo do João").isTrue();
    }

    // ==================================================================
    // SEGURANCA
    // ==================================================================

    @Test
    @DisplayName("Sem a chave administrativa, a API recusa o acesso")
    void semChaveEhRecusado() throws Exception {
        mockMvc.perform(get("/api/admin/credentials"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/credentials").header("x-admin-key", "chave-errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A verificação de saúde é pública")
    void healthEhPublico() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Não se emite credencial para o celular de outro hóspede")
    void naoEmiteParaCelularDeOutroHospede() throws Exception {
        // Cria um segundo hóspede, com celular próprio.
        JsonNode outro = postJson("/api/admin/guests",
                """
                {"name":"Pedro Alves"}""", 201);

        JsonNode celularDoPedro = postJson("/api/admin/devices",
                """
                {"guestId":"%s","platform":"ANDROID","label":"Celular do Pedro"}"""
                        .formatted(outro.get("id").asText()), 201);

        // Tenta dar a chave do apartamento do João para o celular do Pedro.
        String reservaDoJoao = null;
        for (JsonNode reserva : getJson("/api/admin/reservations")) {
            if ("João da Silva".equals(reserva.get("guestName").asText())) {
                reservaDoJoao = reserva.get("id").asText();
            }
        }
        assertThat(reservaDoJoao).isNotNull();

        JsonNode erro = postJson("/api/admin/credentials",
                """
                {"reservationId":"%s","deviceId":"%s","accessPointCodes":["apartamento_804"]}"""
                        .formatted(reservaDoJoao, celularDoPedro.get("id").asText()), 400);

        assertThat(erro.get("message").asText()).contains("outro hóspede");
    }

    @Test
    @DisplayName("Reserva com checkout antes do check-in é recusada")
    void reservaComPeriodoInvertidoEhRecusada() throws Exception {
        // Qualquer hóspede serve aqui: o que está sendo testado é o período.
        String hospede = postJson("/api/admin/guests", """
                {"name":"Teste de Período"}""", 201).get("id").asText();

        postJson("/api/admin/reservations",
                """
                {"guestId":"%s","unitLabel":"999",
                 "checkInAt":"2026-10-05T15:00:00-03:00",
                 "checkOutAt":"2026-10-01T11:00:00-03:00"}""".formatted(hospede), 400);
    }

    // ==================================================================
    // O FLUXO PRINCIPAL: ABRIR PORTAS
    // ==================================================================

    @Test
    @DisplayName("Fluxo completo: horários, porta errada, permissão e revogação")
    void fluxoCompletoDeAcesso() throws Exception {
        String cred = credencialDoJoao();

        // Os horários saem da própria credencial, e não de datas fixas:
        // o cenário de demonstração acompanha o dia em que a aplicação sobe.
        JsonNode dados = getJson("/api/admin/credentials/" + cred);
        Instant inicio = Instant.parse(dados.get("validFrom").asText());
        Instant fim = Instant.parse(dados.get("validUntil").asText());

        String antes = inicio.minus(Duration.ofHours(1)).toString();
        String durante = inicio.plus(Duration.between(inicio, fim).dividedBy(2)).toString();
        String depois = fim.plus(Duration.ofHours(1)).toString();

        // 1. Antes do check-in: negado
        assertThat(tentarAcesso(cred, "reader_portaria", antes).get("reason").asText())
                .isEqualTo("BEFORE_CHECK_IN");

        // 2 e 3. Durante a estadia: portaria e apartamento liberam
        assertThat(tentarAcesso(cred, "reader_portaria", durante).get("decision").asText())
                .isEqualTo("GRANTED");
        assertThat(tentarAcesso(cred, "reader_apto_804", durante).get("decision").asText())
                .isEqualTo("GRANTED");

        // 4. Apartamento de outro hóspede: negado
        assertThat(tentarAcesso(cred, "reader_apto_805", durante).get("reason").asText())
                .isEqualTo("DOOR_NOT_AUTHORIZED");

        // 5. Academia autorizada: liberado
        assertThat(tentarAcesso(cred, "reader_academia", durante).get("decision").asText())
                .isEqualTo("GRANTED");

        // 6. Removida a permissão da academia: só a academia é negada
        mockMvc.perform(delete("/api/admin/credentials/" + cred + "/grants/academia")
                        .header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isOk());

        assertThat(tentarAcesso(cred, "reader_academia", durante).get("reason").asText())
                .isEqualTo("GRANT_REVOKED");
        assertThat(tentarAcesso(cred, "reader_apto_804", durante).get("decision").asText())
                .isEqualTo("GRANTED");

        // 8. Depois do checkout: negado
        assertThat(tentarAcesso(cred, "reader_portaria", depois).get("reason").asText())
                .isEqualTo("AFTER_CHECK_OUT");

        // 7. Credencial revogada: negado em todas as portas
        postJson("/api/admin/credentials/" + cred + "/revoke",
                """
                {"reason":"Hóspede perdeu o celular"}""", 200);

        for (String leitor : new String[]{"reader_portaria", "reader_apto_804"}) {
            assertThat(tentarAcesso(cred, leitor, durante).get("reason").asText())
                    .isEqualTo("CREDENTIAL_REVOKED");
        }
    }

    @Test
    @DisplayName("Leitor desconhecido é negado, e a tentativa fica registrada")
    void leitorDesconhecidoEhNegadoERegistrado() throws Exception {
        String cred = credencialDoJoao();

        JsonNode resposta = tentarAcesso(cred, "reader_inventado", Instant.now().toString());

        assertThat(resposta.get("reason").asText()).isEqualTo("READER_NOT_FOUND");

        // Toda tentativa, mesmo de um leitor inexistente, vira registro.
        assertThat(resposta.get("eventId").isNull()).isFalse();
    }
}
