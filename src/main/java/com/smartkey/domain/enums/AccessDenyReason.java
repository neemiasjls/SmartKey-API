package com.smartkey.domain.enums;

/**
 * Motivo pelo qual um acesso foi negado.
 *
 * Cada motivo carrega uma mensagem em portugues, que e exatamente o texto
 * exibido na tela do aplicativo leitor (FASE 5) abaixo do "ACESSO NEGADO".
 */
public enum AccessDenyReason {

    // ---- Problemas com o leitor / porta -------------------------------------

    READER_NOT_FOUND("Leitor não cadastrado no sistema"),
    READER_INACTIVE("Leitor desativado"),

    // ---- Problemas com a credencial ----------------------------------------

    CREDENTIAL_NOT_FOUND("Credencial não encontrada"),
    CREDENTIAL_REVOKED("Credencial revogada"),
    RESERVATION_CANCELLED("Reserva cancelada"),

    // ---- Problemas de horario ----------------------------------------------

    BEFORE_CHECK_IN("Ainda não chegou o horário do check-in"),
    AFTER_CHECK_OUT("Credencial expirada: o checkout já passou"),

    // ---- Problemas de permissao --------------------------------------------

    DOOR_NOT_AUTHORIZED("Porta não autorizada para esta credencial"),
    GRANT_REVOKED("Permissão removida para esta porta"),
    GRANT_NOT_YET_VALID("Permissão ainda não está válida neste horário"),
    GRANT_EXPIRED("Permissão expirada para esta porta"),

    // ---- Problemas de criptografia (usados a partir da FASE 3) -------------

    DEVICE_KEY_MISSING("Dispositivo sem chave pública cadastrada"),
    INVALID_SIGNATURE("Assinatura inválida"),
    CHALLENGE_NOT_FOUND("Desafio não encontrado"),
    CHALLENGE_EXPIRED("Desafio expirado"),
    CHALLENGE_ALREADY_USED("Desafio já utilizado (tentativa de repetição)");

    private final String message;

    AccessDenyReason(String message) {
        this.message = message;
    }

    /** Texto amigavel, exibido na tela do leitor. */
    public String getMessage() {
        return message;
    }
}
