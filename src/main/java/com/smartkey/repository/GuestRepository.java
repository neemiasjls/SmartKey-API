package com.smartkey.repository;

import com.smartkey.domain.model.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * O Spring gera a implementacao desta interface sozinho, em tempo de execucao.
 * Voce escreve apenas a assinatura dos metodos.
 */
public interface GuestRepository extends JpaRepository<Guest, UUID> {
}
