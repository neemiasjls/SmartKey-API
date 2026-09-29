package com.smartkey.service;

import com.smartkey.config.SmartKeyProperties;
import com.smartkey.domain.access.AuthorizationEngine;
import com.smartkey.domain.access.AuthorizationRequest;
import com.smartkey.domain.access.AuthorizationResult;
import com.smartkey.domain.crypto.QrPayload;
import com.smartkey.domain.crypto.SignatureAlgorithm;
import com.smartkey.domain.crypto.SignatureVerifier;
import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;
import com.smartkey.domain.model.AccessEvent;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.model.Reader;
import com.smartkey.repository.AccessEventRepository;
import com.smartkey.repository.CredentialRepository;
import com.smartkey.repository.ReaderRepository;
import com.smartkey.repository.UsedQrNonceRepository;
import com.smartkey.web.dto.AccessCheckResponse;
import com.smartkey.web.dto.QrVerifyRequest;
import com.smartkey.web.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Acesso por QR Code — o canal que funciona em Android e iPhone sem instalar
 * nada da loja.
 *
 * DIFERENÇA PARA O CANAL NFC:
 *
 * No NFC o leitor fala primeiro e manda o desafio. Com QR isso é impossível:
 * o celular mostra, o leitor lê, e pronto. Então o número que não pode se
 * repetir passa a ser sorteado pelo próprio celular.
 *
 * Como isso continua sendo seguro:
 *
 *   1. O código vale por poucos segundos (o celular assina o horário junto).
 *   2. Cada número só é aceito UMA vez — a chave primária da tabela
 *      used_qr_nonces recusa a segunda gravação.
 *   3. A assinatura continua sendo feita pela chave privada que vive dentro
 *      do aparelho.
 *
 * É honestamente um pouco mais fraco que o desafio do NFC, porque o leitor não
 * contribui com aleatoriedade. É o mesmo desenho dos cartões de embarque de
 * companhias aéreas, e a janela curta é o que compensa.
 */
@Service
public class QrAccessService {

    private static final Logger log = LoggerFactory.getLogger(QrAccessService.class);

    private final ReaderRepository readerRepository;
    private final CredentialRepository credentialRepository;
    private final AccessEventRepository accessEventRepository;
    private final UsedQrNonceRepository usedQrNonceRepository;
    private final QrNonceClaimer nonceClaimer;
    private final SmartKeyProperties properties;

    public QrAccessService(ReaderRepository readerRepository,
                           CredentialRepository credentialRepository,
                           AccessEventRepository accessEventRepository,
                           UsedQrNonceRepository usedQrNonceRepository,
                           QrNonceClaimer nonceClaimer,
                           SmartKeyProperties properties) {
        this.readerRepository = readerRepository;
        this.credentialRepository = credentialRepository;
        this.accessEventRepository = accessEventRepository;
        this.usedQrNonceRepository = usedQrNonceRepository;
        this.nonceClaimer = nonceClaimer;
        this.properties = properties;
    }

