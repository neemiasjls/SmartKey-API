package com.smartkey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Limite de tentativas — com as regras de PRODUÇÃO, não as folgadas do demo.
 *
 * Simula o servidor atrás do proxy do Render (1 salto confiável), com o
 * limite real de 10 ativações por minuto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestPropertySource(properties = {
        "smartkey.enroll-attempts-per-minute=3",
        "smartkey.trusted-proxy-hops=1"
})
@DisplayName("Limite de tentativas")
class RateLimitTest {

    private static final String CORPO_INVALIDO = """
            {"token":"token-que-nao-existe","publicKey":"x","algorithm":"ECDSA_P256"}""";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Uma tentativa de ativação. O cabeçalho X-Forwarded-For imita o que o
     * proxy do Render entrega: o que o cliente mandou (se mandou), seguido
     * do IP real que o proxy enxergou, SEMPRE no final.
     */
    private int tentarAtivar(String xForwardedFor) throws Exception {
        return mockMvc.perform(post("/api/keys/enroll")
                        .header("X-Forwarded-For", xForwardedFor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_INVALIDO))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Passando do limite, a resposta é 429 — e não erro 500")
    void excessoDevolve429() throws Exception {
        String ip = "203.0.113.10";

        // As 3 primeiras passam pelo limite (e falham porque o token não existe).
        for (int i = 0; i < 3; i++) {
            assertThat(tentarAtivar(ip)).isEqualTo(404);
        }

        // A quarta é barrada pelo limite, com o código certo.
        assertThat(tentarAtivar(ip)).isEqualTo(429);
    }

    @Test
    @DisplayName("Forjar o X-Forwarded-For NÃO dribla o limite")
    void cabecalhoForjadoNaoDriblaOLimite() throws Exception {
        String ipReal = "203.0.113.20";

        // O atacante põe um "IP" diferente a cada tentativa no começo do
        // cabeçalho. Mas o proxy sempre acrescenta o IP real NO FINAL — e é
        // esse que o sistema usa.
        for (int i = 0; i < 3; i++) {
            assertThat(tentarAtivar("10.0.0." + i + ", " + ipReal)).isEqualTo(404);
        }

        assertThat(tentarAtivar("10.0.0.99, " + ipReal))
                .as("o IP forjado no início do cabeçalho não pode zerar a contagem")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("Origens diferentes têm limites independentes")
    void origensDiferentesNaoSeAfetam() throws Exception {
        for (int i = 0; i < 4; i++) {
            tentarAtivar("203.0.113.30");
        }

        // Um hóspede legítimo, em outro IP, não paga pelo abuso do primeiro.
        assertThat(tentarAtivar("203.0.113.31")).isEqualTo(404);
    }
}
