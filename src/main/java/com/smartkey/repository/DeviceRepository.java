package com.smartkey.repository;

import com.smartkey.domain.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByGuestId(UUID guestId);

    /** Traz o dispositivo junto com o hospede dono dele. */
    @Query("SELECT d FROM Device d JOIN FETCH d.guest WHERE d.id = :id")
    Optional<Device> findByIdWithGuest(@Param("id") UUID id);

    @Query("SELECT d FROM Device d JOIN FETCH d.guest")
    List<Device> findAllWithGuest();
}
