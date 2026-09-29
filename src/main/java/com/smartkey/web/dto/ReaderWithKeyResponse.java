package com.smartkey.web.dto;

import com.smartkey.domain.enums.ReaderStatus;
import com.smartkey.domain.model.Reader;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Resposta do cadastro (ou da troca) da chave de um leitor.
 *
 * ESTA E A UNICA VEZ que a chave aparece. O servidor guarda apenas o hash
 * dela; se voce perder, nao ha como recuperar - so gerar outra.
 */
public record ReaderWithKeyResponse(
        UUID id,
        String code,
        String name,
        String accessPointCode,
        ReaderStatus status,

        @Schema(description = "A chave deste leitor. Anote agora: não será mostrada de novo.")
        String apiKey
) {
    public static ReaderWithKeyResponse from(Reader reader, String apiKey) {
        return new ReaderWithKeyResponse(
                reader.getId(), reader.getCode(), reader.getName(),
                reader.getAccessPointCode(), reader.getStatus(), apiKey);
    }
}
