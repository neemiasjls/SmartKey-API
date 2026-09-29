-- ===========================================================================
-- FASE 3 - Desafios criptográficos (challenge / nonce)
--
-- Como funciona, em uma frase: o leitor sorteia um número que nunca foi usado
-- antes, o celular assina esse número com sua chave privada, e o servidor
-- confere a assinatura com a chave pública. Quem não tem a chave privada não
-- consegue produzir a assinatura.
--
-- E por que o número precisa ser sempre novo: se fosse sempre o mesmo, alguém
-- poderia gravar a resposta de um acesso legítimo e repeti-la depois. É o
-- chamado ataque de repetição (replay).
-- ===========================================================================

CREATE TABLE challenges (
    id UUID PRIMARY KEY,

    -- O número sorteado, em Base64. São 32 bytes vindos de um gerador
    -- criptográfico: o espaço de possibilidades é grande demais para
    -- alguém adivinhar ou repetir por acaso.
    nonce VARCHAR(100) NOT NULL,

    -- Para qual leitor este desafio foi emitido. Guardamos o código como
    -- texto porque ele entra na mensagem assinada: assim uma assinatura
    -- feita para a portaria não serve para a porta do apartamento.
    reader_id   UUID,
    reader_code VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    -- Validade curta (segundos). Quanto menor a janela, menor o tempo que um
    -- atacante teria para tentar alguma coisa com um desafio interceptado.
    expires_at TIMESTAMPTZ NOT NULL,

    -- A trava contra repetição: fica nulo até o desafio ser usado.
    -- Depois de preenchido, nunca mais volta a ser nulo.
    used_at              TIMESTAMPTZ,
    used_by_credential_id UUID,

    CONSTRAINT ck_challenge_period CHECK (expires_at > created_at)
);

-- Um mesmo nonce jamais pode existir duas vezes. Esta regra vive no banco,
-- e não apenas no código: nem uma condição de corrida entre dois pedidos
-- simultâneos consegue furá-la.
CREATE UNIQUE INDEX uk_challenges_nonce ON challenges (nonce);

CREATE INDEX ix_challenges_expires ON challenges (expires_at);
CREATE INDEX ix_challenges_reader  ON challenges (reader_code);

-- ---------------------------------------------------------------------------
-- Coerência das chaves públicas dos dispositivos
--
-- Ou o dispositivo tem chave pública E algoritmo, ou não tem nenhum dos dois.
-- Metade preenchida seria um estado sem sentido, e impossível de verificar.
-- ---------------------------------------------------------------------------
ALTER TABLE devices
    ADD CONSTRAINT ck_device_public_key_pair
    CHECK (
        (public_key IS NULL     AND public_key_alg IS NULL)
        OR
        (public_key IS NOT NULL AND public_key_alg IS NOT NULL)
    );
