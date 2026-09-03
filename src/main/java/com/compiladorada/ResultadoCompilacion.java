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
}
