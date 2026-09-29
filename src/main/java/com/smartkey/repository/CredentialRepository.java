package com.smartkey.repository;

import com.smartkey.domain.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    /**
     * Busca a credencial trazendo, na MESMA consulta, tudo o que vai ser
     * necessario depois: a reserva, o hospede, o dispositivo e as permissoes.
     *
     * Por que todos esses "JOIN FETCH" sao obrigatorios:
     *
     * Os relacionamentos sao LAZY, ou seja, o Java so vai ao banco buscar cada
     * um deles no momento em que alguem chama o getter. Mas a conexao com o
     * banco e fechada assim que o metodo do service termina - e a conversao
     * para JSON acontece DEPOIS disso, ja no controller.
     *
     * Resultado: sem estes FETCH, qualquer getter nao carregado estoura
     * LazyInitializationException e a API devolve erro 500.
     *
     * Trazer tudo de uma vez tambem e mais rapido: 1 ida ao banco em vez de 5.
     */
    @Query("""
            SELECT c FROM Credential c
            JOIN FETCH c.reservation r
            JOIN FETCH r.guest
            JOIN FETCH c.device d
            JOIN FETCH d.guest
            LEFT JOIN FETCH c.accessGrants
            WHERE c.id = :id
            """)
    Optional<Credential> findByIdWithGrants(@Param("id") UUID id);

    /** Mesma ideia da consulta acima, mas para a listagem completa. */
    @Query("""
            SELECT DISTINCT c FROM Credential c
            JOIN FETCH c.reservation r
            JOIN FETCH r.guest
            JOIN FETCH c.device d
            JOIN FETCH d.guest
            LEFT JOIN FETCH c.accessGrants
            """)
    List<Credential> findAllWithDetails();

    /**
     * Busca pela chave de ativacao. Traz tudo junto porque a resposta do
     * enroll precisa do nome do hospede e das permissoes.
     */
    @Query("""
            SELECT c FROM Credential c
            JOIN FETCH c.reservation r
            JOIN FETCH r.guest
            JOIN FETCH c.device d
            JOIN FETCH d.guest
            LEFT JOIN FETCH c.accessGrants
            WHERE c.enrollmentToken = :token
            """)
    Optional<Credential> findByEnrollmentToken(@Param("token") String token);

    List<Credential> findByReservationId(UUID reservationId);

    List<Credential> findByDeviceId(UUID deviceId);
}
