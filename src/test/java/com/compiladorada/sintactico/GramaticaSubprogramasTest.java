package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaSubprogramasTest {

    private boolean tieneErrores(String fuente) {
        AdaParser p = new AdaParser(new StringReader(fuente));
        try {
            p.programa();
        } catch (Exception ignorada) {
            // la recuperación puede rendirse; los errores acumulados siguen valiendo
        }
        return !p.getErroresSintacticos().isEmpty();
    }

    @Test
    void procedimiento_minimo() {
        assertFalse(tieneErrores("procedure Vacio is begin null; end;"),
                "procedimiento mínimo debería parsear sin errores");
    }

    @Test
    void procedimiento_con_end_nombrado() {
        assertFalse(tieneErrores("procedure Saludo is begin null; end Saludo;"),
                "procedimiento con end nombrado debería parsear sin errores");
    }

    @Test
    void procedimiento_con_parametros_y_modos() {
        assertFalse(tieneErrores(
                "procedure P (A : in Integer; B : out Integer; C : in out Float) "
              + "is begin null; end P;"),
                "procedimiento con parámetros y modos debería parsear sin errores");
    }

    @Test
    void funcion_con_retorno_y_declaraciones() {
        assertFalse(tieneErrores(
                "function Doble (X : Integer) return Integer is "
              + "  R : Integer; K : constant Integer := 2; "
              + "begin R := X; end Doble;"),
                "función con retorno y declaraciones debería parsear sin errores");
    }

    @Test
    void declaracion_de_varias_variables_en_una_linea() {
        assertFalse(tieneErrores(
                "procedure P is A, B, C : Integer; begin null; end;"),
                "declaración de varias variables debería parsear sin errores");
    }

    @Test
    void falta_punto_y_coma_se_reporta_como_error() {
        assertTrue(tieneErrores("procedure P is begin null end;"));
    }
}
