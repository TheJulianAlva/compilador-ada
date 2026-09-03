package com.compiladorada.lexico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PalabrasReservadasTest {

    @Test
    void hay_73_palabras_reservadas() {
        assertEquals(73, PalabrasReservadas.TODAS.size());
    }

    @Test
    void reconoce_reservadas_sin_importar_mayusculas() {
        assertTrue(PalabrasReservadas.esReservada("procedure"));
        assertTrue(PalabrasReservadas.esReservada("PROCEDURE"));
        assertTrue(PalabrasReservadas.esReservada("Procedure"));
        assertTrue(PalabrasReservadas.esReservada("end"));
    }

    @Test
    void tipos_predefinidos_no_son_reservados() {
        assertFalse(PalabrasReservadas.esReservada("Integer"));
        assertFalse(PalabrasReservadas.esReservada("Float"));
        assertFalse(PalabrasReservadas.esReservada("Boolean"));
        assertFalse(PalabrasReservadas.esReservada("Character"));
        assertFalse(PalabrasReservadas.esReservada("String"));
    }

    @Test
    void identificador_comun_no_es_reservado() {
        assertFalse(PalabrasReservadas.esReservada("Contador"));
    }
}
