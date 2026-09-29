package com.smartkey.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefas de manutenção que rodam sozinhas, em segundo plano.
 *
 * Desafios vencidos e códigos QR já usados não servem para mais nada — nem
 * para uso legítimo, nem para ataque, porque a validade é conferida pela data.
 * Sem esta limpeza, as duas tabelas cresceriam para sempre: cada tentativa de
 * abrir uma porta deixa uma linha.
 */
@Component
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final ChallengeService challengeService;
    private final QrAccessService qrAccessService;

    public MaintenanceJobs(ChallengeService challengeService, QrAccessService qrAccessService) {
        this.challengeService = challengeService;
        this.qrAccessService = qrAccessService;
    }

    /** A cada 15 minutos, começando 1 minuto depois de a aplicação subir. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT15M")
    public void purgeExpiredCodes() {
        int challenges = challengeService.cleanupExpired();
        int qrCodes = qrAccessService.cleanupOldNonces();

        if (challenges + qrCodes > 0) {
            log.info("Limpeza: {} desafios e {} códigos QR antigos removidos",
                    challenges, qrCodes);
        }
    }
}
