package com.gabrielpacheco.agendamento.service;

import com.gabrielpacheco.agendamento.domain.AppointmentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "SCHEDULED, CONFIRMED, true",
            "SCHEDULED, CANCELLED, true",
            "SCHEDULED, COMPLETED, true",
            "SCHEDULED, NO_SHOW,   true",
            "CONFIRMED, COMPLETED, true",
            "CONFIRMED, NO_SHOW,   true",
            "CONFIRMED, CANCELLED, true",
            "CONFIRMED, SCHEDULED, false",
            "COMPLETED, CANCELLED, false",
            "CANCELLED, CONFIRMED, false",
            "NO_SHOW,   COMPLETED, false"
    })
    void allowsOnlyValidTransitions(AppointmentStatus from, AppointmentStatus to, boolean allowed) {
        assertThat(from.canChangeTo(to)).isEqualTo(allowed);
    }
}
