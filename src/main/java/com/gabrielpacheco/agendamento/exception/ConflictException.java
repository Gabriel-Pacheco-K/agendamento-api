package com.gabrielpacheco.agendamento.exception;

/**
 * O horário pedido já está ocupado.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
