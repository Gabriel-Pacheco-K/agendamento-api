package com.gabrielpacheco.agendamento.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "professionals")
public class Professional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String specialty;

    @OneToMany(mappedBy = "professional", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek, startTime")
    private List<WorkingHours> workingHours = new ArrayList<>();

    protected Professional() {
    }

    public Professional(String name, String specialty) {
        this.name = name;
        this.specialty = specialty;
    }

    public void replaceWorkingHours(List<WorkingHours> newHours) {
        workingHours.clear();
        workingHours.addAll(newHours);
    }

    public List<WorkingHours> hoursOn(DayOfWeek day) {
        return workingHours.stream().filter(hours -> hours.getDayOfWeek() == day).toList();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public List<WorkingHours> getWorkingHours() {
        return workingHours;
    }
}
