-- ===========================================================================
-- Indice na chave dos leitores
--
-- Toda tentativa de abrir uma porta procura o leitor pelo hash da chave dele.
-- Sem indice, o banco le a tabela inteira a cada tentativa.
--
-- UNIQUE porque dois leitores com a mesma chave seria um erro grave: um
-- responderia pelo outro. Na pratica e impossivel (32 bytes sorteados), mas
-- a regra fica no banco para nao depender disso.
-- ===========================================================================

CREATE UNIQUE INDEX uk_readers_api_key_hash
    ON readers (api_key_hash)
    WHERE api_key_hash IS NOT NULL;
