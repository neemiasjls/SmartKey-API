package com.smartkey.web.error;

/** Autenticado, mas sem permissao para ESTA acao. Vira HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
