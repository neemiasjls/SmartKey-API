package com.smartkey.web.controller;

import com.smartkey.service.AccessService;
import com.smartkey.service.AdminService;
import com.smartkey.web.ReaderScopeGuard;
import com.smartkey.web.dto.AccessCheckRequest;
import com.smartkey.web.dto.AccessCheckResponse;
import com.smartkey.web.dto.AccessEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * O ENDPOINT PRINCIPAL DO SISTEMA.
 *
 * E aqui que o leitor pergunta "pode abrir?" e recebe a resposta que vai
 * pintar a tela de verde ou vermelho.
 *
 * FASE 1 (agora): o leitor envia apenas o id da credencial. Isso NAO e seguro
 * para producao - qualquer um que descobrisse o id conseguiria entrar. Serve
 * para validarmos toda a regra de negocio (horarios, portas, revogacao) antes
 * de acrescentar a criptografia.
 *
 * FASE 3: este mesmo endpoint passara a exigir challengeId + signature, e a
 * verificacao criptografica acontecera ANTES da consulta de permissoes.
 */
@RestController
@RequestMapping("/api/access")
@Tag(name = "6. Acesso (usado pelo leitor)")
public class AccessController {

    private final AccessService accessService;
    private final AdminService adminService;
    private final ReaderScopeGuard readerScopeGuard;

    public AccessController(AccessService accessService,
                            AdminService adminService,
                            ReaderScopeGuard readerScopeGuard) {
        this.accessService = accessService;
        this.adminService = adminService;
        this.readerScopeGuard = readerScopeGuard;
    }

    @PostMapping("/check")
    @Operation(summary = "Pode abrir esta porta?",
            description = """
                    Devolve GRANTED ou DENIED, junto com o motivo em português.

                    Toda tentativa - liberada ou negada - fica registrada no
                    histórico.

                    Dica para testar: o campo opcional 'at' permite simular a
                    tentativa em outra data/hora, para verificar o comportamento
                    antes do check-in e depois do checkout sem ter que esperar.""")
    public AccessCheckResponse check(@Valid @RequestBody AccessCheckRequest request) {
        readerScopeGuard.ensureCanActAs(request.readerCode());
        return accessService.check(request);
    }

    @GetMapping("/events")
    @Operation(summary = "Histórico de tentativas de acesso")
    public List<AccessEventResponse> events(
            @RequestParam(required = false) UUID credentialId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        // Fora da faixa, o PageRequest lancaria excecao (erro 500).
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200));

        var result = (credentialId == null)
                ? adminService.listEvents(pageable)
                : adminService.listEventsByCredential(credentialId, pageable);

        return result.getContent().stream().map(AccessEventResponse::from).toList();
    }
}
