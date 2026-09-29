package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * O desafio entregue ao leitor, que o repassa ao celular.
 *
 * O campo messageTemplate mostra exatamente como montar o texto a ser
 * assinado: basta trocar {credentialId} pelo id da credencial.
 */
public record ChallengeResponse(

        UUID challengeId,

        @Schema(description = "Número sorteado, em Base64")
        String nonce,

        Instant expiresAt,

        @Schema(description = "Algoritmo esperado", example = "ECDSA_P256")
        String algorithm,

        @Schema(description = "Formato exato do texto que o celular deve assinar",
                example = "SMARTKEY-ACCESS-v1|<challengeId>|<nonce>|reader_apto_804|{credentialId}")
        String messageTemplate
) {}
