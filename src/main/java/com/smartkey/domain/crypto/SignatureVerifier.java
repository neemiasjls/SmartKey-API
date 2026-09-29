package com.smartkey.domain.crypto;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Confere se uma assinatura foi mesmo feita pela chave privada correspondente
 * à chave pública cadastrada.
 *
 * O SERVIDOR NUNCA VÊ A CHAVE PRIVADA. Ela é criada dentro do celular e não
 * sai de lá. O servidor guarda apenas a chave pública, que - como o nome diz -
 * não é segredo: com ela só dá para CONFERIR assinaturas, nunca para criá-las.
 *
 * Nada de criptografia caseira aqui: usamos a biblioteca padrão do Java
 * (java.security), com algoritmos públicos e auditados há décadas.
 */
public final class SignatureVerifier {

    /** Tamanho de uma assinatura ECDSA P-256 no formato cru: 32 + 32 bytes. */
    private static final int P256_RAW_SIGNATURE_LENGTH = 64;

    private static final byte DER_SEQUENCE_TAG = 0x30;
    private static final byte DER_INTEGER_TAG = 0x02;

    private SignatureVerifier() {
        // classe utilitaria
    }

    /**
     * @param publicKeyBase64 chave pública em Base64, formato X.509 SPKI
     * @param algorithm       algoritmo declarado no cadastro do dispositivo
     * @param message         os bytes que deveriam ter sido assinados
     * @param signatureBase64 a assinatura recebida, em Base64
     * @return true somente se a assinatura confere
     */
    public static boolean verify(String publicKeyBase64,
                                 SignatureAlgorithm algorithm,
                                 byte[] message,
                                 String signatureBase64) {
        try {
            PublicKey publicKey = decodePublicKey(publicKeyBase64, algorithm);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64.trim());

            if (algorithm == SignatureAlgorithm.ECDSA_P256) {
                signatureBytes = normalizeEcdsaSignature(signatureBytes);
            }

            Signature verifier = Signature.getInstance(algorithm.getSignatureName());
            verifier.initVerify(publicKey);
            verifier.update(message);
            return verifier.verify(signatureBytes);

        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Chave malformada, assinatura corrompida, Base64 inválido...
            // Qualquer problema aqui significa uma coisa só: não confere.
            // Nunca deixamos a exceção escapar, para não revelar a um atacante
            // QUAL foi o defeito da tentativa dele.
            return false;
        }
    }

    /** Reconstrói a chave pública a partir do Base64 cadastrado. */
    public static PublicKey decodePublicKey(String publicKeyBase64,
                                            SignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(publicKeyBase64.trim());
        return KeyFactory.getInstance(algorithm.getKeyFactoryName())
                .generatePublic(new X509EncodedKeySpec(der));
    }

    /**
     * Uma assinatura ECDSA são dois números, r e s. O problema é que existem
     * DUAS formas de embrulhar esses números, e as plataformas discordam:
     *
     *   CRU (raw)  r e s colados, 64 bytes secos.
     *              É o que o Web Crypto do navegador produz.
     *
     *   DER        os dois números dentro de uma estrutura ASN.1, com
     *              marcadores de tipo e tamanho. É o que o Android Keystore
     *              produz, e o único formato que o Java sabe verificar.
     *
     * Aceitamos os dois e convertemos o cru para DER quando necessário - assim
     * o mesmo servidor atende tanto o app Android quanto um navegador.
     */
    static byte[] normalizeEcdsaSignature(byte[] signature) {
        // Já está em DER: começa com o marcador de SEQUENCE.
        if (signature.length > 0 && signature[0] == DER_SEQUENCE_TAG) {
            return signature;
        }
        if (signature.length != P256_RAW_SIGNATURE_LENGTH) {
            throw new IllegalArgumentException(
                    "Assinatura ECDSA com tamanho inesperado: " + signature.length);
        }
        return rawToDer(signature);
    }

    private static byte[] rawToDer(byte[] raw) {
        int half = raw.length / 2;

        // "1" força a interpretação como número positivo: sem isso, um r que
        // comece com bit alto viraria um número negativo e a conta daria errado.
        byte[] r = new BigInteger(1, Arrays.copyOfRange(raw, 0, half)).toByteArray();
        byte[] s = new BigInteger(1, Arrays.copyOfRange(raw, half, raw.length)).toByteArray();

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeInteger(content, r);
        writeInteger(content, s);
        byte[] body = content.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(DER_SEQUENCE_TAG);
        writeLength(out, body.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static void writeInteger(ByteArrayOutputStream out, byte[] value) {
        out.write(DER_INTEGER_TAG);
        writeLength(out, value.length);
        out.write(value, 0, value.length);
    }

    /** Comprimento no formato ASN.1: curto até 127, longo acima disso. */
    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
        } else if (length < 0x100) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    /**
     * Confere se um texto Base64 realmente contém uma chave pública válida
     * para o algoritmo informado. Usado no cadastro, para recusar logo uma
     * chave malformada em vez de descobrir isso na porta.
     */
    public static boolean isValidPublicKey(String publicKeyBase64,
                                           SignatureAlgorithm algorithm) {
        try {
            decodePublicKey(publicKeyBase64, algorithm);
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
