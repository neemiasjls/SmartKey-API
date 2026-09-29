package com.smartkey.domain.access;

import com.smartkey.domain.enums.ReaderStatus;

/**
 * Foto dos dados do leitor no momento da tentativa.
 *
 * @param code            identificador do leitor, ex: "reader_apto_804"
 * @param accessPointCode a porta que ele controla,  ex: "apartamento_804"
 * @param status          ACTIVE ou INACTIVE
 */
public record ReaderSnapshot(
        String code,
        String accessPointCode,
        ReaderStatus status
) {}
