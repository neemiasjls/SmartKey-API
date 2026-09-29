-- ===========================================================================
-- Token de ativação da chave
--
-- O PROBLEMA QUE ISTO RESOLVE:
--
-- O celular do hóspede precisa enviar sua chave pública ao servidor. Mas ele
-- não pode usar a chave de administração — com ela, o hóspede poderia criar
-- reservas, abrir outros apartamentos e apagar registros.
--
-- E deixar o cadastro aberto seria pior ainda: qualquer um que descobrisse o
-- id de uma credencial registraria a PRÓPRIA chave pública nela, e passaria a
-- abrir a porta daquele apartamento.
--
-- A SOLUÇÃO:
--
-- Ao emitir a chave, o sistema sorteia um token. Ele vai no link entregue ao
-- hóspede e serve para UMA ÚNICA coisa: registrar a chave pública daquele
-- aparelho. Depois de usado, queima.
--
-- É o mesmo princípio do convite de uso único que aplicativos de fechadura
-- enviam por mensagem.
-- ===========================================================================

ALTER TABLE credentials
    ADD COLUMN enrollment_token VARCHAR(64),
    ADD COLUMN enrollment_used_at TIMESTAMPTZ;

-- Dois tokens iguais nunca podem existir.
CREATE UNIQUE INDEX uk_credentials_enrollment_token
    ON credentials (enrollment_token)
    WHERE enrollment_token IS NOT NULL;
