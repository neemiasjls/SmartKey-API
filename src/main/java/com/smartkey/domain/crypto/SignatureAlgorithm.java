package com.smartkey.domain.crypto;

/**
 * Algoritmos de assinatura aceitos pelo sistema.
 *
 * ESCOLHA PADRÃO: ECDSA_P256.
 *
 * O critério não foi a matemática, e sim ONDE A CHAVE PRIVADA CONSEGUE MORAR.
 * De nada adianta um algoritmo elegante se a chave ficar num arquivo comum,
 * de onde um celular com root a copia.
 *
 *   ECDSA P-256  funciona no Android Keystore com respaldo de hardware desde
 *                o Android 6 (2015), e também no StrongBox. Funciona ainda no
 *                Web Crypto de qualquer navegador.
 *
 *   Ed25519      é mais moderno e tem uma vantagem real (ver abaixo), mas só
 *                chegou ao Android Keystore no Android 13, e mesmo assim
 *                depende de o fabricante ter implementado. Nos navegadores o
 *                suporte é irregular.
 *
 * A DESVANTAGEM DO ECDSA, dita com todas as letras: cada assinatura precisa de
 * um número aleatório de qualidade. Se esse número se repetir, a chave privada
 * pode ser calculada - foi assim que o PlayStation 3 foi quebrado em 2010.
 * O Ed25519 não tem esse risco porque é determinístico.
 *
 * O que nos protege: no Android a assinatura é feita DENTRO do chip seguro,
 * que tem seu próprio gerador de aleatoriedade em hardware. O risco residual é
 * bem menor do que o de deixar a chave privada em software.
 *
 * Os dois algoritmos ficam suportados: quando os aparelhos amadurecerem, basta
 * cadastrar o dispositivo com ED25519, sem mudar nada no resto do sistema.
 */
public enum SignatureAlgorithm {

    /** ECDSA sobre a curva P-256 (secp256r1), com SHA-256. Padrão. */
    ECDSA_P256("EC", "SHA256withECDSA"),

    /** EdDSA sobre a curva 25519. */
    ED25519("Ed25519", "Ed25519");

    private final String keyFactoryName;
    private final String signatureName;

    SignatureAlgorithm(String keyFactoryName, String signatureName) {
        this.keyFactoryName = keyFactoryName;
        this.signatureName = signatureName;
    }

    /** Nome usado pelo Java para reconstruir a chave pública. */
    public String getKeyFactoryName() {
        return keyFactoryName;
    }

    /** Nome usado pelo Java para verificar a assinatura. */
    public String getSignatureName() {
        return signatureName;
    }

    /** Converte o texto vindo da API, aceitando algumas variações comuns. */
    public static SignatureAlgorithm parse(String value) {
        if (value == null || value.isBlank()) {
            return ECDSA_P256;
        }
        String normalized = value.trim().toUpperCase()
                .replace("-", "_")
                .replace(" ", "_");

        return switch (normalized) {
            case "ECDSA_P256", "ECDSA", "EC", "P_256", "P256", "SECP256R1", "ES256"
                    -> ECDSA_P256;
            case "ED25519", "EDDSA", "CURVE25519"
                    -> ED25519;
            default -> throw new IllegalArgumentException(
                    "Algoritmo não suportado: " + value
                    + ". Use ECDSA_P256 ou ED25519.");
        };
    }
}
