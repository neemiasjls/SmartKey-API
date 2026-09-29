package com.smartkey.config.security;

import com.smartkey.config.ClientOrigin;
import com.smartkey.config.RateLimiter;
import com.smartkey.config.SmartKeyProperties;
import com.smartkey.repository.ReaderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import static com.smartkey.config.security.ApiKeyAuthenticationFilter.ROLE_ADMIN;
import static com.smartkey.config.security.ApiKeyAuthenticationFilter.ROLE_READER;

/**
 * QUEM PODE ACESSAR O QUÊ — tudo decidido aqui, num lugar só.
 *
 * Três princípios guiam esta configuração:
 *
 * 1. NEGAR POR PADRÃO. A última regra nega tudo. Um endpoint novo nasce
 *    fechado; para abri-lo é preciso vir aqui e liberar de propósito.
 *    O contrário (liberar por padrão e fechar o que lembrar) é como os
 *    sistemas vazam.
 *
 * 2. DECIDIR PELO ENDEREÇO NORMALIZADO. O Spring Security compara as regras
 *    com o mesmo caminho que o Spring MVC usa para rotear — e, antes disso,
 *    rejeita endereços ambíguos ("/api;/admin", "//api", "%2F"). Foi essa
 *    discordância entre o filtro antigo e o roteador que permitia entrar no
 *    painel administrativo sem chave.
 *
 * 3. MENOR PRIVILÉGIO. Cada tipo de chamador só alcança o que precisa:
 *
 *      anônimo   páginas, documentação, ativação da chave do hóspede
 *      LEITOR    apenas os quatro endpoints de verificação de acesso
 *      ADMIN     tudo
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Content-Security-Policy: diz ao navegador de onde a página pode carregar
     * coisas. Se um dia algum dado malicioso escapar para dentro do HTML, o
     * navegador se recusa a executar script que não venha deste servidor.
     *
     * 'unsafe-inline' em style-src é uma concessão: as páginas usam estilos
     * embutidos. Estilo embutido não executa código, então o risco é baixo;
     * para SCRIPTS não há concessão nenhuma.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "connect-src 'self'",
            "manifest-src 'self'",
            "worker-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ReaderRepository readerRepository,
                                                   SmartKeyProperties properties,
                                                   RateLimiter rateLimiter,
                                                   ClientOrigin clientOrigin) throws Exception {

        var apiKeyFilter = new ApiKeyAuthenticationFilter(
                readerRepository, properties, rateLimiter, clientOrigin);

        http
                // A autenticação é por cabeçalho, sem cookie de sessão. Sem
                // cookie, o navegador não envia credencial nenhuma sozinho — e é
                // exatamente disso que o ataque CSRF depende. A proteção contra
                // CSRF não teria o que proteger aqui.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)

                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED,
                                        "Autenticação necessária. Envie o cabeçalho "
                                                + "'x-admin-key' ou 'x-reader-key'."))
                        .accessDeniedHandler((request, response, ex) ->
                                JsonErrorWriter.write(response, HttpStatus.FORBIDDEN,
                                        "Esta chave não tem permissão para este recurso.")))

                .authorizeHttpRequests(auth -> auth
                        // --- Administração ------------------------------------
                        .requestMatchers("/api/admin/**").hasRole(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/access/events").hasRole(ROLE_ADMIN)

                        // --- Fechaduras -----------------------------------------
                        // O leitor passa daqui, mas a ReaderScopeGuard ainda
                        // confere se ele está respondendo pela PRÓPRIA porta.
                        .requestMatchers(HttpMethod.POST,
                                "/api/access/challenge",
                                "/api/access/verify",
                                "/api/access/qr-verify",
                                "/api/access/check").hasAnyRole(ROLE_ADMIN, ROLE_READER)

                        // --- Público --------------------------------------------
                        // O hóspede não tem chave de API: a autorização dele é
                        // o token de uso único que vem no corpo da ativação.
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/keys/enroll", "/api/keys/qr").permitAll()

                        // Qualquer outro endereço da API: fechado.
                        .requestMatchers("/api/**").denyAll()

                        // --- Páginas e documentação -----------------------------
                        .requestMatchers(HttpMethod.GET,
                                "/", "/*.html", "/*.js", "/*.css", "/*.json",
                                "/icons/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Negar por padrão.
                        .anyRequest().denyAll())

                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // Nenhum endereço vaza para outros sites ao clicar num link.
                        .referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // A câmera só pode ser usada por páginas deste servidor.
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(self), microphone=(), geolocation=(), payment=()")));

        return http.build();
    }

    /**
     * Endereços ambíguos ("/api;/admin", "//api") são recusados com 400.
     *
     * Sem isto, a recusa vira uma exceção sem tratamento e chega ao cliente
     * como erro 500 — o que, além de feio, polui o log de erros do servidor
     * toda vez que um robô de varredura passa por aqui.
     */
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return new HttpStatusRequestRejectedHandler(HttpStatus.BAD_REQUEST.value());
    }

    /**
     * Impede o Spring Boot de criar sozinho um usuário padrão com senha
     * aleatória impressa no console. Não usamos usuário e senha: a identidade
     * vem das chaves de API.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
