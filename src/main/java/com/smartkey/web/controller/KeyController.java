package com.smartkey.web.controller;

import com.google.zxing.WriterException;
import com.smartkey.config.ClientOrigin;
import com.smartkey.config.RateLimiter;
import com.smartkey.config.SmartKeyProperties;
import com.smartkey.service.KeyEnrollmentService;
import com.smartkey.service.QrCodeRenderer;
import com.smartkey.web.dto.EnrollKeyRequest;
import com.smartkey.web.dto.EnrolledKeyResponse;
import com.smartkey.web.dto.QrRenderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Endpoints usados pelo CELULAR DO HÓSPEDE.
 *
 * Repare que estes caminhos NÃO exigem a chave de administração — e não
 * poderiam exigir: o hóspede não pode ter poderes administrativos. A
 * autorização vem do token de uso único que veio no link dele.
 */
@RestController
@RequestMapping("/api/keys")
@Tag(name = "8. Chave do hóspede")
public class KeyController {

    /**
     * Desenhar QR custa processamento, e este endpoint é público. O app do
     * hóspede pede um a cada 20 segundos; 60 por minuto é folga de sobra.
     */
    private static final int MAX_QR_RENDERS_PER_MINUTE = 60;

    /**
     * Janela do limite de tentativas de ativacao (o limite em si e
     * configuravel em smartkey.enroll-attempts-per-minute).
     *
     * O token tem 32 bytes sorteados - forca bruta e inviavel de qualquer
     * forma. O limite existe para impedir que alguem consuma o servidor com
     * uma enxurrada de tentativas, e para deixar rastro de quem tenta.
     */
    private static final Duration ENROLL_WINDOW = Duration.ofMinutes(1);

    private final KeyEnrollmentService enrollmentService;
    private final QrCodeRenderer qrCodeRenderer;
    private final RateLimiter rateLimiter;
    private final ClientOrigin clientOrigin;
    private final SmartKeyProperties properties;

    public KeyController(KeyEnrollmentService enrollmentService,
                         QrCodeRenderer qrCodeRenderer,
                         RateLimiter rateLimiter,
                         ClientOrigin clientOrigin,
                         SmartKeyProperties properties) {
        this.enrollmentService = enrollmentService;
        this.qrCodeRenderer = qrCodeRenderer;
        this.rateLimiter = rateLimiter;
        this.clientOrigin = clientOrigin;
        this.properties = properties;
    }

    @PostMapping("/enroll")
    @Operation(summary = "Ativa a chave neste aparelho",
            description = """
                    Chamado uma única vez, pela página que o hóspede abre pelo link.

                    Envie apenas a chave **pública**. A privada é criada dentro do
                    aparelho e não deve sair de lá em hipótese alguma.

                    O token queima depois do uso: o mesmo link não ativa um
                    segundo aparelho.""")
    public EnrolledKeyResponse enroll(@Valid @RequestBody EnrollKeyRequest request,
                                      HttpServletRequest httpRequest) {

        String origin = clientOrigin.of(httpRequest);

        if (!rateLimiter.tryAcquire("enroll:" + origin,
                properties.getEnrollAttemptsPerMinute(), ENROLL_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Tentativas demais. Aguarde um minuto.");
        }

        return enrollmentService.enroll(request);
    }

    @PostMapping(value = "/qr", produces = "image/svg+xml")
    @Operation(summary = "Desenha um QR Code",
            description = """
                    Recebe no corpo o texto já assinado pelo aparelho e devolve o
                    desenho do QR em SVG.

                    O texto vai no corpo, e não na URL, para não ficar gravado em
                    logs de servidor e de proxy — ele pode ser um link de ativação.

                    Este endpoint não tem acesso à chave privada e não produz
                    assinatura nenhuma: ele só desenha o que recebe.""")
    public ResponseEntity<String> qr(@Valid @RequestBody QrRenderRequest request,
                                     HttpServletRequest httpRequest) {

        if (!rateLimiter.tryAcquire("qr:" + clientOrigin.of(httpRequest),
                MAX_QR_RENDERS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Tentativas demais. Aguarde um minuto.");
        }

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    // O QR muda a cada poucos segundos: guardar em cache
                    // devolveria um código já vencido.
                    .cacheControl(CacheControl.noStore())
                    .body(qrCodeRenderer.toSvg(request.data(), request.sizeOrDefault()));

        } catch (WriterException e) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Não foi possível gerar o QR para este conteúdo.");
        }
    }
}
