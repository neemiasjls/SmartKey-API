package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de desenho de um QR Code.
 *
 * Vai no CORPO da requisicao, e nao na URL, de proposito: o conteudo pode ser
 * um link de ativacao ou um codigo de acesso assinado. Enderecos ficam gravados
 * nos logs do servidor, do Render, do Cloudflare e do ngrok; o corpo, nao.
 */
public record QrRenderRequest(

        @NotBlank
        @Size(max = 512)
        String data,

        @Min(120)
        @Max(720)
        @Schema(defaultValue = "320")
        Integer size
) {
    public int sizeOrDefault() {
        return size == null ? 320 : size;
    }
}
