package com.smartkey.service;

import com.smartkey.config.SmartKeyProperties;
import com.smartkey.domain.access.AuthorizationEngine;
import com.smartkey.domain.access.AuthorizationRequest;
import com.smartkey.domain.access.AuthorizationResult;
import com.smartkey.domain.access.CredentialSnapshot;
import com.smartkey.domain.access.GrantSnapshot;
import com.smartkey.domain.access.ReaderSnapshot;
import com.smartkey.domain.model.AccessEvent;
import com.smartkey.domain.model.AccessGrant;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.model.Reader;
import com.smartkey.repository.AccessEventRepository;
import com.smartkey.repository.CredentialRepository;
import com.smartkey.repository.ReaderRepository;
import com.smartkey.web.dto.AccessCheckRequest;
import com.smartkey.web.dto.AccessCheckResponse;
import com.smartkey.web.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Faz a ponte entre o banco de dados e o motor de decisao.
 *
 * O trabalho aqui e sempre o mesmo, em tres passos:
 *
 *   1. BUSCAR no banco os dados do leitor e da credencial
 *   2. PERGUNTAR ao AuthorizationEngine se libera
 *   3. REGISTRAR o resultado no historico
 *
 * A decisao em si nao acontece aqui de proposito - ela mora no
 * AuthorizationEngine, que e uma classe pura e facil de testar.
 */
@Service
public class AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessService.class);

    private final ReaderRepository readerRepository;
    private final CredentialRepository credentialRepository;
    private final AccessEventRepository accessEventRepository;
    private final SmartKeyProperties properties;

    public AccessService(ReaderRepository readerRepository,
                         CredentialRepository credentialRepository,
                         AccessEventRepository accessEventRepository,
                         SmartKeyProperties properties) {
        this.readerRepository = readerRepository;
        this.credentialRepository = credentialRepository;
        this.accessEventRepository = accessEventRepository;
        this.properties = properties;
    }

    @Transactional
    public AccessCheckResponse check(AccessCheckRequest request) {

        // Este endpoint abre a porta apenas com o id da credencial, SEM exigir
        // assinatura. Serve para testar as regras de horario e permissao, mas
        // seria uma porta escancarada em producao: quem descobrisse um id
        // entraria. Por isso vem desligado por padrao.
        if (!properties.isAllowInsecureCheck()) {
            throw new BadRequestException(
                    "Este endpoint (sem assinatura) está desligado neste servidor. "
                    + "Use o fluxo seguro: POST /api/access/challenge e depois "
                    + "POST /api/access/verify.");
        }

        final Instant now = resolveInstant(request.at());

        // -------- Passo 1: buscar os dados --------------------------------

        Reader reader = readerRepository.findByCode(request.readerCode()).orElse(null);

        Credential credential = credentialRepository
                .findByIdWithGrants(request.credentialId())
                .orElse(null);

        // -------- Passo 2: perguntar ao motor -----------------------------

        AuthorizationResult result = AuthorizationEngine.decide(new AuthorizationRequest(
                now,
                toSnapshot(reader),
                toSnapshot(credential),
                toGrantSnapshots(credential)));

        // -------- Passo 3: registrar no historico -------------------------

        AccessEvent event = accessEventRepository.save(new AccessEvent(
                reader != null ? reader.getId() : null,
                request.readerCode(),
                credential != null ? credential.getId() : null,
                result.accessPointCode(),
                result.decision(),
                result.reason(),
                result.message(),
                now));

        log.info("Acesso {} | leitor={} porta={} credencial={} motivo={}",
                result.decision(),
                request.readerCode(),
                result.accessPointCode(),
                request.credentialId(),
                result.reason());

        return new AccessCheckResponse(
                result.decision(),
                result.reason(),
                result.message(),
                request.readerCode(),
                result.accessPointCode(),
                request.credentialId(),
                now,
                event.getId());
    }

    /**
     * Decide qual instante usar na avaliacao.
     *
     * Normalmente e "agora". Mas em desenvolvimento permitimos informar uma
     * data/hora qualquer, para conseguir testar "antes do check-in" e
     * "depois do checkout" sem esperar dias.
     *
     * Em producao isso fica desligado: aceitar um horario vindo do cliente
     * permitiria a qualquer pessoa fingir estar dentro da janela da reserva.
     */
    private Instant resolveInstant(Instant requested) {
        if (requested == null) {
            return Instant.now();
        }
        if (!properties.isAllowTimeTravel()) {
            throw new BadRequestException(
                    "Simulação de data/hora está desligada neste servidor. "
                    + "Remova o campo 'at' da requisição.");
        }
        return requested;
    }

    // ---------------------------------------------------------------------
    // Conversao: entidades do banco -> dados puros para o motor
    // ---------------------------------------------------------------------

    static ReaderSnapshot toSnapshot(Reader reader) {
        if (reader == null) {
            return null;
        }
        return new ReaderSnapshot(
                reader.getCode(),
                reader.getAccessPointCode(),
                reader.getStatus());
    }

    static CredentialSnapshot toSnapshot(Credential credential) {
        if (credential == null) {
            return null;
        }
        return new CredentialSnapshot(
                credential.getId().toString(),
                credential.getStatus(),
                credential.getValidFrom(),
                credential.getValidUntil(),
                credential.getReservation().getStatus());
    }

    static List<GrantSnapshot> toGrantSnapshots(Credential credential) {
        if (credential == null) {
            return List.of();
        }
        return credential.getAccessGrants().stream()
                .map(AccessService::toSnapshot)
                .toList();
    }

    private static GrantSnapshot toSnapshot(AccessGrant grant) {
        return new GrantSnapshot(
                grant.getAccessPointCode(),
                grant.getStatus(),
                grant.getValidFrom(),
                grant.getValidUntil());
    }

    // ---------------------------------------------------------------------

    /** Consulta o historico de uma credencial (usado pelo painel). */
    @Transactional(readOnly = true)
    public boolean credentialExists(UUID credentialId) {
        return credentialRepository.existsById(credentialId);
    }
}
