package com.smartkey.web.controller;

import com.smartkey.domain.enums.ReaderStatus;
import com.smartkey.service.AdminService;
import com.smartkey.web.dto.CreateReaderRequest;
import com.smartkey.web.dto.ReaderResponse;
import com.smartkey.web.dto.ReaderWithKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/readers")
@Tag(name = "2. Leitores (fechaduras)")
public class ReaderController {

    private final AdminService adminService;

    public ReaderController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um leitor e gera a chave dele",
            description = """
                    `code` = qual aparelho. `accessPointCode` = qual porta ele controla.

                    **A resposta traz a chave deste leitor, e é a única vez que
                    ela aparece.** O servidor guarda apenas o hash — se você
                    perder a chave, não há como recuperá-la, só gerar outra.

                    Cada leitor tem a sua própria chave de propósito: uma
                    fechadura arrancada da parede não pode virar administradora
                    do sistema.""")
    public ReaderWithKeyResponse create(@Valid @RequestBody CreateReaderRequest request) {
        return adminService.createReader(request);
    }

    @PostMapping("/{id}/api-key")
    @Operation(summary = "Gera uma chave nova para este leitor",
            description = """
                    Use quando o aparelho for perdido, roubado ou substituído.

                    A chave antiga para de funcionar imediatamente, e nenhum
                    outro leitor é afetado.""")
    public ReaderWithKeyResponse rotateKey(@PathVariable UUID id) {
        return adminService.rotateReaderKey(id);
    }

    @GetMapping
    @Operation(summary = "Lista todos os leitores")
    public List<ReaderResponse> list() {
        return adminService.listReaders().stream().map(ReaderResponse::from).toList();
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Ativa ou desativa um leitor")
    public ReaderResponse setStatus(@PathVariable UUID id, @RequestParam ReaderStatus status) {
        return ReaderResponse.from(adminService.setReaderStatus(id, status));
    }
}
