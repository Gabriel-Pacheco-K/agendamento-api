package com.gabrielpacheco.agendamento.dto;

import com.gabrielpacheco.agendamento.domain.Appointment;
import com.gabrielpacheco.agendamento.domain.AppointmentStatus;
import com.gabrielpacheco.agendamento.domain.Professional;
import com.gabrielpacheco.agendamento.domain.ServiceType;
import com.gabrielpacheco.agendamento.domain.WorkingHours;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    // ─── Profissionais ──────────────────────────────────────────

    public record ProfessionalRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String specialty) {
    }

    public record WorkingHoursRequest(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime start,
            @NotNull LocalTime end) {
    }

    public record WorkingHoursUpdate(@NotNull @Valid List<WorkingHoursRequest> periods) {
    }

    public record WorkingHoursResponse(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {

        public static WorkingHoursResponse from(WorkingHours hours) {
            return new WorkingHoursResponse(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime());
        }
    }

    public record ProfessionalResponse(Long id, String name, String specialty, List<WorkingHoursResponse> workingHours) {

        public static ProfessionalResponse from(Professional professional) {
            return new ProfessionalResponse(professional.getId(), professional.getName(), professional.getSpecialty(),
                    professional.getWorkingHours().stream().map(WorkingHoursResponse::from).toList());
        }
    }

    // ─── Serviços ───────────────────────────────────────────────

    public record ServiceRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(5) @Max(480) Integer durationMinutes,
            @NotNull @DecimalMin("0.00") BigDecimal price) {
    }

    public record ServiceResponse(Long id, String name, int durationMinutes, BigDecimal price) {

        public static ServiceResponse from(ServiceType service) {
            return new ServiceResponse(service.getId(), service.getName(), service.getDurationMinutes(), service.getPrice());
        }
    }

    // ─── Agendamentos ───────────────────────────────────────────

    public record AppointmentRequest(
            @NotNull Long professionalId,
            @NotNull Long serviceId,
            @NotNull LocalDateTime startAt,
            @NotBlank @Size(max = 100) String customerName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{8,20}$", message = "telefone inválido") String customerPhone) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record AppointmentResponse(
            Long id,
            Long professionalId,
            String professionalName,
            String service,
            BigDecimal price,
            String customerName,
            String customerPhone,
            LocalDateTime startAt,
            LocalDateTime endAt,
            AppointmentStatus status,
            String cancellationReason) {

        public static AppointmentResponse from(Appointment appointment) {
            return new AppointmentResponse(
                    appointment.getId(),
                    appointment.getProfessional().getId(),
                    appointment.getProfessional().getName(),
                    appointment.getService().getName(),
                    appointment.getService().getPrice(),
                    appointment.getCustomerName(),
                    appointment.getCustomerPhone(),
                    appointment.getStartAt(),
                    appointment.getEndAt(),
                    appointment.getStatus(),
                    appointment.getCancellationReason());
        }
    }

    public record Availability(Long professionalId, Long serviceId, LocalDate date, int durationMinutes, List<LocalTime> slots) {
    }
}
