package com.smartkey.service;

import com.smartkey.domain.crypto.SignatureAlgorithm;
import com.smartkey.domain.crypto.SignatureVerifier;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.model.Device;
import com.smartkey.repository.CredentialRepository;
import com.smartkey.repository.DeviceRepository;
import com.smartkey.web.dto.EnrollKeyRequest;
import com.smartkey.web.dto.EnrolledKeyResponse;
import com.smartkey.domain.enums.CredentialStatus;
import com.smartkey.domain.enums.ReservationStatus;
import com.smartkey.web.error.BadRequestException;
import com.smartkey.web.error.ConflictException;
import com.smartkey.web.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Ativação da chave no celular do hóspede.
 *
 * O FLUXO, do ponto de vista do hóspede:
 *
 *   1. Recebe um link com um token de uso único.
 *   2. Abre o link. A página cria ali mesmo um par de chaves — a privada
 *      fica trancada no aparelho.
 *   3. A página envia a chave PÚBLICA junto com o token.
 *   4. O token queima. O link não serve mais para mais ninguém.
 *
 * Por que o token queima: sem isso, quem interceptasse o link poderia
 * registrar a própria chave e passar a abrir aquela porta. Com uso único, o
 * segundo a tentar é recusado — e o hóspede legítimo percebe que algo houve,
 * porque a ativação dele falha.
 */
@Service
public class KeyEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(KeyEnrollmentService.class);

    private static final int TOKEN_BYTES = 24;

    private final CredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public KeyEnrollmentService(CredentialRepository credentialRepository,
                                DeviceRepository deviceRepository) {
        this.credentialRepository = credentialRepository;
        this.deviceRepository = deviceRepository;
    }

    /** Sorteia um token novo. Chamado quando a chave é emitida. */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public EnrolledKeyResponse enroll(EnrollKeyRequest request) {

        Credential credential = credentialRepository
                .findByEnrollmentToken(request.token().trim())
                .orElseThrow(() -> new NotFoundException(
                        "Link de ativação inválido ou já utilizado."));

        // Um link que ainda não foi usado não pode ressuscitar uma chave
        // que a administração já desligou. Sem esta checagem, o hóspede de
        // uma reserva cancelada ainda conseguiria "ativar" algo.
        if (credential.getStatus() == CredentialStatus.REVOKED
                || credential.getReservation().getStatus() == ReservationStatus.CANCELLED
                || !Instant.now().isBefore(credential.getValidUntil())) {
            throw new ConflictException(
                    "Esta chave não está mais válida. Peça um novo link à administração.");
        }

        SignatureAlgorithm algorithm;
        try {
            algorithm = SignatureAlgorithm.parse(request.algorithm());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        String publicKey = request.publicKey().trim();

        if (!SignatureVerifier.isValidPublicKey(publicKey, algorithm)) {
            throw new BadRequestException(
                    "A chave pública enviada não é válida para " + algorithm.name() + ".");
        }

        Device device = credential.getDevice();
        device.setPublicKey(publicKey);
        device.setPublicKeyAlg(algorithm.name());

        if (request.deviceLabel() != null && !request.deviceLabel().isBlank()) {
            device.setLabel(request.deviceLabel().trim());
        }

        deviceRepository.save(device);

        // Queima o token: este link não ativa mais nada.
        credential.markEnrollmentUsed();
        credentialRepository.save(credential);

        log.info("Chave ativada | credencial={} aparelho={}",
                credential.getId(), device.getId());

        return EnrolledKeyResponse.from(credential);
    }
}
