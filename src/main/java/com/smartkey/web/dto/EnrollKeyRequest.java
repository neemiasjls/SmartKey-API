package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Ativacao da chave no celular do hospede.
 *
 * Nao exige a chave de administracao: a autorizacao vem do token de uso unico
 * que veio no link. Assim o hospede consegue registrar o proprio aparelho sem
 * receber nenhum poder administrativo.
 */
public record EnrollKeyRequest(

        @NotBlank
        @Schema(description = "Token de uso único, recebido no link")
        String token,

        @NotBlank
        @Schema(description = "Chave PÚBLICA do aparelho, em Base64 (X.509 SPKI)")
        String publicKey,

        @Schema(example = "ECDSA_P256")
        String algorithm,

        @Schema(description = "Nome amigável do aparelho", example = "iPhone da Maria")
        String deviceLabel
) {}
