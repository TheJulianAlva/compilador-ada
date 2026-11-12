package com.compiladorada.ide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GestorTemaTest {

    @Test
    void alternar_el_tema_actualiza_el_estado() {
        GestorTema.aplicar(GestorTema.Tema.OSCURO);
        assertEquals(GestorTema.Tema.OSCURO, GestorTema.getActual());
        GestorTema.aplicar(GestorTema.Tema.CLARO);
        assertEquals(GestorTema.Tema.CLARO, GestorTema.getActual());
    }
}
