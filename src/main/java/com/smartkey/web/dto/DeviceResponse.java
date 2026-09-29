package com.smartkey.web.dto;

import com.smartkey.domain.enums.DevicePlatform;
import com.smartkey.domain.model.Device;

import java.util.UUID;

public record DeviceResponse(
        UUID id,
        UUID guestId,
        DevicePlatform platform,
        String label,
        boolean hasPublicKey
) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(
                d.getId(),
                d.getGuest().getId(),
                d.getPlatform(),
                d.getLabel(),
                d.getPublicKey() != null && !d.getPublicKey().isBlank());
    }
}
