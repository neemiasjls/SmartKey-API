package com.smartkey.config.security;

import com.smartkey.config.ClientOrigin;
import com.smartkey.config.RateLimiter;
import com.smartkey.config.SmartKeyProperties;
import com.smartkey.domain.crypto.ApiKeys;
import com.smartkey.repository.ReaderRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Descobre QUEM está chamando, a partir da chave enviada no cabeçalho.
 *
 * Este filtro só identifica. Quem decide o que cada um pode acessar é a
 * {@link SecurityConfig} — e essa separação é justamente o que faltava antes:
 * os filtros antigos identificavam E decidiam, comparando o endereço cru da
 * requisição, enquanto o Spring roteava por outro endereço, normalizado.
 *
 * Três resultados possíveis:
 *
 *   x-admin-key válida   -> ROLE_ADMIN
 *   x-reader-key válida  -> ROLE_READER, com o código do leitor como nome
 *   nenhuma chave        -> segue anônimo (as rotas públicas funcionam; as
 *                           protegidas são barradas mais adiante)
 *
 * Chave presente porém ERRADA é recusada aqui mesmo, e conta para o limite de
 * tentativas. Deixar seguir como anônimo esconderia do cliente que a chave dele
 * está errada — e dificultaria achar o problema.
 *
 * Esta classe NÃO é um @Component de propósito: se fosse, o Spring Boot a
 * registraria também como filtro comum, e ela rodaria duas vezes.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    public static final String ADMIN_HEADER = "x-admin-key";
    public static final String READER_HEADER = "x-reader-key";

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_READER = "READER";

    /**
     * Tentativas com chave ERRADA por origem. Acertos não contam: uma
     * fechadura legítima abre a porta quantas vezes precisar.
     */
    private static final int MAX_FAILED_ATTEMPTS = 20;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(1);

    private final ReaderRepository readerRepository;
    private final SmartKeyProperties properties;
    private final RateLimiter rateLimiter;
    private final ClientOrigin clientOrigin;

    public ApiKeyAuthenticationFilter(ReaderRepository readerRepository,
                                      SmartKeyProperties properties,
                                      RateLimiter rateLimiter,
                                      ClientOrigin clientOrigin) {
        this.readerRepository = readerRepository;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clientOrigin = clientOrigin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String adminKey = request.getHeader(ADMIN_HEADER);
        String readerKey = request.getHeader(READER_HEADER);

        if (adminKey != null) {
            if (!isValidAdminKey(adminKey)) {
                fail(request, response, "Chave administrativa inválida.");
                return;
            }
            authenticate("admin", ROLE_ADMIN);

        } else if (readerKey != null) {
            var reader = readerRepository.findByApiKeyHash(ApiKeys.hash(readerKey));
            if (reader.isEmpty()) {
                fail(request, response, "Chave de leitor inválida.");
                return;
            }
            // O nome da autenticação É o código do leitor: é com ele que a
            // ReaderScopeGuard confina cada leitor à sua própria porta.
            authenticate(reader.get().getCode(), ROLE_READER);
        }

        chain.doFilter(request, response);
    }

    /** Comparação em tempo constante: o tempo de resposta não revela nada. */
    private boolean isValidAdminKey(String presented) {
        String configured = properties.getAdminApiKey();

        // Sem chave configurada, NINGUÉM entra como administrador.
        // Falhar fechado é a postura certa num controle de acesso.
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                configured.getBytes(StandardCharsets.UTF_8));
    }

    private static void authenticate(String name, String role) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                name, null, AuthorityUtils.createAuthorityList("ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void fail(HttpServletRequest request,
                      HttpServletResponse response,
                      String message) throws IOException {

        String origin = clientOrigin.of(request);

        if (!rateLimiter.tryAcquire("auth:" + origin, MAX_FAILED_ATTEMPTS, FAILURE_WINDOW)) {
            log.warn("Excesso de tentativas com chave inválida vindas de {}", origin);
            JsonErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Tentativas demais. Aguarde um minuto.");
            return;
        }
        JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED, message);
    }
}
