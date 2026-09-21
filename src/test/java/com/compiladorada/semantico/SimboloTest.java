package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimboloTest {

    @Test
    void guarda_sus_datos_tal_cual() {
        Simbolo s = new Simbolo("Contador", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 3, 5);
        assertEquals("Contador", s.nombre());
        assertEquals(Simbolo.Categoria.VARIABLE, s.categoria());
        assertEquals(TipoAda.INTEGER, s.tipo());
        assertFalse(s.esConstante());
        assertEquals(3, s.lineaDeclaracion());
        assertEquals(5, s.columnaDeclaracion());
    }
}
