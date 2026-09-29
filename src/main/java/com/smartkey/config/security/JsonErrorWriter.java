package com.smartkey.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escreve erros de segurança no MESMO formato JSON usado pelo resto da API.
 *
 * Os erros de autenticação acontecem nos filtros, antes de chegar a qualquer
 * controller — então o tratador global de exceções não os alcança. Sem esta
 * classe, o cliente receberia um formato num caso e outro formato no outro.
 *
 * Usa o ObjectMapper em vez de montar o texto na mão: montar JSON concatenando
 * strings quebra (ou pior, permite injeção) assim que a mensagem tiver aspas.
 */
final class JsonErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", status.name());
        body.put("message", message);
        body.put("timestamp", Instant.now());
        body.put("details", null);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), body);
    }
}
