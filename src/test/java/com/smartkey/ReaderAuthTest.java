package com.smartkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticação e limites de poder dos LEITORES.
 *
 * O cenário que estes testes protegem é concreto: uma fechadura fica numa
 * parede, num corredor, ao alcance de qualquer pessoa. Quem abrir o aparelho
 * consegue extrair o que estiver dentro dele.
 *
 * A pergunta que cada teste responde é: o que esse alguém conseguiria fazer
 * com a chave que encontrou lá dentro?
 *
 * A resposta desejada é "quase nada" — só perguntar se uma credencial abre
 * AQUELA porta específica.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DisplayName("Segurança dos leitores")
class ReaderAuthTest {

    private static final String ADMIN_KEY = "demo123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Chave do leitor da academia — o nosso "leitor comprometido". */
    private String chaveDaAcademia;
    private String codigoDaAcademia;

    private UUID idDaAcademia;

    @BeforeEach
    void criarLeitorProprio() throws Exception {
        // Um leitor novo a cada teste, para um não interferir no outro.
        String sufixo = UUID.randomUUID().toString().substring(0, 8).replace("-", "");
        codigoDaAcademia = "reader_teste_" + sufixo;

        JsonNode leitor = adminPost("/api/admin/readers", """
                {"code":"%s","name":"Leitor de teste","accessPointCode":"academia"}"""
                .formatted(codigoDaAcademia), 201);

        chaveDaAcademia = leitor.get("apiKey").asText();
        idDaAcademia = UUID.fromString(leitor.get("id").asText());
    }

    // ------------------------------------------------------------------

    private JsonNode adminPost(String url, String body, int expected) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("x-admin-key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expected))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? objectMapper.createObjectNode()
                : objectMapper.readTree(content);
    }