    @Transactional
    public AccessCheckResponse verify(QrVerifyRequest request) {

        // Mesma separação de relógios do canal NFC: o real manda na validade
        // do código; o simulado só existe para exercitar as regras da reserva.
        final Instant realNow = Instant.now();
        final Instant now = resolveInstant(request.at());
        final String readerCode = request.readerCode();

        // ---- O conteúdo lido é mesmo um código nosso? --------------------
        QrPayload payload = QrPayload.parse(request.qrContent());

        if (payload == null) {
            // A câmera lê muita coisa que não é nosso QR. Não é erro do
            // sistema; é só um código que não interessa.
            return deny(null, readerCode, null,
                    AccessDenyReason.CHALLENGE_NOT_FOUND, now);
        }

        // ---- O código ainda é recente? -----------------------------------
        long ageSeconds = Math.abs(realNow.getEpochSecond() - payload.issuedAtEpochSeconds());

        if (ageSeconds > properties.getQrTtlSeconds()) {
            // Vale tanto para código velho quanto para código com data no
            // futuro: os dois indicam que algo não bate.
            return deny(null, readerCode, payload.credentialId(),
                    AccessDenyReason.CHALLENGE_EXPIRED, now);
        }

        // ---- Este código já foi apresentado antes? -----------------------
        // Reservamos ANTES de conferir a assinatura, pelo mesmo motivo do
        // canal NFC: não dar a ninguém um código reutilizável para ficar
        // testando assinaturas até acertar.
        boolean claimed;
        try {
            nonceClaimer.insert(
                    payload.nonce(), payload.credentialId(), readerCode,
                    Instant.ofEpochSecond(payload.issuedAtEpochSeconds()), realNow);
            claimed = true;
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            // Este número já tinha sido usado. É exatamente o ataque de
            // repetição sendo barrado pela chave primária do banco.
            //
            // Capturamos os DOIS tipos de propósito: o Spring traduz a falha
            // para a sua própria exceção apenas em alguns caminhos; em outros
            // sobe a exceção crua do Hibernate. Esperar só por uma delas
            // deixaria o erro escapar como falha do servidor — e, pior, o
            // acesso ficaria sem resposta clara na porta.
            claimed = false;
        }

        if (!claimed) {
            return deny(null, readerCode, payload.credentialId(),
                    AccessDenyReason.CHALLENGE_ALREADY_USED, now);
        }

        // ---- A credencial existe? ----------------------------------------
        Credential credential = credentialRepository
                .findByIdWithGrants(payload.credentialId())
                .orElse(null);

        if (credential == null) {
            return deny(null, readerCode, payload.credentialId(),
                    AccessDenyReason.CREDENTIAL_NOT_FOUND, now);
        }

        String publicKey = credential.getDevice().getPublicKey();

        if (publicKey == null || publicKey.isBlank()) {
            return deny(null, readerCode, credential.getId(),
                    AccessDenyReason.DEVICE_KEY_MISSING, now);
        }

        // ---- A assinatura confere? ---------------------------------------
        boolean signatureOk = SignatureVerifier.verify(
                publicKey,
                SignatureAlgorithm.parse(credential.getDevice().getPublicKeyAlg()),
                payload.signedMessageBytes(),
                payload.signatureBase64());

        if (!signatureOk) {
            log.warn("QR com assinatura inválida | leitor={} credencial={}",
                    readerCode, payload.credentialId());
            return deny(null, readerCode, credential.getId(),
                    AccessDenyReason.INVALID_SIGNATURE, now);
        }

        // ================================================================
        // Identidade provada. Agora, e só agora, as regras de permissão.
        // ================================================================

        Reader reader = readerRepository.findByCode(readerCode).orElse(null);

        AuthorizationResult result = AuthorizationEngine.decide(new AuthorizationRequest(
                now,
                AccessService.toSnapshot(reader),
                AccessService.toSnapshot(credential),
                AccessService.toGrantSnapshots(credential)));

        AccessEvent event = accessEventRepository.save(new AccessEvent(
                reader != null ? reader.getId() : null, readerCode,
                credential.getId(), result.accessPointCode(),
                result.decision(), result.reason(), result.message(), now));

        log.info("QR {} | leitor={} porta={} credencial={}",
                result.decision(), readerCode, result.accessPointCode(), credential.getId());

        return new AccessCheckResponse(
                result.decision(), result.reason(), result.message(),
                readerCode, result.accessPointCode(), credential.getId(),
                now, event.getId());
    }

    // ------------------------------------------------------------------

    private AccessCheckResponse deny(UUID readerId, String readerCode, UUID credentialId,
                                     AccessDenyReason reason, Instant now) {
        AccessEvent event = accessEventRepository.save(new AccessEvent(
                readerId, readerCode, credentialId, null,
                AccessDecision.DENIED, reason, reason.getMessage(), now));

        return new AccessCheckResponse(
                AccessDecision.DENIED, reason, reason.getMessage(),
                readerCode, null, credentialId, now, event.getId());
    }

    private Instant resolveInstant(Instant requested) {
        if (requested == null) {
            return Instant.now();
        }
        if (!properties.isAllowTimeTravel()) {
            throw new BadRequestException(
                    "Simulação de data/hora está desligada neste servidor.");
        }
        return requested;
    }

    /** Remove códigos antigos. Eles já não servem nem para uso nem para ataque. */
    @Transactional
    public int cleanupOldNonces() {
        return usedQrNonceRepository.deleteUsedBefore(Instant.now().minusSeconds(3600));
    }
}
