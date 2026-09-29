package com.smartkey.web.controller;

import com.smartkey.service.AdminService;
import com.smartkey.web.dto.AccessGrantResponse;
import com.smartkey.web.dto.AddGrantRequest;
import com.smartkey.web.dto.CredentialResponse;
import com.smartkey.web.dto.IssueCredentialRequest;
import com.smartkey.web.dto.RevokeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/credentials")
@Tag(name = "5. Credenciais (a chave digital)")
public class CredentialController {

    private final AdminService adminService;

    public CredentialController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emite a chave digital",
            description = """
                    Junta uma reserva com um dispositivo e define quais portas
                    a chave abre. A validade copia automaticamente o check-in e
                    o checkout da reserva - é isso que faz a chave parar de
                    funcionar sozinha depois do checkout.""")
    public CredentialResponse issue(@Valid @RequestBody IssueCredentialRequest request) {
        return CredentialResponse.from(adminService.issueCredential(request));
    }

    @GetMapping
    @Operation(summary = "Lista todas as credenciais")
    public List<CredentialResponse> list() {
        return adminService.listCredentials().stream().map(CredentialResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma credencial, com suas permissões")
    public CredentialResponse get(@PathVariable UUID id) {
        return CredentialResponse.from(adminService.getCredential(id));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoga a chave inteira",
            description = "Usado quando o hóspede perde o celular ou sai antes do prazo.")
    public CredentialResponse revoke(@PathVariable UUID id,
                                     @Valid @RequestBody(required = false) RevokeRequest request) {
        String reason = (request == null) ? null : request.reason();
        return CredentialResponse.from(adminService.revokeCredential(id, reason));
    }

    // -------------------------------------------------------------------
    // Permissoes individuais
    // -------------------------------------------------------------------

    @PostMapping("/{id}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Acrescenta uma porta a esta chave")
    public AccessGrantResponse addGrant(@PathVariable UUID id,
                                        @Valid @RequestBody AddGrantRequest request) {
        return AccessGrantResponse.from(adminService.addGrant(id, request));
    }

    @DeleteMapping("/{id}/grants/{accessPointCode}")
    @Operation(summary = "Retira uma porta desta chave",
            description = """
                    Exemplo: retirar 'academia'. A permissão não é apagada,
                    apenas marcada como revogada, para o histórico continuar
                    explicável.""")
    public AccessGrantResponse revokeGrant(@PathVariable UUID id,
                                           @PathVariable String accessPointCode) {
        return AccessGrantResponse.from(adminService.revokeGrant(id, accessPointCode));
    }
}
