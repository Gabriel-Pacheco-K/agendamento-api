package com.gabrielpacheco.agendamento.repository;

import com.gabrielpacheco.agendamento.domain.Appointment;
import com.gabrielpacheco.agendamento.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Dois intervalos [a, b) e [c, d) se sobrepõem quando a < d e c < b.
     * Um horário que termina às 10:00 não conflita com outro que começa às 10:00.
     */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.professional.id = :professionalId
              and a.status in :statuses
              and a.startAt < :end and a.endAt > :start
            """)
    boolean existsOverlap(@Param("professionalId") Long professionalId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("""
            select a from Appointment a
            join fetch a.service
            where a.professional.id = :professionalId
              and a.startAt < :end and a.endAt > :start
              and a.status in :statuses
            order by a.startAt
            """)
    List<Appointment> findInRange(@Param("professionalId") Long professionalId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("statuses") Collection<AppointmentStatus> statuses);
}
