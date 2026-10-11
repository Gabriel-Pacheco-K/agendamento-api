package com.gabrielpacheco.agendamento.service;

import com.gabrielpacheco.agendamento.config.BookingPolicy;
import com.gabrielpacheco.agendamento.domain.Appointment;
import com.gabrielpacheco.agendamento.domain.Professional;
import com.gabrielpacheco.agendamento.domain.ServiceType;
import com.gabrielpacheco.agendamento.domain.WorkingHours;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private final BookingPolicy policy = new BookingPolicy(Duration.ofMinutes(30), 60, Duration.ofHours(2), Duration.ofMinutes(15));
    private final AvailabilityService service = new AvailabilityService(null, null, policy, Clock.systemUTC());

    private final Professional ana = new Professional("Ana", "Cabeleireira");
    private final ServiceType haircut = new ServiceType("Corte", 30, new BigDecimal("45.00"));
    private final List<WorkingHours> morning = List.of(
            new WorkingHours(ana, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));

    @Test
    void emptyDayOffersEverySlotWhereTheServiceFits() {
        List<LocalTime> slots = service.computeSlots(morning, List.of(), haircut, MONDAY, MONDAY.atStartOfDay());

        // 09:45 fica de fora: o corte terminaria às 10:15, depois do fim do expediente.
        assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30));
    }

    @Test
    void slotsOverlappingABookingAreRemovedButAdjacentOnesStay() {
        Appointment booked = new Appointment(ana, haircut, "Cliente", "48999990000", MONDAY.atTime(9, 30));

        List<LocalTime> slots = service.computeSlots(morning, List.of(booked), haircut, MONDAY, MONDAY.atStartOfDay());

        // 09:00–09:30 encosta no agendamento das 09:30, mas não se sobrepõe a ele.
        assertThat(slots).containsExactly(LocalTime.of(9, 0));
    }

    @Test
    void respectsMinimumAdvance() {
        List<LocalTime> slots = service.computeSlots(morning, List.of(), haircut, MONDAY, MONDAY.atTime(9, 10));

        assertThat(slots).containsExactly(LocalTime.of(9, 15), LocalTime.of(9, 30));
    }

    @Test
    void lunchBreakSplitsTheDay() {
        List<WorkingHours> splitDay = List.of(
                new WorkingHours(ana, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0)),
                new WorkingHours(ana, DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(13, 30)));
        ServiceType longService = new ServiceType("Coloração", 60, new BigDecimal("150.00"));

        assertThat(service.computeSlots(splitDay, List.of(), longService, MONDAY, MONDAY.atStartOfDay()))
                .as("um serviço de 1h não pode atravessar o almoço")
                .containsExactly(LocalTime.of(11, 0));
    }
}
