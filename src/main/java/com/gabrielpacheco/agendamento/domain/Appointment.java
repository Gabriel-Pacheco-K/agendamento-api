package com.gabrielpacheco.agendamento.domain;

import com.gabrielpacheco.agendamento.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id")
    private Professional professional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ServiceType service;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    protected Appointment() {
    }

    public Appointment(Professional professional, ServiceType service, String customerName,
                       String customerPhone, LocalDateTime startAt) {
        this.professional = professional;
        this.service = service;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.startAt = startAt;
        this.endAt = startAt.plus(service.getDuration());
    }

    public void changeStatus(AppointmentStatus target) {
        if (!status.canChangeTo(target)) {
            throw new BusinessException("Não é possível mudar o agendamento de " + status + " para " + target + ".");
        }
        this.status = target;
    }

    public void cancel(String reason) {
        changeStatus(AppointmentStatus.CANCELLED);
        this.cancellationReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Professional getProfessional() {
        return professional;
    }

    public ServiceType getService() {
        return service;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
