package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReaderRequest(

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9_]+$",
                message = "use apenas letras minúsculas, números e underline")
        @Schema(description = "Identificador do aparelho", example = "reader_apto_804")
        String code,

        @NotBlank
        @Size(max = 200)
        @Schema(example = "Porta do apartamento 804")
        String name,

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9_]+$",
                message = "use apenas letras minúsculas, números e underline")
        @Schema(description = "Porta que este leitor controla", example = "apartamento_804")
        String accessPointCode
) {}
