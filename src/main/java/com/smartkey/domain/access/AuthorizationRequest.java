package com.smartkey.domain.access;

import java.time.Instant;
import java.util.List;

/**
 * Tudo que o motor de decisao precisa saber para responder
 * "pode entrar ou nao?".
 *
 * Perceba que aqui nao existe banco de dados nem HTTP: sao dados puros.
 * Quem busca esses dados no banco e o AccessService.
 *
 * @param now         instante da tentativa
 * @param reader      dados do leitor, ou null se o leitor nao existe
 * @param credential  dados da credencial, ou null se ela nao existe
 * @param grants      permissoes da credencial (pode vir vazia)
 */
public record AuthorizationRequest(
        Instant now,
        ReaderSnapshot reader,
        CredentialSnapshot credential,
        List<GrantSnapshot> grants
) {
    public AuthorizationRequest {
        if (now == null) {
            throw new IllegalArgumentException("now nao pode ser null");
        }
        grants = (grants == null) ? List.of() : List.copyOf(grants);
    }
}
