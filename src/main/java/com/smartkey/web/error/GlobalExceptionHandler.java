package com.smartkey.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transforma excecoes Java em respostas HTTP organizadas.
 *
 * Sem isto, um erro qualquer devolveria uma pagina de erro gigante, com o
 * caminho interno das classes - informacao que nao deve vazar para fora.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String error, String message, Instant timestamp, Object details) {
        static ApiError of(String error, String message) {
            return new ApiError(error, message, Instant.now(), null);
        }

        static ApiError of(String error, String message, Object details) {
            return new ApiError(error, message, Instant.now(), details);
        }
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", ex.getMessage()));
    }

    /** Erros de validacao (@NotBlank, @Email, etc): diz exatamente qual campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR",
                "Alguns campos estão inválidos.",
                fieldErrors));
    }

    /**
     * Erros que ja carregam o proprio status HTTP (429, 400, ...).
     *
     * Sem este tratador, eles caiam no generico logo abaixo e viravam 500 -
     * foi assim que "tentativas demais" chegava ao cliente como falha do
     * servidor, escondendo o motivo real.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(ApiError.of(status.name(), ex.getReason()));
    }

    /** Corpo que não é JSON válido, ou com tipo errado num campo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "MALFORMED_REQUEST", "O corpo da requisição não é um JSON válido."));
    }

    /** Parâmetro com formato errado, ex: um id que não é UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_PARAMETER",
                "Valor inválido para '%s'.".formatted(ex.getName())));
    }

    /**
     * Violações de regra do banco (valor duplicado, texto longo demais...)
     * que escaparam da validação. É erro do pedido, não do servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "CONFLICT", "Os dados conflitam com um registro existente."));
    }

    /**
     * Exceções do próprio Spring que já sabem qual status HTTP representam:
     * recurso inexistente (404), método não suportado (405), parâmetro
     * obrigatório ausente (400)...
     *
     * Sem este tratador, TODAS caíam no genérico abaixo e viravam 500 — um
     * robô pedindo "/wp-admin.php" enchia o log de "erros do servidor".
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleSpringStatus(ErrorResponseException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(ApiError.of(status.name(), ex.getBody().getDetail()));
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> handleSpringErrorResponse(Exception ex) {
        ErrorResponse error = (ErrorResponse) ex;
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(ApiError.of(status.name(), error.getBody().getDetail()));
    }

    /** Qualquer outro erro inesperado. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR",
                        "Ocorreu um erro inesperado no servidor."));
    }
}
