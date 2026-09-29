-- ===========================================================================
-- FASE 4/6 - Acesso por QR Code
--
-- O canal QR inverte o sentido da conversa. No NFC o leitor fala primeiro
-- (manda o desafio) e o celular responde. Com QR, o celular mostra e o leitor
-- lê: o leitor não tem como mandar nada antes.
--
-- Consequência: o número que não pode se repetir passa a ser sorteado pelo
-- CELULAR, e não pelo leitor. A garantia de que ele nunca se repete deixa de
-- ser "eu sorteei" e passa a ser esta tabela: a chave primária recusa a
-- segunda inserção do mesmo valor.
--
-- É um pouco mais fraco que o desafio do NFC (o leitor não contribui com
-- aleatoriedade), e por isso o código carrega um horário de emissão e vale
-- por poucos segundos. É o mesmo desenho usado em cartões de embarque.
-- ===========================================================================

CREATE TABLE used_qr_nonces (
    -- O próprio número sorteado é a chave primária. É ele que faz o trabalho:
    -- tentar gravar duas vezes o mesmo valor simplesmente não passa.
    nonce VARCHAR(64) PRIMARY KEY,

    credential_id UUID,
    reader_code   VARCHAR(100),

    -- Horário que o celular declarou ao assinar.
    issued_at TIMESTAMPTZ NOT NULL,
    used_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_used_qr_nonces_used ON used_qr_nonces (used_at);
