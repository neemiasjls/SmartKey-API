package com.smartkey.web;

import com.smartkey.web.error.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static com.smartkey.config.security.ApiKeyAuthenticationFilter.ROLE_ADMIN;
import static com.smartkey.config.security.ApiKeyAuthenticationFilter.ROLE_READER;

/**
 * Garante que um leitor só responda pela SUA porta.
 *
 * A SecurityConfig já garante que só leitores (ou a administração) chegam aos
 * endpoints de verificação. Mas "ser um leitor" não basta: a chave do leitor
 * da academia não pode perguntar sobre a porta do apartamento 804.
 *
 * O ataque que isto impede: com a chave de um leitor fácil de alcançar, pedir
 * um desafio EM NOME da porta do 804, apresentá-lo a um hóspede num leitor
 * adulterado, e usar a resposta assinada para abrir o apartamento.
 */
@Component
public class ReaderScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(ReaderScopeGuard.class);

    public void ensureCanActAs(String readerCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (hasRole(auth, ROLE_ADMIN)) {
            return;   // a administração pode consultar qualquer porta
        }

        if (!hasRole(auth, ROLE_READER)) {
            throw new ForbiddenException("Leitor não autenticado.");
        }

        if (!auth.getName().equals(readerCode)) {
            log.warn("Leitor '{}' tentou responder pela porta de '{}'", auth.getName(), readerCode);
            throw new ForbiddenException(
                    "Esta chave pertence ao leitor '%s' e não pode responder por '%s'."
                            .formatted(auth.getName(), readerCode));
        }
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
