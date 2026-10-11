package com.gabrielpacheco.agendamento.controller;

import com.gabrielpacheco.agendamento.dto.Dtos.Availability;
import com.gabrielpacheco.agendamento.dto.Dtos.ProfessionalRequest;
import com.gabrielpacheco.agendamento.dto.Dtos.ProfessionalResponse;
import com.gabrielpacheco.agendamento.dto.Dtos.ServiceRequest;
import com.gabrielpacheco.agendamento.dto.Dtos.ServiceResponse;
import com.gabrielpacheco.agendamento.dto.Dtos.WorkingHoursUpdate;
import com.gabrielpacheco.agendamento.service.AvailabilityService;
import com.gabrielpacheco.agendamento.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Profissionais e serviços")
public class CatalogController {

    private final CatalogService catalogService;
    private final AvailabilityService availabilityService;

    public CatalogController(CatalogService catalogService, AvailabilityService availabilityService) {
        this.catalogService = catalogService;
        this.availabilityService = availabilityService;
    }

    @PostMapping("/professionals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um profissional")
    public ProfessionalResponse createProfessional(@Valid @RequestBody ProfessionalRequest request) {
        return catalogService.createProfessional(request);
    }

    @GetMapping("/professionals")
    @Operation(summary = "Lista os profissionais")
    public List<ProfessionalResponse> listProfessionals() {
        return catalogService.listProfessionals();
    }

    @GetMapping("/professionals/{id}")
    @Operation(summary = "Busca um profissional com seu expediente")
    public ProfessionalResponse getProfessional(@PathVariable Long id) {
        return catalogService.getProfessional(id);
    }

    @PutMapping("/professionals/{id}/working-hours")
    @Operation(summary = "Define o expediente semanal (substitui o atual)")
    public ProfessionalResponse replaceWorkingHours(@PathVariable Long id, @Valid @RequestBody WorkingHoursUpdate request) {
        return catalogService.replaceWorkingHours(id, request.periods());
    }

    @GetMapping("/professionals/{id}/availability")
    @Operation(summary = "Horários livres do profissional em um dia, para um serviço")
    public Availability availability(@PathVariable Long id,
                                     @RequestParam Long serviceId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availabilityService.availableSlots(id, serviceId, date);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um serviço com duração e preço")
    public ServiceResponse createService(@Valid @RequestBody ServiceRequest request) {
        return catalogService.createService(request);
    }

    @GetMapping("/services")
    @Operation(summary = "Lista os serviços")
    public List<ServiceResponse> listServices() {
        return catalogService.listServices();
    }
}
