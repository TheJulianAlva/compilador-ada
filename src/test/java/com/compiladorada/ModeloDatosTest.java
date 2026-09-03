package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModeloDatosTest {

    @Test
    void token_lexico_guarda_sus_campos() {
        TokenLexico t = new TokenLexico("Hola", TipoToken.IDENTIFICADOR, 3, 11);
        assertEquals("Hola", t.lexema());
        assertEquals(TipoToken.IDENTIFICADOR, t.tipo());
        assertEquals(3, t.linea());
        assertEquals(11, t.columna());
    }

    @Test
    void error_lexico_se_formatea_estilo_gcc() {
        ErrorCompilacion e = new ErrorCompilacion(Categoria.LEXICO, 12, 8, "carácter no válido '$'");
        assertEquals("p.ada:12:8: error léxico: carácter no válido '$'", e.formatear("p.ada"));
    }

    @Test
    void error_sintactico_usa_su_etiqueta() {
        ErrorCompilacion e = new ErrorCompilacion(Categoria.SINTACTICO, 20, 1, "se esperaba ';'");
        assertEquals("p.ada:20:1: error sintáctico: se esperaba ';'", e.formatear("p.ada"));
    }

    @Test
    void resultado_sin_errores_no_tiene_errores() {
        ResultadoCompilacion r = new ResultadoCompilacion(List.of(), List.of(), List.of(), null);
        assertFalse(r.tieneErrores());
    }

    @Test
    void resultado_con_error_sintactico_tiene_errores() {
        ErrorCompilacion e = new ErrorCompilacion(Categoria.SINTACTICO, 1, 1, "x");
        ResultadoCompilacion r = new ResultadoCompilacion(List.of(), List.of(), List.of(e), null);
        assertTrue(r.tieneErrores());
    }
}
