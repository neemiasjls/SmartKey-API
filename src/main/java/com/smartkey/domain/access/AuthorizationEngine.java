package com.smartkey.domain.access;

import com.smartkey.domain.enums.AccessGrantStatus;
import com.smartkey.domain.enums.CredentialStatus;
import com.smartkey.domain.enums.ReaderStatus;
import com.smartkey.domain.enums.ReservationStatus;

import java.time.Instant;
import java.util.List;

import static com.smartkey.domain.access.AuthorizationResult.denied;
import static com.smartkey.domain.access.AuthorizationResult.granted;
import static com.smartkey.domain.enums.AccessDenyReason.*;

/**
 * O CORACAO DO SISTEMA.
 *
 * Responde a unica pergunta que importa: esta credencial pode abrir esta porta
 * neste instante?
 *
 * Esta classe e PURA de proposito:
 *   - nao acessa banco de dados
 *   - nao conhece HTTP
 *   - nao depende do Spring
 *   - nao le o relogio do sistema (o instante "agora" chega como parametro)
 *
 * Por que isso importa: os 10 casos de teste que voce quer validar
 * (antes do check-in, porta errada, revogada, depois do checkout...) podem ser
 * testados aqui em milissegundos, sem subir servidor e sem banco de dados.
 *
 * Na FASE 3, a verificacao da assinatura criptografica acontecera ANTES de
 * chamar esta classe. Ou seja: primeiro provamos QUEM e o dispositivo, depois
 * perguntamos aqui SE ele pode entrar. As duas responsabilidades ficam
 * separadas.
 *
 * REGRA DE INTERVALO: a validade e [validFrom, validUntil).
 * O inicio entra, o fim nao. Um checkout marcado para 11:00 significa que
 * 10:59:59 ainda abre e 11:00:00 ja nao abre mais.
 */
public final class AuthorizationEngine {

    private AuthorizationEngine() {
        // classe utilitaria: nao deve ser instanciada
    }

    public static AuthorizationResult decide(AuthorizationRequest request) {
        final Instant now = request.now();

        // ---------------------------------------------------------------
        // 1) O leitor existe e esta ativo?
        // ---------------------------------------------------------------
        final ReaderSnapshot reader = request.reader();
        if (reader == null) {
            return denied(READER_NOT_FOUND, null);
        }
        if (reader.status() != ReaderStatus.ACTIVE) {
            return denied(READER_INACTIVE, reader.accessPointCode());
        }

        // A partir daqui sabemos QUAL porta esta sendo tentada.
        final String door = reader.accessPointCode();

        // ---------------------------------------------------------------
        // 2) A credencial existe e esta valida?
        // ---------------------------------------------------------------
        final CredentialSnapshot credential = request.credential();
        if (credential == null) {
            return denied(CREDENTIAL_NOT_FOUND, door);
        }
        if (credential.status() == CredentialStatus.REVOKED) {
            return denied(CREDENTIAL_REVOKED, door);
        }
        if (credential.reservationStatus() == ReservationStatus.CANCELLED) {
            return denied(RESERVATION_CANCELLED, door);
        }

        // ---------------------------------------------------------------
        // 3) Estamos dentro da janela da hospedagem?
        // ---------------------------------------------------------------
        if (now.isBefore(credential.validFrom())) {
            return denied(BEFORE_CHECK_IN, door);
        }
        if (!now.isBefore(credential.validUntil())) {
            // now >= validUntil
            return denied(AFTER_CHECK_OUT, door);
        }

        // ---------------------------------------------------------------
        // 4) Esta credencial tem permissao para ESTA porta?
        // ---------------------------------------------------------------
        final GrantSnapshot grant = findGrant(request.grants(), door);
        if (grant == null) {
            // A credencial e valida, mas nao inclui esta porta.
            // E o caso do Joao tentando abrir o apartamento 805.
            return denied(DOOR_NOT_AUTHORIZED, door);
        }
        if (grant.status() != AccessGrantStatus.ACTIVE) {
            return denied(GRANT_REVOKED, door);
        }

        // ---------------------------------------------------------------
        // 5) A permissao tem uma janela propria, mais restrita?
        // ---------------------------------------------------------------
        if (grant.validFrom() != null && now.isBefore(grant.validFrom())) {
            return denied(GRANT_NOT_YET_VALID, door);
        }
        if (grant.validUntil() != null && !now.isBefore(grant.validUntil())) {
            return denied(GRANT_EXPIRED, door);
        }

        // Passou por tudo.
        return granted(door);
    }

    /** Procura a permissao correspondente a porta. Retorna null se nao houver. */
    private static GrantSnapshot findGrant(List<GrantSnapshot> grants, String door) {
        if (grants == null || door == null) {
            return null;
        }
        for (GrantSnapshot grant : grants) {
            if (door.equals(grant.accessPointCode())) {
                return grant;
            }
        }
        return null;
    }
}
