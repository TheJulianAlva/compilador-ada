package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.lexico.TokenLexico;

import java.util.List;

public record ResultadoCompilacion(
        List<TokenLexico> tokens,
        List<ErrorCompilacion> erroresLexicos,
        List<ErrorCompilacion> erroresSintacticos,
        Object ast) {

    public boolean tieneErrores() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty();
    }
}
