package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateReservationRequest(

        @NotNull
        UUID guestId,

        @NotBlank
        @Size(max = 50)
        @Schema(example = "804")
        String unitLabel,

        @NotNull
        @Schema(description = """
                Data e hora do check-in, com fuso horário.
                Para 18/09/2026 às 15:00 em Brasília, envie com -03:00 no final.""",
                example = "2026-09-18T15:00:00-03:00")
        Instant checkInAt,

        @NotNull
        @Schema(example = "2026-09-21T11:00:00-03:00")
        Instant checkOutAt,

        @Size(max = 500)
        String notes
) {}
