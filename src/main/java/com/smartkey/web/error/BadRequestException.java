package com.smartkey.web.error;

/** Lancada quando os dados enviados nao fazem sentido. Vira HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
