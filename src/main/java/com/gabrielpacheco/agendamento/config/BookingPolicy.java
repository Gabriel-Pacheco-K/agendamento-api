package com.gabrielpacheco.agendamento.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Regras de agendamento configuráveis pelo application.yml, sem mudar código.
 *
 * @param minAdvance     antecedência mínima para marcar (ex.: 30 min)
 * @param maxAdvanceDays até quantos dias à frente é possível marcar
 * @param cancelDeadline até quanto tempo antes do início o cliente pode cancelar
 * @param slotStep       intervalo entre os horários oferecidos na agenda (ex.: a cada 15 min)
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingPolicy(Duration minAdvance, int maxAdvanceDays, Duration cancelDeadline, Duration slotStep) {
}
