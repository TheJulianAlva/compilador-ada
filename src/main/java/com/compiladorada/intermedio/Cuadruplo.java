package com.compiladorada.intermedio;

/** Instrucción de tres direcciones: {@code (op, arg1, arg2, resultado)}. */
public record Cuadruplo(String op, String arg1, String arg2, String resultado) {

    @Override
    public String toString() {
        return "(" + op + ", " + vacio(arg1) + ", " + vacio(arg2) + ", " + vacio(resultado) + ")";
    }

    private static String vacio(String s) {
        return s == null || s.isEmpty() ? "_" : s;
    }
}
