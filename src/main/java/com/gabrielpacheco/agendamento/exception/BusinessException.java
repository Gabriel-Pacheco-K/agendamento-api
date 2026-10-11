package com.gabrielpacheco.agendamento.exception;

/**
 * Violação de uma regra de negócio, como horário fora do expediente ou cancelamento fora do prazo.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
