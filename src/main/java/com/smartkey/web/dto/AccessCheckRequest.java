package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Pergunta feita pelo leitor: "esta credencial pode abrir minha porta?"
 *
 * Na FASE 3 este objeto ganhara os campos challengeId e signature.
 */
public record AccessCheckRequest(

        @NotBlank
        @Schema(example = "reader_apto_804")
        String readerCode,

        @NotNull
        @Schema(description = "Id da credencial apresentada")
        UUID credentialId,

        @Schema(description = """
                OPCIONAL. Simula a tentativa em outra data/hora, para testar
                "antes do check-in" e "depois do checkout" sem esperar.
                Só funciona se allow-time-travel estiver ligado.
                Formato ISO-8601, ex: 2026-09-19T13:00:00Z""",
                example = "2026-09-19T13:00:00Z")
        Instant at
) {}
