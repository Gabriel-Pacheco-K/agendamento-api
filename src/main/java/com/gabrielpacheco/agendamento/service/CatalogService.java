package com.gabrielpacheco.agendamento.service;

import com.gabrielpacheco.agendamento.domain.Professional;
import com.gabrielpacheco.agendamento.domain.ServiceType;
import com.gabrielpacheco.agendamento.domain.WorkingHours;
import com.gabrielpacheco.agendamento.dto.Dtos.ProfessionalRequest;
import com.gabrielpacheco.agendamento.dto.Dtos.ProfessionalResponse;
import com.gabrielpacheco.agendamento.dto.Dtos.ServiceRequest;
import com.gabrielpacheco.agendamento.dto.Dtos.ServiceResponse;
import com.gabrielpacheco.agendamento.dto.Dtos.WorkingHoursRequest;
import com.gabrielpacheco.agendamento.exception.BusinessException;
import com.gabrielpacheco.agendamento.exception.NotFoundException;
import com.gabrielpacheco.agendamento.repository.ProfessionalRepository;
import com.gabrielpacheco.agendamento.repository.ServiceTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Cadastro de profissionais, expedientes e serviços.
 */
@Service
public class CatalogService {

    private final ProfessionalRepository professionalRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public CatalogService(ProfessionalRepository professionalRepository, ServiceTypeRepository serviceTypeRepository) {
        this.professionalRepository = professionalRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    @Transactional
    public ProfessionalResponse createProfessional(ProfessionalRequest request) {
        Professional professional = new Professional(request.name().trim(), request.specialty());
        return ProfessionalResponse.from(professionalRepository.save(professional));
    }

    @Transactional(readOnly = true)
    public List<ProfessionalResponse> listProfessionals() {
        return professionalRepository.findAll().stream().map(ProfessionalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProfessionalResponse getProfessional(Long id) {
        return ProfessionalResponse.from(findProfessional(id));
    }

    /**
     * Substitui o expediente inteiro do profissional. Recusa períodos invertidos
     * e períodos que se sobrepõem no mesmo dia.
     */
    @Transactional
    public ProfessionalResponse replaceWorkingHours(Long professionalId, List<WorkingHoursRequest> periods) {
        Professional professional = findProfessional(professionalId);
        validate(periods);

        professional.replaceWorkingHours(periods.stream()
                .map(p -> new WorkingHours(professional, p.dayOfWeek(), p.start(), p.end()))
                .toList());
        return ProfessionalResponse.from(professional);
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        ServiceType service = new ServiceType(request.name().trim(), request.durationMinutes(), request.price());
        return ServiceResponse.from(serviceTypeRepository.save(service));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices() {
        return serviceTypeRepository.findAll().stream().map(ServiceResponse::from).toList();
    }

    Professional findProfessional(Long id) {
        return professionalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Profissional " + id + " não encontrado."));
    }

    ServiceType findService(Long id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Serviço " + id + " não encontrado."));
    }

    static void validate(List<WorkingHoursRequest> periods) {
        for (WorkingHoursRequest period : periods) {
            if (!period.start().isBefore(period.end())) {
                throw new BusinessException("Em " + period.dayOfWeek() + ", o início (" + period.start()
                        + ") deve ser antes do fim (" + period.end() + ").");
            }
        }

        List<WorkingHoursRequest> sorted = periods.stream()
                .sorted(Comparator.comparing(WorkingHoursRequest::dayOfWeek).thenComparing(WorkingHoursRequest::start))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            WorkingHoursRequest previous = sorted.get(i - 1);
            WorkingHoursRequest current = sorted.get(i);
            if (previous.dayOfWeek() == current.dayOfWeek() && current.start().isBefore(previous.end())) {
                throw new BusinessException("Em " + current.dayOfWeek() + ", os períodos "
                        + previous.start() + "–" + previous.end() + " e " + current.start() + "–" + current.end() + " se sobrepõem.");
            }
        }
    }
}
