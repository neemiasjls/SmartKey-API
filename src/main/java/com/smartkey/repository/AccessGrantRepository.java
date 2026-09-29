package com.smartkey.repository;

import com.smartkey.domain.model.AccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    List<AccessGrant> findByCredentialId(UUID credentialId);

    Optional<AccessGrant> findByCredentialIdAndAccessPointCode(UUID credentialId,
                                                              String accessPointCode);
}
