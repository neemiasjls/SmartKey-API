package com.smartkey.web.controller;

import com.smartkey.service.ChallengeService;
import com.smartkey.web.ReaderScopeGuard;
import com.smartkey.service.QrAccessService;
import com.smartkey.web.dto.AccessCheckResponse;
import com.smartkey.web.dto.ChallengeRequest;
import com.smartkey.web.dto.ChallengeResponse;
import com.smartkey.web.dto.QrVerifyRequest;
import com.smartkey.web.dto.VerifyAccessRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O FLUXO SEGURO (FASE 3).
 *
 * São dois passos, sempre nesta ordem:
 *
 *   1. POST /api/access/challenge   o leitor pede um desafio
 *   2. POST /api/access/verify      o leitor devolve a resposta assinada
 *
 * Entre os dois, quem trabalha é o celular: ele assina o desafio com a chave
 * privada guardada no seu chip seguro.
 */
@RestController
@RequestMapping("/api/access")
@Tag(name = "7. Acesso seguro (com assinatura)")
public class SecureAccessController {

    private final ChallengeService challengeService;
    private final QrAccessService qrAccessService;
    private final ReaderScopeGuard readerScopeGuard;

    public SecureAccessController(ChallengeService challengeService,
                                  QrAccessService qrAccessService,
                                  ReaderScopeGuard readerScopeGuard) {
        this.challengeService = challengeService;
        this.qrAccessService = qrAccessService;
        this.readerScopeGuard = readerScopeGuard;
    }

    @PostMapping("/challenge")
    @Operation(summary = "Passo 1: pedir um desafio",
            description = """
                    O leitor chama este endpoint a cada tentativa de abertura.

                    Devolve um número sorteado (nonce) que nunca foi usado antes
                    e vale por poucos segundos. O leitor entrega esse número ao
                    celular, que o assina.

                    O campo `messageTemplate` mostra exatamente qual texto deve
                    ser assinado — basta substituir `{credentialId}`.""")
    public ChallengeResponse challenge(@Valid @RequestBody ChallengeRequest request) {
        readerScopeGuard.ensureCanActAs(request.readerCode());
        return challengeService.createChallenge(request.readerCode());
    }

    @PostMapping("/verify")
    @Operation(summary = "Passo 2: enviar a resposta assinada",
            description = """
                    O servidor confere a assinatura com a chave pública do
                    dispositivo. Só depois de comprovar QUEM é o aparelho é que
                    ele pergunta SE aquela credencial pode abrir aquela porta.

                    Motivos de recusa possíveis nesta etapa:

                    - `CHALLENGE_NOT_FOUND` — desafio inexistente
                    - `CHALLENGE_EXPIRED` — desafio vencido
                    - `CHALLENGE_ALREADY_USED` — tentativa de repetição (replay)
                    - `DEVICE_KEY_MISSING` — aparelho sem chave pública cadastrada
                    - `INVALID_SIGNATURE` — assinatura não confere

                    Depois disso, valem as regras normais de horário e porta.""")
    public AccessCheckResponse verify(@Valid @RequestBody VerifyAccessRequest request) {
        // A porta so e conhecida depois de ler o desafio; por isso a checagem
        // de escopo do leitor acontece dentro do servico.
        return challengeService.verifyAndCheck(request);
    }

    @PostMapping("/qr-verify")
    @Operation(summary = "Acesso por QR Code (leitor com câmera)",
            description = """
                    Caminho alternativo ao NFC, usado quando o leitor lê um QR
                    exibido na tela do hóspede.

                    A diferença é de quem sorteia o número que não pode se
                    repetir: no NFC é o leitor; aqui é o celular, porque com QR
                    o leitor não tem como falar antes. Em compensação o código
                    vale por poucos segundos e cada um só é aceito uma vez.

                    A assinatura continua sendo feita pela chave privada do
                    aparelho, exatamente como no NFC.""")
    public AccessCheckResponse verifyQr(@Valid @RequestBody QrVerifyRequest request) {
        readerScopeGuard.ensureCanActAs(request.readerCode());
        return qrAccessService.verify(request);
    }
}
