package com.compiladorada.intermedio;

/** Valor numérico de una evaluación: Integer (long, acotado a 32 bits por el
 * {@link Evaluador}) o Float (double). */
public final class Valor {

    private final boolean real;
    private final long entero;
    private final double flotante;

    private Valor(boolean real, long entero, double flotante) {
        this.real = real;
        this.entero = entero;
        this.flotante = flotante;
    }

    public static Valor entero(long v) {
        return new Valor(false, v, 0);
    }

    public static Valor real(double v) {
        return new Valor(true, 0, v);
    }

    public boolean esReal() {
        return real;
    }

    public long entero() {
        return entero;
    }

    public double real() {
        return flotante;
    }

    public String tipo() {
        return real ? "Float" : "Integer";
    }

    @Override
    public String toString() {
        return real ? Double.toString(flotante) : Long.toString(entero);
    }
}
