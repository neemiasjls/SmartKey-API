package com.smartkey.domain.enums;

/** Situacao da credencial (a chave digital em si). */
public enum CredentialStatus {
    ACTIVE,
    /** Revogada manualmente pela administracao, antes do checkout. */
    REVOKED
}
