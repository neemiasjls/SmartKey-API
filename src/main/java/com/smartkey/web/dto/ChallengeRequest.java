package com.smartkey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Pedido do leitor: "me dê um desafio novo para apresentar ao celular". */
public record ChallengeRequest(

        @NotBlank
        @Schema(example = "reader_apto_804")
        String readerCode
) {}
