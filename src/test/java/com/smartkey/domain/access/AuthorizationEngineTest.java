package com.smartkey.domain.access;

import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;
import com.smartkey.domain.enums.AccessGrantStatus;
import com.smartkey.domain.enums.CredentialStatus;
import com.smartkey.domain.enums.ReaderStatus;
import com.smartkey.domain.enums.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do motor de decisao, usando exatamente o cenario do Joao.
 *
 * Nenhum destes testes precisa de banco de dados, internet ou servidor rodando.
 * Eles executam em milissegundos.
 */
@DisplayName("Motor de decisao de acesso")
class AuthorizationEngineTest {

    // ------------------------------------------------------------------
    // CENARIO: reserva do Joao, apartamento 804
    //   check-in : 18/09/2026 as 15:00 (horario de Brasilia)
    //   checkout : 21/09/2026 as 11:00 (horario de Brasilia)
    // ------------------------------------------------------------------

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private static final Instant CHECK_IN = brasilia(2026, 9, 18, 15, 0);
    private static final Instant CHECK_OUT = brasilia(2026, 9, 21, 11, 0);

    /** Um instante qualquer no meio da hospedagem: dia 19 as 10:00. */
    private static final Instant DURANTE = brasilia(2026, 9, 19, 10, 0);

    private static Instant brasilia(int ano, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(ano, mes, dia, hora, minuto)
                .atZone(BRASILIA)
                .toInstant();
    }

    // ------------------------------------------------------------------
    // Leitores cadastrados no predio
    // ------------------------------------------------------------------

    private static final ReaderSnapshot READER_PORTARIA = new ReaderSnapshot(
            "reader_portaria", "entrada_condominio", ReaderStatus.ACTIVE);

    private static final ReaderSnapshot READER_APTO_804 = new ReaderSnapshot(
            "reader_apto_804", "apartamento_804", ReaderStatus.ACTIVE);

    private static final ReaderSnapshot READER_APTO_805 = new ReaderSnapshot(
            "reader_apto_805", "apartamento_805", ReaderStatus.ACTIVE);

    private static final ReaderSnapshot READER_ACADEMIA = new ReaderSnapshot(
            "reader_academia", "academia", ReaderStatus.ACTIVE);

    // ------------------------------------------------------------------
    // Ajudantes para montar o cenario
    // ------------------------------------------------------------------

    private static CredentialSnapshot credencialAtiva() {
        return new CredentialSnapshot(
                "cred-joao", CredentialStatus.ACTIVE,
                CHECK_IN, CHECK_OUT, ReservationStatus.CONFIRMED);
    }

    /** As tres permissoes que o Joao recebe ao fazer a reserva. */
    private static List<GrantSnapshot> permissoesDoJoao() {
        return List.of(
                GrantSnapshot.active("entrada_condominio"),
                GrantSnapshot.active("apartamento_804"),
                GrantSnapshot.active("academia"));
    }

    private static AuthorizationResult tentar(Instant quando,
                                              ReaderSnapshot leitor,
                                              CredentialSnapshot credencial,
                                              List<GrantSnapshot> permissoes) {
        return AuthorizationEngine.decide(
                new AuthorizationRequest(quando, leitor, credencial, permissoes));
    }

    /** Atalho: cenario normal do Joao, variando apenas o instante e o leitor. */
    private static AuthorizationResult joaoTenta(Instant quando, ReaderSnapshot leitor) {
        return tentar(quando, leitor, credencialAtiva(), permissoesDoJoao());
    }

    // ==================================================================
    // OS 8 CASOS PRINCIPAIS (os casos 9 e 10 chegam na FASE 3)
    // ==================================================================

    @Test
    @DisplayName("1. Antes do check-in, o acesso e NEGADO")
    void caso1_antesDoCheckIn() {
        Instant umDiaAntes = brasilia(2026, 9, 17, 15, 0);

        AuthorizationResult r = joaoTenta(umDiaAntes, READER_PORTARIA);

        assertThat(r.decision()).isEqualTo(AccessDecision.DENIED);
        assertThat(r.reason()).isEqualTo(AccessDenyReason.BEFORE_CHECK_IN);
        assertThat(r.message()).isEqualTo("Ainda não chegou o horário do check-in");
    }

