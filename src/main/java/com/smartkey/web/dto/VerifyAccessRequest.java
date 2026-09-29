package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * A resposta assinada, enviada pelo leitor ao servidor.
 *
 * Este é o endpoint seguro: sem uma assinatura válida, feita pela chave
 * privada que está dentro do celular, o acesso é negado.
 */
public record VerifyAccessRequest(

        @NotNull
        @Schema(description = "Id do desafio recebido em /access/challenge")
        UUID challengeId,

        @NotNull
        UUID credentialId,

        @NotBlank
        @Schema(description = """
                Assinatura em Base64. Aceita tanto o formato DER (Android
                Keystore) quanto o formato cru de 64 bytes (Web Crypto).""")
        String signature,

        @Schema(description = "OPCIONAL, apenas em desenvolvimento: simula outra data/hora")
        Instant at
) {}