    /** Pede um desafio usando a chave DE LEITOR informada. */
    private MvcResult pedirDesafioComoLeitor(String readerKey, String readerCode)
            throws Exception {
        return mockMvc.perform(post("/api/access/challenge")
                        .header("x-reader-key", readerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"readerCode":"%s"}""".formatted(readerCode)))
                .andReturn();
    }

    // ==================================================================
    // A CHAVE
    // ==================================================================

    @Test
    @DisplayName("Cada leitor nasce com a sua própria chave")
    void leitorNasceComChave() {
        assertThat(chaveDaAcademia).isNotBlank();

        // O prefixo permite reconhecer o que é aquele texto se ele aparecer
        // onde não devia — num log, num print, num commit.
        assertThat(chaveDaAcademia).startsWith("rdr_");

        // Entropia suficiente: 32 bytes viram ~43 caracteres em Base64.
        assertThat(chaveDaAcademia.length()).isGreaterThan(40);
    }

    @Test
    @DisplayName("A chave NÃO volta a aparecer nas consultas")
    void chaveNaoApareceNasListagens() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/readers")
                        .header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();

        String corpo = result.getResponse().getContentAsString();

        // Nem a chave, nem o hash dela, saem na listagem.
        assertThat(corpo).doesNotContain(chaveDaAcademia);
        assertThat(corpo).doesNotContain("apiKey");
        assertThat(corpo).doesNotContain("apiKeyHash");
    }

    @Test
    @DisplayName("Com a própria chave, o leitor consulta a SUA porta")
    void leitorUsaAPropriaPorta() throws Exception {
        MvcResult result = pedirDesafioComoLeitor(chaveDaAcademia, codigoDaAcademia);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("challengeId");
    }

    // ==================================================================
    // OS LIMITES — o que um leitor roubado NÃO consegue fazer
    // ==================================================================

    @Test
    @DisplayName("Um leitor NÃO consegue responder pela porta de outro")
    void leitorNaoRespondePorOutraPorta() throws Exception {
        // Este é o ataque de retransmissão: pedir um desafio da porta do
        // apartamento usando a chave do leitor da academia, apresentá-lo a um
        // hóspede desavisado e usar a resposta para abrir o apartamento.
        MvcResult result = pedirDesafioComoLeitor(chaveDaAcademia, "reader_apto_804");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("FORBIDDEN");
    }

    /**
     * 403, e não 401: o leitor está autenticado (a chave é válida), mas não
     * tem PERMISSÃO para isto. 401 significaria "não sei quem você é".
     */
    @Test
    @DisplayName("Um leitor NÃO consegue criar reservas nem hóspedes")
    void leitorNaoTemPoderAdministrativo() throws Exception {
        for (String url : new String[]{
                "/api/admin/guests", "/api/admin/readers", "/api/admin/credentials"}) {

            mockMvc.perform(post(url)
                            .header("x-reader-key", chaveDaAcademia)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Um leitor NÃO consegue ler o histórico de acessos")
    void leitorNaoLeHistorico() throws Exception {
        // O histórico revela a rotina dos hóspedes: quando entram, quando saem.
        // Uma fechadura não tem por que saber disso.
        mockMvc.perform(get("/api/access/events")
                        .header("x-reader-key", chaveDaAcademia))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem chave nenhuma, o acesso é recusado")
    void semChaveEhRecusado() throws Exception {
        mockMvc.perform(post("/api/access/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"readerCode":"reader_apto_804"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Chave de leitor inventada é recusada")
    void chaveInventadaEhRecusada() throws Exception {
        MvcResult result = pedirDesafioComoLeitor(
                "rdr_chave-que-nunca-existiu-aaaaaaaaaaaaaaaaaa", codigoDaAcademia);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    // ==================================================================
    // TROCA DE CHAVE
    // ==================================================================

    @Test
    @DisplayName("Trocar a chave invalida a anterior imediatamente")
    void trocaDeChaveInvalidaAAnterior() throws Exception {
        // A chave atual funciona...
        assertThat(pedirDesafioComoLeitor(chaveDaAcademia, codigoDaAcademia)
                .getResponse().getStatus()).isEqualTo(200);

        // ...o aparelho é roubado, e geramos outra.
        JsonNode nova = adminPost("/api/admin/readers/" + idDaAcademia + "/api-key", "", 200);
        String chaveNova = nova.get("apiKey").asText();

        assertThat(chaveNova).isNotEqualTo(chaveDaAcademia);

        // A antiga morre na hora.
        assertThat(pedirDesafioComoLeitor(chaveDaAcademia, codigoDaAcademia)
                .getResponse().getStatus()).isEqualTo(401);

        // E a nova funciona.
        assertThat(pedirDesafioComoLeitor(chaveNova, codigoDaAcademia)
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Trocar a chave de um leitor não afeta os outros")
    void trocaDeChaveNaoAfetaOutrosLeitores() throws Exception {
        String outroCodigo = "reader_outro_"
                + UUID.randomUUID().toString().substring(0, 8).replace("-", "");

        String outraChave = adminPost("/api/admin/readers", """
                {"code":"%s","name":"Outro leitor","accessPointCode":"academia"}"""
                .formatted(outroCodigo), 201).get("apiKey").asText();

        // Troca a chave do PRIMEIRO leitor.
        adminPost("/api/admin/readers/" + idDaAcademia + "/api-key", "", 200);

        // O segundo continua funcionando normalmente.
        assertThat(pedirDesafioComoLeitor(outraChave, outroCodigo)
                .getResponse().getStatus()).isEqualTo(200);
    }

    // ==================================================================
    // A ADMINISTRAÇÃO CONTINUA PODENDO TUDO
    // ==================================================================

    @Test
    @DisplayName("A administração continua consultando qualquer porta")
    void administracaoContinuaPodendoTudo() throws Exception {
        // O painel de testes precisa disso; e quem tem a chave de
        // administração já podia tudo de qualquer forma.
        for (String leitor : new String[]{
                "reader_apto_804", "reader_portaria", codigoDaAcademia}) {

            mockMvc.perform(post("/api/access/challenge")
                            .header("x-admin-key", ADMIN_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"readerCode":"%s"}""".formatted(leitor)))
                    .andExpect(status().isOk());
        }
    }
}
