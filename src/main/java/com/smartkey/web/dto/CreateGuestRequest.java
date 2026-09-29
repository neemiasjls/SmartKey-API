package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuestRequest(

        @NotBlank
        @Size(max = 200)
        @Schema(example = "João da Silva")
        String name,

        @Email
        @Size(max = 200)
        @Schema(example = "joao@exemplo.com")
        String email,

        @Size(max = 50)
        @Schema(example = "+5511999998888")
        String phone
) {}
