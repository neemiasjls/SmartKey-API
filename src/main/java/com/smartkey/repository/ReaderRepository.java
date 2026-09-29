package com.smartkey.repository;

import com.smartkey.domain.model.Reader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReaderRepository extends JpaRepository<Reader, UUID> {

    /** Busca o leitor pelo codigo, ex: "reader_apto_804". */
    Optional<Reader> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Busca o leitor pelo hash da sua chave de API.
     *
     * Consultamos pelo hash, e nao pela chave: o banco nunca ve a chave em
     * texto puro. Se o banco vazar, as chaves dos leitores nao vazam junto.
     */
    Optional<Reader> findByApiKeyHash(String apiKeyHash);
}
