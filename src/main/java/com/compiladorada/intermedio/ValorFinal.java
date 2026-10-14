package com.compiladorada.intermedio;

/** Estado de una variable al terminar el programa; {@code valor == null} si no tiene valor ({@code motivo} lo explica). */
public record ValorFinal(String nombre, String tipo, String valor, String motivo) {

    public String texto() {
        return valor != null ? valor : "(sin valor: " + motivo + ")";
    }
}
