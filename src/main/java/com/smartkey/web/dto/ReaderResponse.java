package com.smartkey.web.dto;

import com.smartkey.domain.enums.ReaderStatus;
import com.smartkey.domain.model.Reader;

import java.util.UUID;

public record ReaderResponse(
        UUID id,
        String code,
        String name,
        String accessPointCode,
        ReaderStatus status
) {
    public static ReaderResponse from(Reader reader) {
        return new ReaderResponse(
                reader.getId(),
                reader.getCode(),
                reader.getName(),
                reader.getAccessPointCode(),
                reader.getStatus());
    }
}
