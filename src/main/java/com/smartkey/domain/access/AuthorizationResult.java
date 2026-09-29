package com.smartkey.domain.access;

import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;

/**
 * Resposta do motor de decisao.
 *
 * @param decision        GRANTED ou DENIED
 * @param reason          motivo da negativa; sempre null quando GRANTED
 * @param message         texto em portugues exibido na tela do leitor
 * @param accessPointCode porta envolvida; null se o leitor nem foi reconhecido
 */
public record AuthorizationResult(
        AccessDecision decision,
        AccessDenyReason reason,
        String message,
        String accessPointCode
) {
    public boolean granted() {
        return decision == AccessDecision.GRANTED;
    }

    static AuthorizationResult granted(String accessPointCode) {
        return new AuthorizationResult(
                AccessDecision.GRANTED, null, "Acesso liberado", accessPointCode);
    }

    static AuthorizationResult denied(AccessDenyReason reason, String accessPointCode) {
        return new AuthorizationResult(
                AccessDecision.DENIED, reason, reason.getMessage(), accessPointCode);
    }
}
