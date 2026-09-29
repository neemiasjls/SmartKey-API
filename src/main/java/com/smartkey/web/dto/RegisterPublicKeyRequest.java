package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Cadastro da chave PÚBLICA de um dispositivo.
 *
 * A chave privada correspondente permanece dentro do aparelho e NUNCA é
 * enviada para cá. Se algum dia alguém pedir a chave privada, é golpe.
 */
public record RegisterPublicKeyRequest(

        @NotBlank
        @Schema(description = "Chave pública em Base64, formato X.509 SPKI")
        String publicKey,

        @Schema(description = "ECDSA_P256 (padrão) ou ED25519", example = "ECDSA_P256")
        String algorithm
) {}
