package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.lexico.TokenLexico;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.util.List;

public record ResultadoCompilacion(
        List<TokenLexico> tokens,
        List<ErrorCompilacion> erroresLexicos,
        List<ErrorCompilacion> erroresSintacticos,
        SimpleNode ast) {

    public boolean tieneErrores() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty();
    }

    /**
     * {@code true} si el análisis sintáctico NO se ejecutó por haber errores
     * léxicos. Las fases son secuenciales: el parser solo corre cuando la fase
     * léxica está limpia. Cuando es {@code true}, {@link #erroresSintacticos()}
     * está vacía y {@link #ast()} es {@code null} porque no se analizó, no
     * porque el programa sea sintácticamente correcto.
     */
    public boolean sintacticoOmitido() {
        return !erroresLexicos.isEmpty();
    }
}
