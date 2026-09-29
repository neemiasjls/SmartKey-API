package com.smartkey.domain.crypto;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Monta EXATAMENTE o texto que o celular assina e que o servidor confere.
 *
 * As duas pontas precisam montar o mesmo texto, byte a byte. Se houver
 * qualquer diferença - um espaço, uma ordem trocada - a assinatura não bate
 * e o acesso é negado.
 *
 * POR QUE O TEXTO TEM TODOS ESSES PEDAÇOS:
 *
 *   SMARTKEY-ACCESS-v1  separação de domínio. Garante que uma assinatura
 *                       feita para outra finalidade (outro sistema, outra
 *                       versão do protocolo) nunca seja aceita aqui.
 *
 *   challengeId         amarra a assinatura àquele desafio específico.
 *
 *   nonce               o número sorteado, que nunca se repete.
 *
 *   readerCode          amarra a assinatura ÀQUELA PORTA. Sem isto, alguém
 *                       poderia capturar a resposta dada na portaria e
 *                       reapresentá-la na porta de um apartamento.
 *
 *   credentialId        amarra a assinatura àquela credencial, impedindo que
 *                       a resposta seja reaproveitada em nome de outra chave.
 *
 * O separador "|" nunca aparece dentro dos campos (são UUIDs, Base64 e
 * códigos com letras, números e underline), então não há ambiguidade sobre
 * onde um campo termina e o outro começa.
 */
public final class SignedMessage {

    /** Identifica o protocolo e a versão. Mudou o formato, muda a versão. */
    public static final String DOMAIN = "SMARTKEY-ACCESS-v1";

    private static final char SEPARATOR = '|';

    private SignedMessage() {
        // classe utilitaria
    }

    /** Devolve o texto a ser assinado, em forma legível. */
    public static String build(UUID challengeId,
                               String nonce,
                               String readerCode,
                               UUID credentialId) {
        return DOMAIN
                + SEPARATOR + challengeId
                + SEPARATOR + nonce
                + SEPARATOR + readerCode
                + SEPARATOR + credentialId;
    }

    /**
     * O mesmo texto, com um marcador no lugar da credencial - para documentar
     * ao leitor o que o celular deve assinar.
     *
     * Montado explicitamente, e nao com replace() sobre o texto final: o
     * sorteio e Base64 e pode conter qualquer sequencia de letras.
     */
    public static String template(UUID challengeId, String nonce, String readerCode) {
        return DOMAIN
                + SEPARATOR + challengeId
                + SEPARATOR + nonce
                + SEPARATOR + readerCode
                + SEPARATOR + "{credentialId}";
    }

    /** Os bytes correspondentes, sempre em UTF-8. */
    public static byte[] bytes(UUID challengeId,
                               String nonce,
                               String readerCode,
                               UUID credentialId) {
        return build(challengeId, nonce, readerCode, credentialId)
                .getBytes(StandardCharsets.UTF_8);
    }
}
