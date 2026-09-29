package com.smartkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracoes da aplicacao, lidas do arquivo application.yml
 * (ou de variaveis de ambiente, quando roda na nuvem).
 */
@ConfigurationProperties(prefix = "smartkey")
public class SmartKeyProperties {

    /**
     * Senha do painel administrativo. Enviada no cabecalho "x-admin-key".
     * Na nuvem, vem da variavel de ambiente ADMIN_API_KEY.
     */
    private String adminApiKey = "";

    /**
     * Permite simular um acesso numa data/hora arbitraria, atraves do campo
     * "at" na requisicao.
     *
     * Isso e essencial para testar "antes do check-in" e "depois do checkout"
     * sem ter que esperar ate 2026. MAS e um buraco de seguranca em producao:
     * qualquer um poderia fingir estar em outro momento. Por isso o padrao e
     * false, e so ligamos em desenvolvimento.
     */
    private boolean allowTimeTravel = false;

    /**
     * Quando true, cria automaticamente na primeira execucao o cenario de
     * exemplo (Joao, apartamento 804, os quatro leitores). Util para testar
     * sem ter que cadastrar tudo a mao.
     */
    private boolean seedDemo = false;

    /**
     * Quantos segundos um desafio criptografico continua valido.
     *
     * Curto de proposito: e a janela em que uma resposta interceptada ainda
     * poderia, em tese, ser util a um atacante. 60 segundos e folgado para
     * encostar o celular no leitor e apertado para qualquer outra coisa.
     */
    private int challengeTtlSeconds = 60;

    /**
     * Mantem ligado o endpoint antigo /api/access/check, da FASE 1, que
     * libera a porta apenas com o id da credencial, SEM assinatura.
     *
     * E util para testar as regras de horario e permissao, mas NAO deve ficar
     * ligado em producao: quem descobrir um id de credencial entraria.
     */
    private boolean allowInsecureCheck = false;

    /**
     * Por quantos segundos um QR Code continua valido.
     *
     * Ele precisa de uma folga maior que o desafio do NFC: o hospede abre o
     * app, procura a porta, mira a camera. Mas continua curto, porque e a
     * janela em que um print da tela ainda poderia ser usado por outra pessoa.
     */
    private int qrTtlSeconds = 45;

    /**
     * Quantos proxies confiaveis ficam entre a internet e esta aplicacao.
     *
     * 0 = acesso direto (desenvolvimento local)
     * 1 = so o Render
     * 2 = Cloudflare na frente do Render
     *
     * Errar para MAIS do que o real abre brecha: o sistema passaria a
     * confiar num valor escrito pelo proprio cliente. Na duvida, use o menor.
     */
    private int trustedProxyHops = 0;

    /**
     * Indica uma instalacao de demonstracao. So muda o que o painel exibe
     * (por exemplo, a dica da chave de teste); nao afrouxa seguranca nenhuma.
     */
    private boolean demoMode = false;

    /** Tentativas de ativacao de chave permitidas por minuto, por origem. */
    private int enrollAttemptsPerMinute = 10;

    public String getAdminApiKey() {
        return adminApiKey;
    }

    public void setAdminApiKey(String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    public boolean isAllowTimeTravel() {
        return allowTimeTravel;
    }

    public void setAllowTimeTravel(boolean allowTimeTravel) {
        this.allowTimeTravel = allowTimeTravel;
    }

    public boolean isSeedDemo() {
        return seedDemo;
    }

    public void setSeedDemo(boolean seedDemo) {
        this.seedDemo = seedDemo;
    }

    public int getChallengeTtlSeconds() {
        return challengeTtlSeconds;
    }

    public void setChallengeTtlSeconds(int challengeTtlSeconds) {
        this.challengeTtlSeconds = challengeTtlSeconds;
    }

    public int getQrTtlSeconds() {
        return qrTtlSeconds;
    }

    public void setQrTtlSeconds(int qrTtlSeconds) {
        this.qrTtlSeconds = qrTtlSeconds;
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public int getEnrollAttemptsPerMinute() {
        return enrollAttemptsPerMinute;
    }

    public void setEnrollAttemptsPerMinute(int enrollAttemptsPerMinute) {
        this.enrollAttemptsPerMinute = enrollAttemptsPerMinute;
    }

    public boolean isAllowInsecureCheck() {
        return allowInsecureCheck;
    }

    public void setAllowInsecureCheck(boolean allowInsecureCheck) {
        this.allowInsecureCheck = allowInsecureCheck;
    }
}
