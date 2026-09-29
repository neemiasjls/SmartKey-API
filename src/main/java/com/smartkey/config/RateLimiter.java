package com.smartkey.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita quantas tentativas cada origem pode fazer num intervalo.
 *
 * PARA QUE SERVE AQUI:
 *
 * Nenhuma das nossas chaves é adivinhável na força bruta — são 32 bytes
 * sorteados. Mas isso não torna o limite inútil:
 *
 *   1. Impede que alguém derrube o servidor com uma enxurrada de tentativas.
 *      Cada verificação de assinatura custa processamento; sem limite, um
 *      atacante consome a CPU toda e as portas legítimas param de responder.
 *
 *   2. Deixa rastro. Uma origem batendo no limite repetidamente é um sinal
 *      claro de que alguém está tentando alguma coisa.
 *
 *   3. Protege o link de ativação do hóspede, que é o elo mais frágil:
 *      ele é curto o bastante para valer a pena tentar.
 *
 * LIMITAÇÃO QUE PRECISA SER DITA: a contagem fica na memória DESTE servidor.
 * Com duas instâncias rodando, cada uma conta as suas — o limite real dobra.
 * Para valer de verdade em produção com várias instâncias, isto precisa ir
 * para um armazenamento compartilhado (Redis). Com uma instância só, que é o
 * caso do plano gratuito do Render, funciona como esperado.
 */
@Component
public class RateLimiter {

    /**
     * Uma "janela fixa": conta tentativas até o instante em que ela expira,
     * e então recomeça do zero.
     *
     * É menos preciso que uma janela deslizante — quem acertar a virada
     * consegue o dobro de tentativas em um curto intervalo. Para o nosso
     * propósito (frear enxurradas) isso é irrelevante, e a implementação
     * simples significa menos lugares onde errar.
     */
    private record Window(Instant resetAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Evita que a memória cresça sem limite se muitas origens aparecerem. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    /**
     * @param key        quem está tentando (IP, chave de leitor, etc.)
     * @param maxPerWindow quantas tentativas são permitidas
     * @param window     tamanho do intervalo
     * @return true se pode prosseguir; false se estourou o limite
     */
    public boolean tryAcquire(String key, int maxPerWindow, Duration window) {
        Instant now = Instant.now();

        if (windows.size() > MAX_TRACKED_KEYS) {
            cleanup(now);
        }

        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.resetAt())) {
                return new Window(now.plus(window), new AtomicInteger(0));
            }
            return existing;
        });

        return current.count().incrementAndGet() <= maxPerWindow;
    }

    /** Zera a contagem de uma origem (usado após uma tentativa bem-sucedida). */
    public void reset(String key) {
        windows.remove(key);
    }

    private void cleanup(Instant now) {
        windows.entrySet().removeIf(entry -> now.isAfter(entry.getValue().resetAt()));
    }
}
