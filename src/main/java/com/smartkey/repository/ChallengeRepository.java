package com.smartkey.repository;

import com.smartkey.domain.model.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    /**
     * QUEIMA o desafio: marca como usado, mas SOMENTE se ele ainda estiver
     * disponível.
     *
     * Devolve 1 se conseguiu queimar, e 0 se o desafio já tinha sido usado
     * (ou não existe).
     *
     * POR QUE ISTO PRECISA SER ASSIM:
     *
     * O caminho ingênuo seria "ler o desafio, ver se usedAt está nulo, e
     * então gravar". Mas se duas requisições chegarem no mesmo instante, as
     * duas leem "ainda não usado" e as duas passam. É a chamada condição de
     * corrida - e aqui ela significaria conseguir usar o mesmo desafio duas
     * vezes, exatamente o que queremos impedir.
     *
     * Este UPDATE resolve porque a condição "used_at IS NULL" é avaliada
     * pelo próprio banco, dentro da mesma operação que faz a escrita. O banco
     * garante que apenas uma das duas requisições consegue alterar a linha; a
     * outra recebe 0 e é recusada.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Challenge c
               SET c.usedAt = :now,
                   c.usedByCredentialId = :credentialId
             WHERE c.id = :id
               AND c.usedAt IS NULL
            """)
    int consume(@Param("id") UUID id,
                @Param("credentialId") UUID credentialId,
                @Param("now") Instant now);

    /** Remove desafios antigos, para a tabela nao crescer sem limite. */
    @Modifying
    @Query("DELETE FROM Challenge c WHERE c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
