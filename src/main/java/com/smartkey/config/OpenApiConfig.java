package com.smartkey.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura a pagina de documentacao interativa da API.
 *
 * Abra http://localhost:8080/swagger-ui.html para testar tudo clicando,
 * sem precisar decorar comandos de terminal.
 */
@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "AdminApiKey";
    private static final String READER_KEY_SCHEME = "ReaderApiKey";

    @Bean
    public OpenAPI smartKeyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartKey API")
                        .version("0.2.0")
                        .description("""
                                Chave digital temporária para hospedagens.

                                **Antes de testar:** clique no botão "Authorize" no topo
                                da página e informe a chave administrativa
                                (a mesma do application-local.yml).
                                """))
                .components(new Components().addSecuritySchemes(
                        API_KEY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("x-admin-key")
                                .description("Chave administrativa"))
                        .addSecuritySchemes(READER_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("x-reader-key")
                                        .description("Chave de um leitor. Só vale para "
                                                + "os endpoints de acesso, e só para a "
                                                + "porta daquele leitor.")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(READER_KEY_SCHEME));
    }
}
