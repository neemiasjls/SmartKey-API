-- ===========================================================================
-- SmartKey - estrutura inicial do banco de dados
--
-- Este arquivo e executado UMA UNICA VEZ pelo Flyway, automaticamente, na
-- primeira vez que a aplicacao sobe. O Flyway grava numa tabela de controle
-- quais arquivos ja rodaram, entao nao ha risco de aplicar duas vezes.
--
-- Para mudar o banco no futuro, NUNCA edite este arquivo: crie um novo
-- (V2__descricao.sql, V3__..., e assim por diante).
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- HOSPEDES
-- ---------------------------------------------------------------------------
CREATE TABLE guests (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    email      VARCHAR(200),
    phone      VARCHAR(50),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- ---------------------------------------------------------------------------
-- RESERVAS
-- ---------------------------------------------------------------------------
CREATE TABLE reservations (
    id           UUID         PRIMARY KEY,
    guest_id     UUID         NOT NULL REFERENCES guests (id) ON DELETE CASCADE,
    unit_label   VARCHAR(50)  NOT NULL,
    check_in_at  TIMESTAMPTZ  NOT NULL,
    check_out_at TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    notes        VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_reservation_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED')),

    -- O checkout tem obrigatoriamente que ser depois do check-in.
    -- Esta regra fica no banco, e nao so no codigo, para que nenhum caminho
    -- (nem um INSERT manual) consiga criar uma reserva impossivel.
    CONSTRAINT ck_reservation_period
        CHECK (check_out_at > check_in_at)
);

CREATE INDEX ix_reservations_guest  ON reservations (guest_id);
CREATE INDEX ix_reservations_period ON reservations (check_in_at, check_out_at);

-- ---------------------------------------------------------------------------
-- DISPOSITIVOS
--
-- public_key guarda SOMENTE a chave publica. A chave privada fica no celular
-- e nunca chega ao servidor.
-- ---------------------------------------------------------------------------
CREATE TABLE devices (
    id             UUID         PRIMARY KEY,
    guest_id       UUID         NOT NULL REFERENCES guests (id) ON DELETE CASCADE,
    platform       VARCHAR(30)  NOT NULL,
    label          VARCHAR(200),
    public_key     VARCHAR(500),
    public_key_alg VARCHAR(50),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_device_platform
        CHECK (platform IN ('ANDROID', 'IOS', 'WEB', 'SIMULATOR'))
);

CREATE INDEX ix_devices_guest ON devices (guest_id);

-- ---------------------------------------------------------------------------
-- CREDENCIAIS (a chave digital)
-- ---------------------------------------------------------------------------
CREATE TABLE credentials (
    id             UUID         PRIMARY KEY,
    reservation_id UUID         NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    device_id      UUID         NOT NULL REFERENCES devices (id)      ON DELETE CASCADE,
    status         VARCHAR(30)  NOT NULL,
    valid_from     TIMESTAMPTZ  NOT NULL,
    valid_until    TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(300),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_credential_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),

    CONSTRAINT ck_credential_period
        CHECK (valid_until > valid_from),

    -- Um mesmo celular so pode ter UMA credencial por reserva.
    CONSTRAINT uk_credential_reservation_device
        UNIQUE (reservation_id, device_id)
);

CREATE INDEX ix_credentials_status      ON credentials (status);
CREATE INDEX ix_credentials_reservation ON credentials (reservation_id);

-- ---------------------------------------------------------------------------
-- PERMISSOES
-- ---------------------------------------------------------------------------
CREATE TABLE access_grants (
    id                UUID         PRIMARY KEY,
    credential_id     UUID         NOT NULL REFERENCES credentials (id) ON DELETE CASCADE,
    access_point_code VARCHAR(100) NOT NULL,
    valid_from        TIMESTAMPTZ,
    valid_until       TIMESTAMPTZ,
    status            VARCHAR(30)  NOT NULL,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_access_grant_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),

    -- A mesma porta nao pode aparecer duas vezes na mesma credencial.
    CONSTRAINT uk_access_grant_credential_point
        UNIQUE (credential_id, access_point_code)
);

CREATE INDEX ix_access_grants_point ON access_grants (access_point_code);

-- ---------------------------------------------------------------------------
-- LEITORES (fechaduras)
-- ---------------------------------------------------------------------------
CREATE TABLE readers (
    id                UUID         PRIMARY KEY,
    code              VARCHAR(100) NOT NULL UNIQUE,
    name              VARCHAR(200) NOT NULL,
    access_point_code VARCHAR(100) NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    api_key_hash      VARCHAR(200),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_reader_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_readers_point ON readers (access_point_code);

-- ---------------------------------------------------------------------------
-- EVENTOS DE ACESSO (auditoria)
--
-- De proposito SEM chaves estrangeiras: o historico precisa sobreviver mesmo
-- que a reserva, o hospede ou o leitor sejam removidos no futuro.
-- ---------------------------------------------------------------------------
CREATE TABLE access_events (
    id                UUID         PRIMARY KEY,
    reader_id         UUID,
    reader_code       VARCHAR(100) NOT NULL,
    credential_id     UUID,
    access_point_code VARCHAR(100),
    decision          VARCHAR(30)  NOT NULL,
    reason            VARCHAR(50),
    message           VARCHAR(300),
    occurred_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_access_event_decision
        CHECK (decision IN ('GRANTED', 'DENIED')),

    -- Coerencia: liberado nunca tem motivo de recusa; negado sempre tem.
    CONSTRAINT ck_access_event_reason
        CHECK (
            (decision = 'GRANTED' AND reason IS NULL)
            OR
            (decision = 'DENIED'  AND reason IS NOT NULL)
        )
);

CREATE INDEX ix_access_events_occurred   ON access_events (occurred_at DESC);
CREATE INDEX ix_access_events_credential ON access_events (credential_id);
CREATE INDEX ix_access_events_reader     ON access_events (reader_code);
