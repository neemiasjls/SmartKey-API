package com.smartkey.web.error;

/** Lancada quando um id informado nao existe no banco. Vira HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entidade, Object id) {
        return new NotFoundException("%s não encontrado(a): %s".formatted(entidade, id));
    }
}
