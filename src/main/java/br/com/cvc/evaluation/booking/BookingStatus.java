package br.com.cvc.evaluation.booking;

/**
 * Ciclo de vida da reserva. As transições permitidas são aplicadas no domínio (M1.5);
 * o banco guarda o estado como texto, para que a evolução do enum não exija migração.
 */
public enum BookingStatus {
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW
}
