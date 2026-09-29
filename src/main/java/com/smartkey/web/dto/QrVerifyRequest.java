package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** O que o leitor envia depois de ler o QR com a camera. */
public record QrVerifyRequest(

        @NotBlank
        @Schema(example = "reader_apto_804")
        String readerCode,

        @NotBlank
        @Schema(description = "Conteúdo lido do QR, exatamente como veio",
                example = "SK1.ZZZ.nonce.1758000000.assinatura")
        String qrContent,

        @Schema(description = "OPCIONAL, apenas em desenvolvimento")
        Instant at
) {}
