package com.gabrielpacheco.agendamento.repository;

import com.gabrielpacheco.agendamento.domain.Professional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

    /**
     * Trava a linha do profissional (SELECT ... FOR UPDATE) até o fim da transação.
     * Duas reservas simultâneas para o mesmo profissional passam a ser feitas uma de cada vez,
     * o que impede que ambas vejam o horário como livre e as duas sejam gravadas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Professional p where p.id = :id")
    Optional<Professional> findByIdForUpdate(@Param("id") Long id);
}
