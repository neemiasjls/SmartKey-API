package com.smartkey.web.controller;

import com.smartkey.service.AdminService;
import com.smartkey.web.dto.CreateDeviceRequest;
import com.smartkey.web.dto.DeviceResponse;
import com.smartkey.web.dto.RegisterPublicKeyRequest;
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
@RequestMapping("/api/admin/devices")
@Tag(name = "4. Dispositivos")
public class DeviceController {

    private final AdminService adminService;

    public DeviceController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra o celular de um hóspede",
            description = "Na FASE 3 este endpoint passará a receber a chave pública do aparelho.")
    public DeviceResponse create(@Valid @RequestBody CreateDeviceRequest request) {
        return DeviceResponse.from(adminService.createDevice(request));
    }

    @GetMapping
    @Operation(summary = "Lista todos os dispositivos")
    public List<DeviceResponse> list() {
        return adminService.listDevices().stream().map(DeviceResponse::from).toList();
    }

    @PostMapping("/{id}/public-key")
    @Operation(summary = "Cadastra a chave pública do aparelho",
            description = """
                    Chamado pelo próprio celular, depois de gerar seu par de chaves.

                    Envie APENAS a chave pública, em Base64, no formato X.509 SPKI.
                    A chave privada fica dentro do aparelho e **nunca** deve ser
                    enviada para o servidor — se algum sistema pedir a chave
                    privada, é golpe.

                    Sem esta chave cadastrada, o acesso é negado com
                    `DEVICE_KEY_MISSING`.""")
    public DeviceResponse registerPublicKey(@PathVariable UUID id,
                                            @Valid @RequestBody RegisterPublicKeyRequest request) {
        return DeviceResponse.from(adminService.registerPublicKey(id, request));
    }
}
