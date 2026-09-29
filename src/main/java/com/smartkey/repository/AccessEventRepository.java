package com.smartkey.repository;

import com.smartkey.domain.model.AccessEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessEventRepository extends JpaRepository<AccessEvent, UUID> {

    /** Historico geral, do mais recente para o mais antigo. */
    Page<AccessEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Historico de uma credencial especifica. */
    Page<AccessEvent> findByCredentialIdOrderByOccurredAtDesc(UUID credentialId, Pageable pageable);

    /** Historico de um leitor especifico. */
    Page<AccessEvent> findByReaderCodeOrderByOccurredAtDesc(String readerCode, Pageable pageable);
}
