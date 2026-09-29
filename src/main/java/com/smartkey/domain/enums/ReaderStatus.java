package com.smartkey.domain.enums;

/** Situacao de um leitor / fechadura. */
public enum ReaderStatus {
    ACTIVE,
    /** Leitor desativado: nega tudo, mesmo credenciais validas. */
    INACTIVE
}
