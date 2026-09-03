package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaTiposTest {

    private void parsear(String fuente) throws ParseException {
        new AdaParser(new StringReader(fuente)).programa();
    }

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
    void tipo_rango_y_subtipo() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is type Grado is range 0 .. 100; "
              + "subtype Positivo is Integer range 1 .. 100; begin null; end;"));
    }

    @Test
    void tipo_enumerado() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is type Color is (Rojo, Verde, Azul); begin null; end;"));
    }

    @Test
    void tipo_registro_y_arreglo() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is "
              + "  type Punto is record X : Integer; Y : Integer; end record; "
              + "  type Vec is array (1 .. 10) of Integer; "
              + "begin null; end;"));
    }

    @Test
    void paquete_spec_con_private() {
        assertDoesNotThrow(() -> parsear(
                "package Pila is X : Integer; private Y : Integer; end Pila;"));
    }

    @Test
    void paquete_body_con_begin() {
        assertDoesNotThrow(() -> parsear(
                "package body Pila is Z : Integer; begin Z := 0; end Pila;"));
    }

    @Test
    void bloque_de_excepciones_con_varios_manejadores() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is begin null; "
              + "exception "
              + "  when Constraint_Error | Program_Error => null; "
              + "  when others => raise; "
              + "end;"));
    }

    @Test
    void record_sin_end_record_se_reporta() {
        assertTrue(tieneErrores(
                "procedure P is type R is record X : Integer; begin null; end;"));
    }
}
