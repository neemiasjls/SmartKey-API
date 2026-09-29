package com.smartkey.web.dto;

import com.smartkey.domain.enums.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDeviceRequest(

        @NotNull
        UUID guestId,

        @NotNull
        @Schema(example = "ANDROID")
        DevicePlatform platform,

        @Size(max = 200)
        @Schema(example = "Samsung do João")
        String label
) {}
