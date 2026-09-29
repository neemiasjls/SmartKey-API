package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Acrescenta (ou reativa) uma permissao numa credencial existente. */
public record AddGrantRequest(

        @NotBlank
        @Pattern(regexp = "^[a-z0-9_]+$",
                message = "use apenas letras minúsculas, números e underline")
        @Schema(example = "academia")
        String accessPointCode,

        @Schema(description = "OPCIONAL: janela própria, menor que a da credencial")
        Instant validFrom,

        Instant validUntil
) {}
