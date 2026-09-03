package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaSubprogramasTest {

    private void parsear(String fuente) throws ParseException {
        new AdaParser(new StringReader(fuente)).programa();
    }

    @Test
    void procedimiento_minimo() {
        assertDoesNotThrow(() -> parsear("procedure Vacio is begin null; end;"));
    }

    @Test
    void procedimiento_con_end_nombrado() {
        assertDoesNotThrow(() -> parsear("procedure Saludo is begin null; end Saludo;"));
    }

    @Test
    void procedimiento_con_parametros_y_modos() {
        assertDoesNotThrow(() -> parsear(
                "procedure P (A : in Integer; B : out Integer; C : in out Float) "
              + "is begin null; end P;"));
    }

    @Test
    void funcion_con_retorno_y_declaraciones() {
        assertDoesNotThrow(() -> parsear(
                "function Doble (X : Integer) return Integer is "
              + "  R : Integer; K : constant Integer := 2; "
              + "begin R := X; end Doble;"));
    }

    @Test
    void declaracion_de_varias_variables_en_una_linea() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is A, B, C : Integer; begin null; end;"));
    }

    @Test
    void falta_punto_y_coma_lanza_ParseException() {
        assertThrows(ParseException.class, () -> parsear(
                "procedure P is begin null end;"));
    }
}
