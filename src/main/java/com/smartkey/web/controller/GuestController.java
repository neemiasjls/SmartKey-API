package com.smartkey.web.controller;

import com.smartkey.service.AdminService;
import com.smartkey.web.dto.CreateGuestRequest;
import com.smartkey.web.dto.GuestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/guests")
@Tag(name = "1. Hóspedes")
public class GuestController {

    private final AdminService adminService;

    public GuestController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um hóspede")
    public GuestResponse create(@Valid @RequestBody CreateGuestRequest request) {
        return GuestResponse.from(adminService.createGuest(request));
    }

    @GetMapping
    @Operation(summary = "Lista todos os hóspedes")
    public List<GuestResponse> list() {
        return adminService.listGuests().stream().map(GuestResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um hóspede pelo id")
    public GuestResponse get(@PathVariable UUID id) {
        return GuestResponse.from(adminService.getGuest(id));
    }
}