    @Test
    @DisplayName("2. Durante a hospedagem, a portaria e LIBERADA")
    void caso2_portariaDuranteHospedagem() {
        AuthorizationResult r = joaoTenta(DURANTE, READER_PORTARIA);

        assertThat(r.granted()).isTrue();
        assertThat(r.accessPointCode()).isEqualTo("entrada_condominio");
    }

    @Test
    @DisplayName("3. Durante a hospedagem, o apartamento 804 e LIBERADO")
    void caso3_apartamentoCorreto() {
        AuthorizationResult r = joaoTenta(DURANTE, READER_APTO_804);

        assertThat(r.granted()).isTrue();
        assertThat(r.accessPointCode()).isEqualTo("apartamento_804");
    }

    @Test
    @DisplayName("4. Tentar abrir o apartamento 805 e NEGADO")
    void caso4_apartamentoDeOutroHospede() {
        AuthorizationResult r = joaoTenta(DURANTE, READER_APTO_805);

        assertThat(r.decision()).isEqualTo(AccessDecision.DENIED);
        assertThat(r.reason()).isEqualTo(AccessDenyReason.DOOR_NOT_AUTHORIZED);
    }

    @Test
    @DisplayName("5. A academia e LIBERADA quando o hospede tem a permissao")
    void caso5_academiaAutorizada() {
        AuthorizationResult r = joaoTenta(DURANTE, READER_ACADEMIA);

        assertThat(r.granted()).isTrue();
    }

    @Test
    @DisplayName("6. Removida a permissao da academia, o acesso e NEGADO")
    void caso6_academiaComPermissaoRemovida() {
        List<GrantSnapshot> semAcademia = List.of(
                GrantSnapshot.active("entrada_condominio"),
                GrantSnapshot.active("apartamento_804"),
                new GrantSnapshot("academia", AccessGrantStatus.REVOKED, null, null));

        AuthorizationResult r = tentar(DURANTE, READER_ACADEMIA, credencialAtiva(), semAcademia);

        assertThat(r.decision()).isEqualTo(AccessDecision.DENIED);
        assertThat(r.reason()).isEqualTo(AccessDenyReason.GRANT_REVOKED);

        // Importante: as outras portas continuam funcionando normalmente.
        assertThat(tentar(DURANTE, READER_APTO_804, credencialAtiva(), semAcademia).granted())
                .isTrue();
    }

    @Test
    @DisplayName("7. Credencial revogada e NEGADA em todas as portas")
    void caso7_credencialRevogada() {
        CredentialSnapshot revogada = new CredentialSnapshot(
                "cred-joao", CredentialStatus.REVOKED,
                CHECK_IN, CHECK_OUT, ReservationStatus.CONFIRMED);

        for (ReaderSnapshot leitor : List.of(READER_PORTARIA, READER_APTO_804, READER_ACADEMIA)) {
            AuthorizationResult r = tentar(DURANTE, leitor, revogada, permissoesDoJoao());

            assertThat(r.decision()).isEqualTo(AccessDecision.DENIED);
            assertThat(r.reason()).isEqualTo(AccessDenyReason.CREDENTIAL_REVOKED);
        }
    }

    @Test
    @DisplayName("8. Depois do checkout, o acesso e NEGADO")
    void caso8_depoisDoCheckout() {
        Instant umaHoraDepois = brasilia(2026, 9, 21, 12, 0);

        AuthorizationResult r = joaoTenta(umaHoraDepois, READER_PORTARIA);

        assertThat(r.decision()).isEqualTo(AccessDecision.DENIED);
        assertThat(r.reason()).isEqualTo(AccessDenyReason.AFTER_CHECK_OUT);
    }

    // ==================================================================
    // CASOS DE BORDA - os minutos exatos do check-in e do checkout
    // ==================================================================

    @Nested
    @DisplayName("Limites exatos do horario")
    class LimitesDeHorario {

        @Test
        @DisplayName("No segundo exato do check-in, ja LIBERA")
        void noInstanteDoCheckIn() {
            assertThat(joaoTenta(CHECK_IN, READER_PORTARIA).granted()).isTrue();
        }

        @Test
        @DisplayName("Um segundo antes do check-in, ainda NEGA")
        void umSegundoAntesDoCheckIn() {
            AuthorizationResult r = joaoTenta(CHECK_IN.minusSeconds(1), READER_PORTARIA);

            assertThat(r.reason()).isEqualTo(AccessDenyReason.BEFORE_CHECK_IN);
        }

