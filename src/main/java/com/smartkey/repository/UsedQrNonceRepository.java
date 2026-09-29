package com.smartkey.repository;

import com.smartkey.domain.model.UsedQrNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * A reserva do numero (o "claim") NAO fica aqui: ela precisa de transacao
 * propria e mora em {@link com.smartkey.service.QrNonceClaimer}.
 */
public interface UsedQrNonceRepository extends JpaRepository<UsedQrNonce, String> {

    /** Limpeza: códigos antigos não servem para nada, nem para ataque. */
    @Modifying
    @Query("DELETE FROM UsedQrNonce n WHERE n.usedAt < :before")
    int deleteUsedBefore(@Param("before") Instant before);
}
