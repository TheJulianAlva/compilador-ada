package com.compiladorada.intermedio;

import java.util.List;

/**
 * Una expresión procesada. Si la evaluación falló, {@code valor} y
 * {@code tipo} son {@code null} y {@code error} explica por qué; las
 * notaciones y los cuádruplos se generan igual.
 */
public record FilaIntermedia(
        int linea,
        String fuente,
        String tipo,
        String prefijo,
        String posfijo,
        List<Cuadruplo> cuadruplos,
        String valor,
        String error) {

    /** Texto para la columna "Resultado": el valor o el error. */
    public String resultado() {
        return error != null ? "ERROR: " + error : valor;
    }
}
