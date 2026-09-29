package com.smartkey.service;

import com.smartkey.config.SmartKeyProperties;
import com.smartkey.domain.access.AuthorizationEngine;
import com.smartkey.domain.access.AuthorizationRequest;
import com.smartkey.domain.access.AuthorizationResult;
import com.smartkey.domain.crypto.SignatureAlgorithm;
import com.smartkey.domain.crypto.SignatureVerifier;
import com.smartkey.domain.crypto.SignedMessage;
import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;
import com.smartkey.domain.model.AccessEvent;
import com.smartkey.domain.model.Challenge;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.model.Reader;
import com.smartkey.repository.AccessEventRepository;
import com.smartkey.repository.ChallengeRepository;
import com.smartkey.repository.CredentialRepository;
import com.smartkey.repository.ReaderRepository;
import com.smartkey.web.dto.AccessCheckResponse;
import com.smartkey.web.dto.ChallengeResponse;
import com.smartkey.web.dto.VerifyAccessRequest;
import com.smartkey.web.error.BadRequestException;
import com.smartkey.web.ReaderScopeGuard;
import com.smartkey.web.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * FASE 3 - o fluxo seguro de abertura de porta.
 *
 * COMO FUNCIONA, EM QUATRO PASSOS:
 *
 *   1. O leitor pede um DESAFIO: um número sorteado que nunca foi usado.
 *   2. O leitor entrega esse número ao celular (por NFC).
 *   3. O celular ASSINA o número com a chave privada que vive dentro dele,
 *      e devolve a assinatura.
 *   4. O servidor CONFERE a assinatura com a chave pública cadastrada.
 *      Só então pergunta ao motor de decisão se pode abrir.
 *
 * POR QUE ISSO É MELHOR QUE UM CÓDIGO FIXO:
 *
 * Um cartão com código fixo entrega o mesmo segredo toda vez. Quem conseguir
 * ler esse segredo uma única vez, clona o cartão para sempre - é assim que
 * caem as fechaduras baratas.
 *
 * Aqui, o que trafega é uma assinatura diferente a cada tentativa, válida
 * apenas para aquele desafio, aquela porta e aquela credencial. Interceptar
 * não adianta: o desafio já foi queimado e nunca mais será aceito.
 */
