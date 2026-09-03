package com.compiladorada.lexico;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecuperacionLexicaTest {

    private List<ErrorCompilacion> errores(String fuente) {
        return new AnalizadorLexico(fuente).errores();
    }

    @Test
    void cadena_sin_cerrar() {
        List<ErrorCompilacion> e = errores("X := \"hola;\nY := 1;");
        assertEquals(1, e.size());
        assertTrue(e.get(0).mensaje().toLowerCase().contains("cadena"));
    }

    @Test
    void identificador_con_doble_guion_bajo() {
        List<ErrorCompilacion> e = errores("mi__variable : Integer;");
        assertEquals(1, e.size());
        assertTrue(e.get(0).mensaje().contains("_"));
    }

    @Test
    void identificador_que_termina_en_guion_bajo() {
        List<ErrorCompilacion> e = errores("contador_ : Integer;");
        assertEquals(1, e.size());
    }

    @Test
    void varios_errores_lexicos_en_la_misma_fuente() {
        List<ErrorCompilacion> e = errores("a__b : Integer; c := $; d_ : Float;");
        assertEquals(3, e.size());
    }

    @Test
    void el_analisis_continua_tras_cada_error() {
        AnalizadorLexico a = new AnalizadorLexico("a__b : Integer;");
        assertTrue(a.tokens().stream()
                .anyMatch(t -> t.tipo() == TipoToken.IDENTIFICADOR && t.lexema().equals("Integer")));
    }

    @Test
    void identificadores_validos_con_guion_bajo_no_son_error() {
        assertTrue(errores("mi_variable : Integer;").isEmpty());
        assertTrue(errores("contador_de_items : Integer;").isEmpty());
        assertTrue(errores("a_1_b_2 : Integer;").isEmpty());
    }
}
