package com.gabrielpacheco.agendamento.service;

import com.gabrielpacheco.agendamento.config.BookingPolicy;
import com.gabrielpacheco.agendamento.domain.Appointment;
import com.gabrielpacheco.agendamento.domain.AppointmentStatus;
import com.gabrielpacheco.agendamento.domain.Professional;
import com.gabrielpacheco.agendamento.domain.ServiceType;
import com.gabrielpacheco.agendamento.exception.BusinessException;
import com.gabrielpacheco.agendamento.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 19, 14, 0);

    @Mock
    private AppointmentRepository appointmentRepository;

    private final BookingPolicy policy = new BookingPolicy(Duration.ofMinutes(30), 60, Duration.ofHours(2), Duration.ofMinutes(15));

    private AppointmentService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new AppointmentService(appointmentRepository, null, null, policy, clock);
    }

    private Appointment appointment() {
        Appointment appointment = new Appointment(new Professional("Ana", null),
                new ServiceType("Corte", 30, new BigDecimal("45.00")), "Cliente", "48999990000", START);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        return appointment;
    }

    @Test
    void cancelsWithinDeadline() {
        Appointment appointment = appointment();

        serviceAt(START.minusHours(2)).cancel(1L, "Imprevisto");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancellationReason()).isEqualTo("Imprevisto");
    }

    @Test
    void refusesLateCancellation() {
        Appointment appointment = appointment();

        assertThatThrownBy(() -> serviceAt(START.minusMinutes(119)).cancel(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2h antes");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void completesOnlyAfterStartEvenWithoutConfirmation() {
        Appointment appointment = appointment();

        assertThatThrownBy(() -> serviceAt(START.minusMinutes(1)).complete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ainda não começou");

        serviceAt(START.plusMinutes(10)).complete(1L);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void noShowRequiresConfirmedAppointment() {
        Appointment appointment = appointment();
        serviceAt(START.minusDays(1)).confirm(1L);

        serviceAt(START.plusMinutes(20)).markNoShow(1L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThatThrownBy(() -> serviceAt(START.minusDays(1)).cancel(1L, null))
                .as("um atendimento encerrado não volta a mudar")
                .isInstanceOf(BusinessException.class);
    }
}
