package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbitoTest {

    @Test
    void declara_y_resuelve_en_el_mismo_ambito() {
        Ambito a = new Ambito(null);
        assertTrue(a.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1)));
        assertNotNull(a.resolver("X"));
        assertEquals(TipoAda.INTEGER, a.resolver("X").tipo());
    }

    @Test
    void la_busqueda_no_distingue_mayusculas() {
        Ambito a = new Ambito(null);
        a.declarar(new Simbolo("Contador", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        assertNotNull(a.resolver("CONTADOR"));
        assertNotNull(a.resolver("contador"));
    }

    @Test
    void redeclarar_en_el_mismo_ambito_devuelve_false() {
        Ambito a = new Ambito(null);
        a.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        assertFalse(a.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.FLOAT, false, 2, 1)));
    }

    @Test
    void resuelve_hacia_afuera_por_los_ambitos_envolventes() {
        Ambito global = new Ambito(null);
        global.declarar(new Simbolo("Global", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        Ambito interno = new Ambito(global);
        assertNotNull(interno.resolver("Global"));
    }

    @Test
    void un_ambito_interno_puede_sombrear_al_externo() {
        Ambito global = new Ambito(null);
        global.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        Ambito interno = new Ambito(global);
        assertTrue(interno.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.FLOAT, false, 2, 1)));
        assertEquals(TipoAda.FLOAT, interno.resolver("X").tipo());
        assertEquals(TipoAda.INTEGER, global.resolver("X").tipo());
    }

    @Test
    void resolver_nombre_no_declarado_devuelve_null() {
        Ambito a = new Ambito(null);
        assertNull(a.resolver("Fantasma"));
    }
}
