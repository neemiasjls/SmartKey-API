package com.smartkey.domain.enums;

/** Situacao de uma permissao individual (ex: academia). */
public enum AccessGrantStatus {
    ACTIVE,
    /** Permissao retirada, mas mantida no historico para auditoria. */
    REVOKED
}
