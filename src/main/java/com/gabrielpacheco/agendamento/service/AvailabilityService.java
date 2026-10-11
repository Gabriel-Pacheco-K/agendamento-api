package com.gabrielpacheco.agendamento.service;

import com.gabrielpacheco.agendamento.config.BookingPolicy;
import com.gabrielpacheco.agendamento.domain.Appointment;
import com.gabrielpacheco.agendamento.domain.AppointmentStatus;
import com.gabrielpacheco.agendamento.domain.Professional;
import com.gabrielpacheco.agendamento.domain.ServiceType;
import com.gabrielpacheco.agendamento.domain.WorkingHours;
import com.gabrielpacheco.agendamento.dto.Dtos.Availability;
import com.gabrielpacheco.agendamento.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula os horários livres de um profissional em um dia, para um serviço.
 */
@Service
public class AvailabilityService {

    private final CatalogService catalogService;
    private final AppointmentRepository appointmentRepository;
    private final BookingPolicy policy;
    private final Clock clock;

    public AvailabilityService(CatalogService catalogService, AppointmentRepository appointmentRepository,
                               BookingPolicy policy, Clock clock) {
        this.catalogService = catalogService;
        this.appointmentRepository = appointmentRepository;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Availability availableSlots(Long professionalId, Long serviceId, LocalDate date) {
        Professional professional = catalogService.findProfessional(professionalId);
        ServiceType service = catalogService.findService(serviceId);

        List<Appointment> booked = appointmentRepository.findInRange(
                professionalId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), AppointmentStatus.ACTIVE);

        List<LocalTime> slots = computeSlots(professional.hoursOn(date.getDayOfWeek()), booked, service, date,
                LocalDateTime.now(clock).plus(policy.minAdvance()));

        return new Availability(professionalId, serviceId, date, service.getDurationMinutes(), slots);
    }

    /**
     * Percorre cada período de expediente em passos de {@code slotStep} e mantém
     * os horários em que o serviço inteiro cabe no período, não colide com outro
     * agendamento e respeita a antecedência mínima.
     *
     * <p>Os agendamentos chegam ordenados por início, e o ponteiro {@code next} só avança.
     * Assim a verificação de conflito custa O(horários + agendamentos), sem comparar cada horário com todos.
     */
    List<LocalTime> computeSlots(List<WorkingHours> periods, List<Appointment> booked, ServiceType service,
                                 LocalDate date, LocalDateTime earliest) {
        List<LocalTime> slots = new ArrayList<>();

        for (WorkingHours period : periods) {
            LocalDateTime cursor = date.atTime(period.getStartTime());
            LocalDateTime periodEnd = date.atTime(period.getEndTime());
            int next = 0;

            while (!cursor.plus(service.getDuration()).isAfter(periodEnd)) {
                LocalDateTime slotEnd = cursor.plus(service.getDuration());

                while (next < booked.size() && !booked.get(next).getEndAt().isAfter(cursor)) {
                    next++;
                }
                boolean overlaps = next < booked.size() && booked.get(next).getStartAt().isBefore(slotEnd);

                if (!overlaps && !cursor.isBefore(earliest)) {
                    slots.add(cursor.toLocalTime());
                }
                cursor = cursor.plus(policy.slotStep());
            }
        }
        return slots;
    }
}