        @Test
        @DisplayName("Um segundo antes do checkout, ainda LIBERA")
        void umSegundoAntesDoCheckout() {
            assertThat(joaoTenta(CHECK_OUT.minusSeconds(1), READER_APTO_804).granted()).isTrue();
        }

        @Test
        @DisplayName("No segundo exato do checkout, ja NEGA")
        void noInstanteDoCheckout() {
            AuthorizationResult r = joaoTenta(CHECK_OUT, READER_APTO_804);

            assertThat(r.reason()).isEqualTo(AccessDenyReason.AFTER_CHECK_OUT);
        }
    }

    // ==================================================================
    // OUTRAS SITUACOES DE ERRO
    // ==================================================================

    @Nested
    @DisplayName("Situacoes de erro")
    class SituacoesDeErro {

        @Test
        @DisplayName("Leitor desconhecido e NEGADO")
        void leitorNaoCadastrado() {
            AuthorizationResult r = tentar(DURANTE, null, credencialAtiva(), permissoesDoJoao());

            assertThat(r.reason()).isEqualTo(AccessDenyReason.READER_NOT_FOUND);
        }

        @Test
        @DisplayName("Leitor desativado nega ate credencial valida")
        void leitorDesativado() {
            ReaderSnapshot desativado = new ReaderSnapshot(
                    "reader_portaria", "entrada_condominio", ReaderStatus.INACTIVE);

            AuthorizationResult r = tentar(DURANTE, desativado, credencialAtiva(), permissoesDoJoao());

            assertThat(r.reason()).isEqualTo(AccessDenyReason.READER_INACTIVE);
        }

        @Test
        @DisplayName("Credencial inexistente e NEGADA")
        void credencialInexistente() {
            AuthorizationResult r = tentar(DURANTE, READER_PORTARIA, null, List.of());

            assertThat(r.reason()).isEqualTo(AccessDenyReason.CREDENTIAL_NOT_FOUND);
        }

        @Test
        @DisplayName("Reserva cancelada invalida a credencial")
        void reservaCancelada() {
            CredentialSnapshot deReservaCancelada = new CredentialSnapshot(
                    "cred-joao", CredentialStatus.ACTIVE,
                    CHECK_IN, CHECK_OUT, ReservationStatus.CANCELLED);

            AuthorizationResult r = tentar(DURANTE, READER_PORTARIA, deReservaCancelada,
                    permissoesDoJoao());

            assertThat(r.reason()).isEqualTo(AccessDenyReason.RESERVATION_CANCELLED);
        }

        @Test
        @DisplayName("Credencial sem nenhuma permissao nega qualquer porta")
        void credencialSemPermissoes() {
            AuthorizationResult r = tentar(DURANTE, READER_PORTARIA, credencialAtiva(), List.of());

            assertThat(r.reason()).isEqualTo(AccessDenyReason.DOOR_NOT_AUTHORIZED);
        }
    }

    // ==================================================================
    // JANELA PROPRIA DE UMA PERMISSAO (ex: academia so ate as 22h)
    // ==================================================================

    @Test
    @DisplayName("Permissao com janela propria expira antes do checkout")
    void permissaoComJanelaPropria() {
        Instant academiaFecha = brasilia(2026, 9, 19, 22, 0);

        List<GrantSnapshot> comHorarioLimitado = List.of(
                GrantSnapshot.active("apartamento_804"),
                new GrantSnapshot("academia", AccessGrantStatus.ACTIVE, null, academiaFecha));

        // As 21:00 a academia ainda abre
        assertThat(tentar(brasilia(2026, 9, 19, 21, 0), READER_ACADEMIA,
                credencialAtiva(), comHorarioLimitado).granted()).isTrue();

        // As 23:00 nao abre mais...
        AuthorizationResult r = tentar(brasilia(2026, 9, 19, 23, 0), READER_ACADEMIA,
                credencialAtiva(), comHorarioLimitado);
        assertThat(r.reason()).isEqualTo(AccessDenyReason.GRANT_EXPIRED);

        // ...mas o apartamento continua abrindo
        assertThat(tentar(brasilia(2026, 9, 19, 23, 0), READER_APTO_804,
                credencialAtiva(), comHorarioLimitado).granted()).isTrue();
    }
}
