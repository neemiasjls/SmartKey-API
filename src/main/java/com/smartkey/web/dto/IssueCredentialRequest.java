package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Emite a chave digital para um dispositivo, numa reserva. */
public record IssueCredentialRequest(

        @NotNull
        UUID reservationId,

        @NotNull
        UUID deviceId,

        @NotEmpty
        @Size(max = 50)
        @Schema(description = "Portas que esta chave poderá abrir",
                example = "[\"entrada_condominio\", \"apartamento_804\", \"academia\"]")
        List<@NotBlank
             @Size(max = 100)
             @Pattern(regexp = "^[A-Za-z0-9_]+$",
                      message = "use apenas letras, números e underline")
             String> accessPointCodes
) {}
