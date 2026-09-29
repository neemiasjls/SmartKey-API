package com.smartkey.web.error;

/** Lancada quando a operacao conflita com algo existente. Vira HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
