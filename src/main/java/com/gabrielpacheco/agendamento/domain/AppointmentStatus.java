package com.gabrielpacheco.agendamento.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de um agendamento. As transições permitidas ficam declaradas
 * aqui, em um único lugar, em vez de espalhadas em vários if/else.
 *
 * <pre>
 * SCHEDULED ──▶ CONFIRMED
 *     │             │
 *     └──────┬──────┘
 *            ├──▶ COMPLETED
 *            ├──▶ NO_SHOW
 *            └──▶ CANCELLED
 *
 * Confirmar é opcional: quem marcou pelo WhatsApp e apareceu pode ir direto para concluído.
 * </pre>
 */
public enum AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** Status que ocupam o horário na agenda do profissional. */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(SCHEDULED, CONFIRMED);

    public boolean canChangeTo(AppointmentStatus target) {
        return switch (this) {
            case SCHEDULED -> target != SCHEDULED;
            case CONFIRMED -> target == COMPLETED || target == NO_SHOW || target == CANCELLED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
