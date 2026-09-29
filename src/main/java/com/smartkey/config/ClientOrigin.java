package com.smartkey.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Descobre o IP real de quem fez a requisição.
 *
 * POR QUE ISTO NÃO É TRIVIAL
 *
 * Na nuvem, a requisição passa por um proxy (o do Render, e talvez o do
 * Cloudflare) antes de chegar aqui. Para o servidor, quem "chamou" é sempre o
 * proxy — o mesmo IP para todo mundo. O IP verdadeiro vem no cabeçalho
 * X-Forwarded-For, que cada proxy vai completando:
 *
 *     X-Forwarded-For: <o que o cliente mandou>, <IP visto pelo proxy 1>, ...
 *
 * A armadilha: o COMEÇO da lista é escrito pelo próprio cliente, e ele pode
 * pôr ali o que quiser. Quem confiasse no primeiro valor deixaria qualquer
 * atacante escapar do limite de tentativas simplesmente trocando o cabeçalho
 * a cada requisição — e ainda poderia fazer o bloqueio cair em cima do IP de
 * outra pessoa.
 *
 * O valor confiável é o que o NOSSO proxy acrescentou, contado a partir do
 * FINAL da lista. Com um proxy só (o Render), é o último item. Com Cloudflare
 * na frente do Render, são dois saltos: o penúltimo.
 */
@Component
public class ClientOrigin {

    private final SmartKeyProperties properties;

    public ClientOrigin(SmartKeyProperties properties) {
        this.properties = properties;
    }

    public String of(HttpServletRequest request) {
        int hops = properties.getTrustedProxyHops();
        String forwarded = request.getHeader("X-Forwarded-For");

        // Sem proxy configurado, ou sem cabeçalho: o IP da conexão é o real.
        if (hops <= 0 || forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] entries = forwarded.split(",");
        int index = entries.length - hops;

        // Menos entradas do que proxies confiáveis significa que a requisição
        // não passou pelo caminho esperado; não confiamos no cabeçalho.
        if (index < 0) {
            return request.getRemoteAddr();
        }
        return entries[index].trim();
    }
}
