package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.intermedio.ResultadoIntermedio;
import com.compiladorada.lexico.TokenLexico;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.util.List;

public record ResultadoCompilacion(
        List<TokenLexico> tokens,
        List<ErrorCompilacion> erroresLexicos,
        List<ErrorCompilacion> erroresSintacticos,
        List<ErrorCompilacion> erroresSemanticos,
        SimpleNode ast,
        ResultadoIntermedio intermedio) {

    /** Resultado sin fase de código intermedio (equivale a {@code intermedio == null}). */
    public ResultadoCompilacion(List<TokenLexico> tokens, List<ErrorCompilacion> erroresLexicos,
                                List<ErrorCompilacion> erroresSintacticos,
                                List<ErrorCompilacion> erroresSemanticos, SimpleNode ast) {
        this(tokens, erroresLexicos, erroresSintacticos, erroresSemanticos, ast, null);
    }

    public boolean tieneErrores() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty() || !erroresSemanticos.isEmpty();
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

    /**
     * {@code true} si el análisis semántico NO se ejecutó por haber errores
     * léxicos o sintácticos. Igual que {@link #sintacticoOmitido()}, las
     * fases son secuenciales.
     */
    public boolean semanticoOmitido() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty();
    }

    /**
     * {@code true} si la generación de código intermedio NO se ejecutó por
     * haber errores léxicos, sintácticos o semánticos. Las fases son
     * secuenciales; en ese caso {@link #intermedio()} es {@code null}.
     */
    public boolean intermedioOmitido() {
        return tieneErrores();
    }
}
