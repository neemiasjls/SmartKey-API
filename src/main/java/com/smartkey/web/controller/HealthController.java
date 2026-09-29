package com.smartkey.web.controller;

import com.smartkey.config.SmartKeyProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint público para confirmar que a API está no ar.
 *
 * Também é usado:
 *   - pelo Render, para saber se o serviço subiu;
 *   - pelo app do hóspede, para acertar o relógio: o QR carrega o horário em
 *     que foi gerado, e um celular com o relógio errado teria todo código
 *     recusado como vencido.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "0. Saúde", description = "Verificação de que a API está no ar")
public class HealthController {

    private final SmartKeyProperties properties;

    public HealthController(SmartKeyProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    @Operation(summary = "Confirma que a API está respondendo, e informa a hora do servidor")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "smartkey-api",
                "time", Instant.now(),
                "demoMode", properties.isDemoMode());
    }
}
