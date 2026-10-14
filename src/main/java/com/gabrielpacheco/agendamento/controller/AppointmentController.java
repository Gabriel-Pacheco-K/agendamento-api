package com.gabrielpacheco.agendamento.controller;

import com.gabrielpacheco.agendamento.dto.Dtos.AppointmentRequest;
import com.gabrielpacheco.agendamento.dto.Dtos.AppointmentResponse;
import com.gabrielpacheco.agendamento.dto.Dtos.DailyRevenue;
import com.gabrielpacheco.agendamento.dto.Dtos.CancelRequest;
import com.gabrielpacheco.agendamento.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Agendamentos")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agenda um horário (409 se já estiver ocupado)")
    public AppointmentResponse book(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.book(request);
    }

    @GetMapping
    @Operation(summary = "Agenda do profissional em um dia")
    public List<AppointmentResponse> agenda(@RequestParam Long professionalId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.agenda(professionalId, date);
    }

    @GetMapping("/revenue")
    @Operation(summary = "Faturamento por dia (só concluídos), de todos os profissionais, até 366 dias")
    public List<DailyRevenue> revenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointmentService.revenue(from, to);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um agendamento")
    public AppointmentResponse get(@PathVariable Long id) {
        return appointmentService.get(id);
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirma o agendamento")
    public AppointmentResponse confirm(@PathVariable Long id) {
        return appointmentService.confirm(id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancela o agendamento, respeitando o prazo mínimo")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request) {
        return appointmentService.cancel(id, request == null ? null : request.reason());
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Marca o atendimento como concluído")
    public AppointmentResponse complete(@PathVariable Long id) {
        return appointmentService.complete(id);
    }

    @PatchMapping("/{id}/no-show")
    @Operation(summary = "Registra que o cliente faltou")
    public AppointmentResponse noShow(@PathVariable Long id) {
        return appointmentService.markNoShow(id);
    }
}
