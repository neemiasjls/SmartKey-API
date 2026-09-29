package com.smartkey.repository;

import com.smartkey.domain.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Todas as reservas de um hospede. */
    List<Reservation> findByGuestId(UUID guestId);

    /**
     * Traz a reserva junto com o hospede.
     *
     * Necessario porque a resposta da API mostra o nome do hospede, e o
     * relacionamento e LAZY: sem o FETCH, o nome so seria buscado depois que
     * a conexao com o banco ja fechou, causando erro 500.
     */
    @Query("SELECT r FROM Reservation r JOIN FETCH r.guest WHERE r.id = :id")
    Optional<Reservation> findByIdWithGuest(@Param("id") UUID id);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.guest")
    List<Reservation> findAllWithGuest();
}
