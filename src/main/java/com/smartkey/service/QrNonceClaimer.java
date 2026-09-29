package com.smartkey.service;

import com.smartkey.domain.model.UsedQrNonce;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva o número sorteado de um QR Code, garantindo que ele só seja aceito
 * uma única vez.
 *
 * ================================================================
 * DUAS ARMADILHAS QUE ESTA CLASSE EVITA
 * ================================================================
 *
 * 1) TRANSAÇÃO PRÓPRIA (REQUIRES_NEW)
 *
 * A unicidade vem da chave primária: gravar o mesmo número duas vezes falha.
 * Só que aproveitar essa falha exige cuidado — no PostgreSQL, QUALQUER erro
 * derruba a transação inteira, e depois dele nenhum outro comando funciona,
 * mesmo que o erro tenha sido capturado no código.
 *
 * Por isso a tentativa acontece numa transação separada. Se falhar, morre
 * sozinha, e a transação principal (que ainda vai conferir assinatura,
 * permissões e gravar o histórico) segue intacta.
 *
 * Note que não há try/catch aqui dentro: a exceção precisa escapar para o
 * Spring desfazer esta transação. Quem captura é o chamador, do lado de fora.
 *
 * 2) persist(), E NÃO save()
 *
 * Esta é sutil e custou um teste vermelho para aparecer.
 *
 * O save() do Spring Data, numa entidade cujo id JÁ FOI PREENCHIDO por nós,
 * primeiro vai ao banco perguntar se aquela linha existe. Se existir, ele faz
 * um UPDATE — e um UPDATE não viola chave primária nenhuma.
 *
 * O resultado seria desastroso e silencioso: apresentar o mesmo QR duas vezes
 * simplesmente sobrescreveria o registro anterior e LIBERARIA a porta de novo.
 * A proteção contra repetição existiria apenas no papel.
 *
 * O persist() não faz essa consulta: ele sempre tenta INSERT. Se a linha já
 * existir, o banco recusa — que é exatamente o comportamento desejado.
 */
@Component
public class QrNonceClaimer {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Grava o número. Lança exceção de integridade se ele já tiver sido usado.
     *
     * O flush força a ida ao banco agora, e não no fim da transação:
     * precisamos saber do conflito imediatamente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(String nonce, UUID credentialId, String readerCode,
                       Instant issuedAt, Instant usedAt) {
        entityManager.persist(
                new UsedQrNonce(nonce, credentialId, readerCode, issuedAt, usedAt));
        entityManager.flush();
    }
}
