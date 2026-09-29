package com.smartkey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicacao.
 *
 * Rodar com:  mvn spring-boot:run
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SmartKeyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartKeyApplication.class, args);
    }
}