@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    /** 32 bytes = 256 bits de aleatoriedade. Adivinhar é inviável. */
    private static final int NONCE_BYTES = 32;

    private final ChallengeRepository challengeRepository;
    private final ReaderRepository readerRepository;
    private final CredentialRepository credentialRepository;
    private final AccessEventRepository accessEventRepository;
    private final SmartKeyProperties properties;
    private final ReaderScopeGuard readerScopeGuard;

    /**
     * SecureRandom, e não Random.
     *
     * O Random comum é previsível: quem observar alguns valores consegue
     * calcular os próximos. Num desafio criptográfico isso seria fatal - o
     * atacante prepararia a assinatura antes mesmo de encostar o celular.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    public ChallengeService(ChallengeRepository challengeRepository,
                            ReaderRepository readerRepository,
                            CredentialRepository credentialRepository,
                            AccessEventRepository accessEventRepository,
                            SmartKeyProperties properties,
                            ReaderScopeGuard readerScopeGuard) {
        this.readerScopeGuard = readerScopeGuard;
        this.challengeRepository = challengeRepository;
        this.readerRepository = readerRepository;
        this.credentialRepository = credentialRepository;
        this.accessEventRepository = accessEventRepository;
        this.properties = properties;
    }

    // ==================================================================
    // PASSO 1 - criar o desafio
    // ==================================================================

    @Transactional
    public ChallengeResponse createChallenge(String readerCode) {
        Reader reader = readerRepository.findByCode(readerCode)
                .orElseThrow(() -> new NotFoundException(
                        "Leitor não cadastrado: " + readerCode));

        byte[] randomBytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(randomBytes);
        String nonce = Base64.getEncoder().encodeToString(randomBytes);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getChallengeTtlSeconds());

        Challenge challenge = challengeRepository.save(
                new Challenge(nonce, reader.getId(), reader.getCode(), now, expiresAt));

        return new ChallengeResponse(
                challenge.getId(),
                nonce,
                expiresAt,
                SignatureAlgorithm.ECDSA_P256.name(),
                SignedMessage.template(challenge.getId(), nonce, reader.getCode()));
    }

    // ==================================================================
    // PASSO 4 - conferir a assinatura e decidir
    // ==================================================================

    /**
     * Repare que este método NÃO lança exceção nos casos de recusa: ele sempre
     * devolve uma resposta.
     *
     * Isso é proposital. Lançar exceção desfaria a transação - e junto com ela
     * desfaria a marcação do desafio como usado, devolvendo ao atacante um
     * desafio novo em folha a cada tentativa fracassada.
     */
    @Transactional
    public AccessCheckResponse verifyAndCheck(VerifyAccessRequest request) {

        // ATENÇÃO: aqui existem DOIS relógios diferentes, de propósito.
        //
        //   realNow    o relógio de verdade. Manda na validade do desafio.
        //
        //   now        o momento a ser avaliado pelas regras da reserva.
        //              Em desenvolvimento pode ser simulado pelo campo "at",
        //              para testar "antes do check-in" sem esperar dias.
        //
        // Por que separar: o desafio nasceu agora, no mundo real, e vive
        // poucos segundos. Se a validade dele obedecesse ao relógio simulado,
        // bastaria informar uma data qualquer para ressuscitar um desafio
        // vencido - e a proteção contra repetição iria por água abaixo.
        //
        // O relógio simulado nunca pode afrouxar a criptografia; ele só existe
        // para exercitar as regras de horário da hospedagem.
        final Instant realNow = Instant.now();
        final Instant now = resolveInstant(request.at());

        // ---- O desafio existe? -------------------------------------------
        Challenge challenge = challengeRepository.findById(request.challengeId()).orElse(null);

        if (challenge == null) {
            return denyWithoutChallenge(request, "(desconhecido)",
                    AccessDenyReason.CHALLENGE_NOT_FOUND, now);
        }

        // Copiamos o que precisamos ANTES do UPDATE abaixo, porque ele limpa
        // o contexto de persistência e a entidade deixa de estar gerenciada.
        final String nonce = challenge.getNonce();
        final String readerCode = challenge.getReaderCode();
        final UUID readerId = challenge.getReaderId();
        final boolean expired = challenge.isExpired(realNow);

        // Só agora sabemos de qual porta era este desafio - e conferimos que
        // quem o está apresentando é de fato aquele leitor. Sem isto, um leitor
        // comprometido poderia consumir desafios emitidos para outras portas.
        readerScopeGuard.ensureCanActAs(readerCode);

        // ---- O desafio ainda vale? ---------------------------------------
        if (expired) {
            return denyWithoutChallenge(request, readerCode,
                    AccessDenyReason.CHALLENGE_EXPIRED, now);
        }

        // ---- QUEIMA o desafio, de forma atômica --------------------------
        // Feito ANTES de conferir a assinatura, de propósito: assim ninguém
        // consegue usar o mesmo desafio para testar assinatura após
        // assinatura até acertar.
        int consumed = challengeRepository.consume(
                challenge.getId(), request.credentialId(), realNow);

        if (consumed == 0) {
            // Alguém chegou primeiro: este desafio já tinha sido usado.
            // É exatamente o ataque de repetição sendo barrado.
            return denyWithoutChallenge(request, readerCode,
                    AccessDenyReason.CHALLENGE_ALREADY_USED, now);
        }

        // ---- A credencial existe? ----------------------------------------
        Credential credential = credentialRepository
                .findByIdWithGrants(request.credentialId())
                .orElse(null);

        if (credential == null) {
            return record(request, readerId, readerCode, null,
                    AccessDenyReason.CREDENTIAL_NOT_FOUND, now);
        }

        // ---- O dispositivo tem chave pública cadastrada? -----------------
        String publicKey = credential.getDevice().getPublicKey();
        String algorithmName = credential.getDevice().getPublicKeyAlg();

        if (publicKey == null || publicKey.isBlank()) {
            return record(request, readerId, readerCode, credential,
                    AccessDenyReason.DEVICE_KEY_MISSING, now);
        }

        // ---- A ASSINATURA CONFERE? ---------------------------------------
        SignatureAlgorithm algorithm = SignatureAlgorithm.parse(algorithmName);

        byte[] message = SignedMessage.bytes(
                request.challengeId(), nonce, readerCode, request.credentialId());

        boolean signatureOk = SignatureVerifier.verify(
                publicKey, algorithm, message, request.signature());

        if (!signatureOk) {
            log.warn("Assinatura inválida | leitor={} credencial={}",
                    readerCode, request.credentialId());
            return record(request, readerId, readerCode, credential,
                    AccessDenyReason.INVALID_SIGNATURE, now);
        }

        // ================================================================
        // A identidade está PROVADA. Só agora perguntamos sobre permissões.
        // ================================================================

        Reader reader = readerRepository.findByCode(readerCode).orElse(null);

        AuthorizationResult result = AuthorizationEngine.decide(new AuthorizationRequest(
                now,
                AccessService.toSnapshot(reader),
                AccessService.toSnapshot(credential),
                AccessService.toGrantSnapshots(credential)));

        AccessEvent event = accessEventRepository.save(new AccessEvent(
                readerId, readerCode, credential.getId(), result.accessPointCode(),
                result.decision(), result.reason(), result.message(), now));

        log.info("Acesso {} (assinatura conferida) | leitor={} porta={} credencial={}",
                result.decision(), readerCode, result.accessPointCode(), credential.getId());

        return new AccessCheckResponse(
                result.decision(), result.reason(), result.message(),
                readerCode, result.accessPointCode(), credential.getId(),
                now, event.getId());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private AccessCheckResponse denyWithoutChallenge(VerifyAccessRequest request,
                                                     String readerCode,
                                                     AccessDenyReason reason,
                                                     Instant now) {
        return record(request, null, readerCode, null, reason, now);
    }

    /** Registra a recusa no histórico e devolve a resposta correspondente. */
    private AccessCheckResponse record(VerifyAccessRequest request,
                                       UUID readerId,
                                       String readerCode,
                                       Credential credential,
                                       AccessDenyReason reason,
                                       Instant now) {

        UUID credentialId = (credential != null) ? credential.getId() : request.credentialId();

        AccessEvent event = accessEventRepository.save(new AccessEvent(
                readerId, readerCode, (credential != null) ? credential.getId() : null,
                null, AccessDecision.DENIED, reason, reason.getMessage(), now));

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

    // ------------------------------------------------------------------
    // Limpeza
    // ------------------------------------------------------------------

    /**
     * Apaga desafios vencidos.
     *
     * Eles já não servem para nada - nem para uso legítimo, nem para ataque,
     * porque a validade é conferida pela data e não pela existência da linha.
     * A limpeza existe só para a tabela não crescer sem parar.
     */
    @Transactional
    public int cleanupExpired() {
        int removed = challengeRepository.deleteExpiredBefore(
                Instant.now().minusSeconds(3600));
        if (removed > 0) {
            log.debug("Desafios vencidos removidos: {}", removed);
        }
        return removed;
    }
}
