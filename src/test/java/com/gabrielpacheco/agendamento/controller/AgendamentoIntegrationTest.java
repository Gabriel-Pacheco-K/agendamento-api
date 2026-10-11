package com.gabrielpacheco.agendamento.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.agendamento.dto.Dtos.AppointmentRequest;
import com.gabrielpacheco.agendamento.exception.ConflictException;
import com.gabrielpacheco.agendamento.repository.AppointmentRepository;
import com.gabrielpacheco.agendamento.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a API de ponta a ponta: HTTP, validação, regras de agenda, concorrência e migrações Flyway.
 * Os horários são marcados na próxima segunda-feira, para os testes não dependerem do dia em que rodam.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgendamentoIntegrationTest {

    private static final LocalDate NEXT_MONDAY =
            LocalDate.now(ZoneId.of("America/Sao_Paulo")).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private long professionalId;
    private long haircutId;

    @BeforeEach
    void setUp() throws Exception {
        professionalId = id(post("/api/professionals", Map.of("name", "Ana Souza", "specialty", "Cabeleireira")));
        haircutId = id(post("/api/services", Map.of("name", "Corte", "durationMinutes", 30, "price", 45.00)));

        List<Map<String, String>> periods = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            periods.add(Map.of("dayOfWeek", day.name(), "start", "09:00", "end", "12:00"));
            periods.add(Map.of("dayOfWeek", day.name(), "start", "13:00", "end", "18:00"));
        }
        send(put("/api/professionals/" + professionalId + "/working-hours"), Map.of("periods", periods))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingHours", hasSize(10)));
    }

    @Test
    void bookingRemovesOverlappingSlotsFromAvailability() throws Exception {
        availability().andExpect(jsonPath("$.slots", hasSize(30)));

        book("10:00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.endAt").value(NEXT_MONDAY + "T10:30:00"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        availability()
                .andExpect(jsonPath("$.slots", hasSize(27)))
                .andExpect(jsonPath("$.slots", hasItem("09:30:00")))
                .andExpect(jsonPath("$.slots", not(hasItem("09:45:00"))))
                .andExpect(jsonPath("$.slots", not(hasItem("10:15:00"))))
                .andExpect(jsonPath("$.slots", hasItem("10:30:00")));
    }

    @Test
    void overlappingBookingIsRejectedButAdjacentIsAccepted() throws Exception {
        book("10:00").andExpect(status().isCreated());

        book("10:15").andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Horário indisponível"));
        book("09:45").andExpect(status().isConflict());
        book("10:30").andExpect(status().isCreated());
        book("09:30").andExpect(status().isCreated());
    }

    @Test
    void cancelledAppointmentFreesTheSlot() throws Exception {
        long id = id(book("15:00"));

        send(patch("/api/appointments/" + id + "/cancel"), Map.of("reason", "Cliente desmarcou"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        book("15:00").andExpect(status().isCreated());
    }

    @Test
    void bookingOutsideWorkingHoursIsRejected() throws Exception {
        book("12:00").andExpect(status().isUnprocessableEntity());   // almoço
        book("11:45").andExpect(status().isUnprocessableEntity());   // terminaria 12:15
        book("17:45").andExpect(status().isUnprocessableEntity());   // terminaria 18:15
        book("08:30").andExpect(status().isUnprocessableEntity());

        send(post("/api/appointments"), request(NEXT_MONDAY.minusDays(1).atTime(10, 0)))  // domingo
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/appointments"), request(LocalDateTime.now().minusDays(1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statusFlowThroughTheApi() throws Exception {
        long id = id(book("16:00"));

        send(patch("/api/appointments/" + id + "/confirm"), null).andExpect(jsonPath("$.status").value("CONFIRMED"));
        send(patch("/api/appointments/" + id + "/confirm"), null).andExpect(status().isUnprocessableEntity());
        send(patch("/api/appointments/" + id + "/complete"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O atendimento ainda não começou."));

        mvc.perform(get("/api/appointments").param("professionalId", String.valueOf(professionalId))
                        .param("date", NEXT_MONDAY.toString()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service").value("Corte"));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        Map<String, Object> bad = new java.util.HashMap<>(Map.of(
                "professionalId", professionalId, "serviceId", haircutId,
                "startAt", NEXT_MONDAY.atTime(10, 0).toString(), "customerName", "", "customerPhone", "abc"));
        send(post("/api/appointments"), bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.customerPhone").value("telefone inválido"));

        List<Map<String, String>> overlapping = List.of(
                Map.of("dayOfWeek", "MONDAY", "start", "09:00", "end", "12:00"),
                Map.of("dayOfWeek", "MONDAY", "start", "11:00", "end", "14:00"));
        send(put("/api/professionals/" + professionalId + "/working-hours"), Map.of("periods", overlapping))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/appointments/999999")).andExpect(status().isNotFound());
    }

    /**
     * 10 clientes tentam reservar exatamente o mesmo horário ao mesmo tempo.
     * Sem a trava pessimista, várias threads veriam o horário livre e todas gravariam.
     * Com ela, só uma reserva é aceita.
     */
    @Test
    void concurrentBookingsForTheSameSlotOnlyOneWins() throws Exception {
        int clients = 10;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch startTogether = new CountDownLatch(1);
        LocalDateTime slot = NEXT_MONDAY.atTime(14, 0);

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            String customer = "Cliente " + i;
            Callable<Boolean> attempt = () -> {
                startTogether.await();
                try {
                    appointmentService.book(new AppointmentRequest(professionalId, haircutId, slot, customer, "48999990000"));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }

        startTogether.countDown();
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(appointmentRepository.findAll().stream()
                .filter(a -> a.getProfessional().getId() == professionalId && a.getStartAt().equals(slot)))
                .hasSize(1);
    }

    private ResultActions availability() throws Exception {
        return mvc.perform(get("/api/professionals/" + professionalId + "/availability")
                .param("serviceId", String.valueOf(haircutId))
                .param("date", NEXT_MONDAY.toString()));
    }

    private ResultActions book(String time) throws Exception {
        return send(post("/api/appointments"), request(NEXT_MONDAY.atTime(java.time.LocalTime.parse(time))));
    }

    private Map<String, Object> request(LocalDateTime start) {
        return Map.of("professionalId", professionalId, "serviceId", haircutId, "startAt", start.toString(),
                "customerName", "Maria Silva", "customerPhone", "(48) 99999-0000");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               Object body) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(json.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
    }

    private ResultActions post(String path, Object body) throws Exception {
        return send(post(path), body);
    }

    private long id(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
