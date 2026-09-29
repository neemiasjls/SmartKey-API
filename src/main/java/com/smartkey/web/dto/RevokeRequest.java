package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record RevokeRequest(

        @Size(max = 300)
        @Schema(example = "Hóspede perdeu o celular")
        String reason
) {}
