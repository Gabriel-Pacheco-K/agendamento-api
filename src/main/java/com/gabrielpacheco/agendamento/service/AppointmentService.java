package com.gabrielpacheco.agendamento.service;

import com.gabrielpacheco.agendamento.config.BookingPolicy;
import com.gabrielpacheco.agendamento.domain.Appointment;
import com.gabrielpacheco.agendamento.domain.AppointmentStatus;
import com.gabrielpacheco.agendamento.domain.Professional;
import com.gabrielpacheco.agendamento.domain.ServiceType;
import com.gabrielpacheco.agendamento.dto.Dtos.AppointmentRequest;
import com.gabrielpacheco.agendamento.dto.Dtos.AppointmentResponse;
import com.gabrielpacheco.agendamento.dto.Dtos.DailyRevenue;
import com.gabrielpacheco.agendamento.exception.BusinessException;
import com.gabrielpacheco.agendamento.exception.ConflictException;
import com.gabrielpacheco.agendamento.exception.NotFoundException;
import com.gabrielpacheco.agendamento.repository.AppointmentRepository;
import com.gabrielpacheco.agendamento.repository.ProfessionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final CatalogService catalogService;
    private final BookingPolicy policy;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ProfessionalRepository professionalRepository,
                              CatalogService catalogService,
                              BookingPolicy policy,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.catalogService = catalogService;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Agenda um horário. A trava no profissional faz com que a verificação de conflito
     * e a gravação aconteçam juntas, sem que outra reserva entre no meio.
     */
    @Transactional
    public AppointmentResponse book(AppointmentRequest request) {
        ServiceType service = catalogService.findService(request.serviceId());
        Professional professional = professionalRepository.findByIdForUpdate(request.professionalId())
                .orElseThrow(() -> new NotFoundException("Profissional " + request.professionalId() + " não encontrado."));

        LocalDateTime start = request.startAt().withSecond(0).withNano(0);
        LocalDateTime end = start.plus(service.getDuration());

        checkAdvance(start);
        checkWorkingHours(professional, start, end);

        if (appointmentRepository.existsOverlap(professional.getId(), start, end, AppointmentStatus.ACTIVE)) {
            throw new ConflictException("O horário " + start.toLocalTime() + " já está ocupado para " + professional.getName() + ".");
        }

        Appointment appointment = new Appointment(professional, service, request.customerName().trim(),
                request.customerPhone().trim(), start);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) {
        return AppointmentResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> agenda(Long professionalId, LocalDate date) {
        catalogService.findProfessional(professionalId);
        return appointmentRepository.findInRange(professionalId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(),
                        EnumSet.allOf(AppointmentStatus.class))
                .stream().map(AppointmentResponse::from).toList();
    }

    /** Até um ano por consulta, o bastante para o gráfico do ano no painel do dono. */
    static final int MAX_REVENUE_DAYS = 366;

    @Transactional(readOnly = true)
    public List<DailyRevenue> revenue(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException("A data final não pode ser antes da inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_REVENUE_DAYS) {
            throw new BusinessException("O período pode ter no máximo " + MAX_REVENUE_DAYS + " dias.");
        }
        Map<LocalDate, List<Appointment>> byDay = appointmentRepository
                .findCompletedStartingBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.groupingBy(a -> a.getStartAt().toLocalDate(), TreeMap::new, Collectors.toList()));
        return byDay.entrySet().stream()
                .map(day -> new DailyRevenue(day.getKey(), day.getValue().size(), day.getValue().stream()
                        .map(a -> a.getService().getPrice())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    @Transactional
    public AppointmentResponse confirm(Long id) {
        Appointment appointment = find(id);
        appointment.changeStatus(AppointmentStatus.CONFIRMED);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long id, String reason) {
        Appointment appointment = find(id);
        LocalDateTime deadline = appointment.getStartAt().minus(policy.cancelDeadline());
        if (LocalDateTime.now(clock).isAfter(deadline)) {
            throw new BusinessException("O cancelamento só é permitido até " + policy.cancelDeadline().toHours()
                    + "h antes do horário.");
        }
        appointment.cancel(reason);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long id) {
        return finish(id, AppointmentStatus.COMPLETED);
    }

    @Transactional
    public AppointmentResponse markNoShow(Long id) {
        return finish(id, AppointmentStatus.NO_SHOW);
    }

    /** Concluir ou registrar falta só faz sentido depois que o horário começou. */
    private AppointmentResponse finish(Long id, AppointmentStatus target) {
        Appointment appointment = find(id);
        if (LocalDateTime.now(clock).isBefore(appointment.getStartAt())) {
            throw new BusinessException("O atendimento ainda não começou.");
        }
        appointment.changeStatus(target);
        return AppointmentResponse.from(appointment);
    }

    private void checkAdvance(LocalDateTime start) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (start.isBefore(now.plus(policy.minAdvance()))) {
            throw new BusinessException("Agendamentos precisam de pelo menos " + policy.minAdvance().toMinutes()
                    + " minutos de antecedência.");
        }
        if (start.toLocalDate().isAfter(now.toLocalDate().plusDays(policy.maxAdvanceDays()))) {
            throw new BusinessException("Só é possível agendar com até " + policy.maxAdvanceDays() + " dias de antecedência.");
        }
    }

    private void checkWorkingHours(Professional professional, LocalDateTime start, LocalDateTime end) {
        boolean fits = start.toLocalDate().equals(end.toLocalDate()) && professional.hoursOn(start.getDayOfWeek()).stream()
                .anyMatch(period -> period.contains(start.toLocalTime(), end.toLocalTime()));
        if (!fits) {
            throw new BusinessException(professional.getName() + " não atende nesse horário.");
        }
    }

    private Appointment find(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Agendamento " + id + " não encontrado."));
    }
}
