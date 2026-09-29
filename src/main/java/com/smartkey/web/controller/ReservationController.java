package com.smartkey.web.controller;

import com.smartkey.service.AdminService;
import com.smartkey.web.dto.CreateReservationRequest;
import com.smartkey.web.dto.ReservationResponse;
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
@RequestMapping("/api/admin/reservations")
@Tag(name = "3. Reservas")
public class ReservationController {

    private final AdminService adminService;

    public ReservationController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma reserva")
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return ReservationResponse.from(adminService.createReservation(request));
    }

    @GetMapping
    @Operation(summary = "Lista todas as reservas")
    public List<ReservationResponse> list() {
        return adminService.listReservations().stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma reserva pelo id")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(adminService.getReservation(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela a reserva",
            description = "Invalida de uma vez todas as credenciais desta reserva.")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return ReservationResponse.from(adminService.cancelReservation(id));
    }
}
