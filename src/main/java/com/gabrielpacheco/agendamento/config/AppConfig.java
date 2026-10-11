package com.gabrielpacheco.agendamento.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(BookingPolicy.class)
@OpenAPIDefinition(info = @Info(
        title = "Agendamento API",
        version = "1.0.0",
        description = "Agenda de profissionais com horários disponíveis, bloqueio de conflitos e ciclo de vida dos agendamentos.",
        contact = @Contact(name = "Gabriel Pacheco Kiesel", url = "https://gabriel-pacheco-k.github.io")))
public class AppConfig {

    /**
     * Relógio no fuso do estabelecimento. Injetá-lo permite testar regras
     * que dependem da hora atual, como antecedência e prazo de cancelamento.
     */
    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
