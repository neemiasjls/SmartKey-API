package com.smartkey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão de segurança: cada teste aqui é um ataque que FUNCIONOU contra uma
 * versão anterior deste sistema, e que não pode voltar a funcionar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DisplayName("Endurecimento de segurança")
class SecurityHardeningTest {

    private static final String ADMIN_KEY = "demo123";

    @Autowired
    private MockMvc mockMvc;

    // ==================================================================
    // O contorno da autenticação
    //
    // Os filtros antigos comparavam o endereço cru; o Spring roteava pelo
    // endereço normalizado. Estas variações passavam pelo meio dos dois e
    // davam acesso administrativo completo SEM chave nenhuma.
    // ==================================================================

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api;/admin/guests",
            "/api/admin;x=1/guests",
            "/api/%61dmin/guests",
            "/api/ADMIN/guests",
            "/api/admin/guests/",
            "/api/access;/events"
    })
    @DisplayName("Variações do endereço não dão acesso sem chave")
    void variacoesDeEnderecoNaoContornamAAutenticacao(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        // O que importa é NÃO ser 200. Recusado (400), sem autenticação (401)
        // ou inexistente (404) são todos aceitáveis.
        assertThat(status)
                .as("GET %s sem chave devolveu %d", path, status)
                .isIn(400, 401, 403, 404);
    }

    @Test
    @DisplayName("Sem chave não se cria nada, nem por endereço alternativo")
    void semChaveNaoCria() throws Exception {
        int status = mockMvc.perform(post("/api;/admin/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invasor"}"""))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(201);
    }

    @Test
    @DisplayName("Endereço de API não listado é negado por padrão")
    void apiNaoListadaEhNegada() throws Exception {
        mockMvc.perform(get("/api/qualquer-coisa-nova").header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("O console do banco H2 não está exposto")
    void consoleDoBancoFechado() throws Exception {
        int status = mockMvc.perform(get("/h2-console/"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(200);
    }

    @Test
    @DisplayName("O gerador de QR não aceita mais o conteúdo pela URL")
    void qrNaoAceitaConteudoNaUrl() throws Exception {
        // Pela URL, um link de ativação ficaria gravado nos logs de servidor e
        // de proxy — e quem lesse o log sequestraria a chave do hóspede.
        int status = mockMvc.perform(get("/api/keys/qr").param("data", "segredo"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(200);

        mockMvc.perform(post("/api/keys/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"conteudo","size":200}"""))
                .andExpect(status().isOk());
    }

    // ==================================================================
    // Erros do cliente não podem virar "erro do servidor" (500)
    // ==================================================================

    @Test
    @DisplayName("JSON malformado → 400")
    void jsonMalformado() throws Exception {
        mockMvc.perform(post("/api/admin/guests")
                        .header("x-admin-key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{quebrado"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Id que não é UUID → 400")
    void idInvalido() throws Exception {
        mockMvc.perform(get("/api/admin/guests/nao-e-uuid").header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Método não suportado → 405")
    void metodoErrado() throws Exception {
        mockMvc.perform(delete("/api/admin/guests").header("x-admin-key", ADMIN_KEY))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Arquivo inexistente → 404")
    void arquivoInexistente() throws Exception {
        mockMvc.perform(get("/nao-existe.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Paginação fora da faixa é ajustada, não quebra")
    void paginacaoForaDaFaixa() throws Exception {
        mockMvc.perform(get("/api/access/events")
                        .header("x-admin-key", ADMIN_KEY)
                        .param("size", "0")
                        .param("page", "-3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Código de porta com HTML é recusado na emissão da chave")
    void codigoDePortaComHtmlEhRecusado() throws Exception {
        mockMvc.perform(post("/api/admin/credentials")
                        .header("x-admin-key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":"00000000-0000-0000-0000-000000000001",
                                 "deviceId":"00000000-0000-0000-0000-000000000002",
                                 "accessPointCodes":["<img src=x onerror=alert(1)>"]}"""))
                .andExpect(status().isBadRequest());
    }

    // ==================================================================
    // Cabeçalhos de proteção do navegador
    // ==================================================================

    @Test
    @DisplayName("As páginas saem com os cabeçalhos de proteção")
    void cabecalhosDeProtecao() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}
