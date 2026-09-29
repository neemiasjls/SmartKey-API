package com.smartkey.domain.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * O conteúdo do QR Code que o celular mostra na tela.
 *
 * FORMATO (tudo em Base64 sem preenchimento, separado por ponto):
 *
 *     SK1.<credencial>.<nonce>.<segundos>.<assinatura>
 *
 * Fica com cerca de 145 caracteres — o suficiente para um QR que a câmera lê
 * rápido, mesmo com a tela do celular meio suja ou com pouca luz.
 *
 * O TEXTO ASSINADO é outro, e mais explícito:
 *
 *     SMARTKEY-QR-v1|<credentialId>|<nonce>|<segundos>
 *
 * Note que o prefixo é diferente do usado no NFC (SMARTKEY-ACCESS-v1). Isso é
 * proposital: uma assinatura feita para o canal NFC não vale no canal QR, e
 * vice-versa. Separar os domínios impede que uma resposta capturada num canal
 * seja reaproveitada no outro.
 */
public record QrPayload(
        UUID credentialId,
        String nonce,
        long issuedAtEpochSeconds,
        String signatureBase64
) {

    public static final String PREFIX = "SK1";
    public static final String DOMAIN = "SMARTKEY-QR-v1";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** Tamanho máximo aceito, para não tentar processar lixo enorme. */
    private static final int MAX_LENGTH = 512;

    /**
     * O sorteio: 16 a 32 bytes em Base64 de URL (22 a 43 caracteres).
     * Menos que isso seria adivinhável; mais não caberia na coluna do banco.
     */
    private static final java.util.regex.Pattern NONCE_FORMAT =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{22,43}$");

    /** O texto que precisa ser assinado pela chave privada do aparelho. */
    public static String messageToSign(UUID credentialId, String nonce, long issuedAt) {
        return DOMAIN + "|" + credentialId + "|" + nonce + "|" + issuedAt;
    }

    public byte[] signedMessageBytes() {
        return messageToSign(credentialId, nonce, issuedAtEpochSeconds)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Lê o conteúdo do QR.
     *
     * Devolve null diante de QUALQUER problema, em vez de lançar exceção:
     * a câmera lê muita coisa que não é nosso código (etiquetas, cartazes,
     * outro QR qualquer), e isso não é motivo para derrubar nada.
     */
    public static QrPayload parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
            return null;
        }

        String[] parts = raw.trim().split("\\.");
        if (parts.length != 5 || !PREFIX.equals(parts[0])) {
            return null;
        }

        try {
            byte[] credentialBytes = DECODER.decode(parts[1]);
            if (credentialBytes.length != 16) {
                return null;
            }

            ByteBuffer buffer = ByteBuffer.wrap(credentialBytes);
            UUID credentialId = new UUID(buffer.getLong(), buffer.getLong());

            String nonce = parts[2];
            if (!NONCE_FORMAT.matcher(nonce).matches()) {
                return null;
            }
            long issuedAt = Long.parseLong(parts[3]);

            // A assinatura é revalidada depois; aqui só conferimos que é
            // Base64 legível, para não carregar lixo adiante.
            DECODER.decode(parts[4]);

            return new QrPayload(credentialId, nonce,
                    issuedAt, toStandardBase64(parts[4]));

        } catch (IllegalArgumentException | ArithmeticException e) {
            return null;
        }
    }

    /** Monta o texto do QR. Usado pelos testes e pela documentação. */
    public String encode() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(credentialId.getMostSignificantBits());
        buffer.putLong(credentialId.getLeastSignificantBits());

        return String.join(".",
                PREFIX,
                ENCODER.encodeToString(buffer.array()),
                nonce,
                Long.toString(issuedAtEpochSeconds),
                toUrlBase64(signatureBase64));
    }

    /**
     * O QR usa Base64 "de URL" (com - e _), que não tem caracteres problemáticos.
     * O verificador de assinatura espera o Base64 comum (com + e /).
     */
    private static String toStandardBase64(String urlSafe) {
        return Base64.getEncoder().encodeToString(DECODER.decode(urlSafe));
    }

    private static String toUrlBase64(String standard) {
        return ENCODER.encodeToString(Base64.getDecoder().decode(standard));
    }
}
