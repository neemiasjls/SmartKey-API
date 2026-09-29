package com.smartkey.web.dto;

import com.smartkey.domain.model.Guest;

import java.util.UUID;

public record GuestResponse(UUID id, String name, String email, String phone) {

    public static GuestResponse from(Guest guest) {
        return new GuestResponse(
                guest.getId(), guest.getName(), guest.getEmail(), guest.getPhone());
    }
}
