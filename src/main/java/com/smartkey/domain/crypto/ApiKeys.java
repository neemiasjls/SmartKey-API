package com.smartkey.domain.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Geração e conferência das chaves de API dos leitores.
 *
 * POR QUE SHA-256 E NÃO BCRYPT
 *
 * Para senhas escolhidas por pessoas, o certo é BCrypt ou Argon2: senhas
 * humanas têm pouca variedade, e esses algoritmos são LENTOS de propósito,
 * para tornar inviável testar milhões de palpites.
 *
 * Aqui o caso é outro. Estas chaves são sorteadas com 32 bytes de
 * aleatoriedade — são 2^256 possibilidades. Não existe lista de "chaves mais
 * usadas" para tentar, e nenhuma quantidade de palpites chega perto.
 *
 * E há um motivo prático forte para não usar BCrypt: a fechadura confere a
 * chave a CADA tentativa de abertura. O BCrypt levaria centenas de
 * milissegundos por conferência — atraso desnecessário com alguém parado na
 * porta, e um belo alvo para derrubar o servidor com requisições.
 *
 * O que NÃO abrimos mão: a comparação é feita em tempo constante, para que o
 * tempo de resposta não entregue quantos caracteres estavam certos.
 */
public final class ApiKeys {

    private static final int KEY_BYTES = 32;
    private static final String PREFIX = "rdr_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
        // classe utilitaria
    }

    /**
     * Sorteia uma chave nova.
     *
     * O prefixo "rdr_" serve para reconhecer de relance o que é aquele texto
     * caso ele apareça onde não devia — num log, num print, num commit. Vários
     * serviços fazem isso, e ferramentas de varredura de segredos usam esses
     * prefixos para detectar vazamentos automaticamente.
     */
    public static String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Calcula o que fica guardado no banco. A chave original não é salva. */
    public static String hash(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em toda JVM; se faltar, algo está muito errado.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /**
     * Confere a chave apresentada contra o hash guardado.
     *
     * Usa MessageDigest.isEqual, que compara todos os bytes sempre. Um equals()
     * comum pararia no primeiro byte diferente, e a diferença de tempo entre
     * "errou no primeiro caractere" e "errou no último" permitiria descobrir a
     * chave aos poucos, cronometrando as respostas.
     */
    public static boolean matches(String presentedKey, String storedHash) {
        if (presentedKey == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presentedKey).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
