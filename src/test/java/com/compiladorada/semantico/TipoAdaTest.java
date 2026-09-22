package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TipoAdaTest {

    @Test
    void escalares_predefinidos_tienen_su_nombre() {
        assertEquals("Integer", TipoAda.INTEGER.nombre());
        assertEquals("Float", TipoAda.FLOAT.nombre());
        assertEquals("Boolean", TipoAda.BOOLEAN.nombre());
        assertEquals("Character", TipoAda.CHARACTER.nombre());
        assertEquals("String", TipoAda.STRING.nombre());
    }

    @Test
    void tipos_distintos_no_son_compatibles() {
        assertFalse(TipoAda.INTEGER.compatibleCon(TipoAda.FLOAT));
        assertTrue(TipoAda.INTEGER.compatibleCon(TipoAda.INTEGER));
    }

    @Test
    void desconocido_es_compatible_con_cualquier_cosa_para_no_encadenar_errores() {
        assertTrue(TipoAda.DESCONOCIDO.compatibleCon(TipoAda.INTEGER));
        assertTrue(TipoAda.INTEGER.compatibleCon(TipoAda.DESCONOCIDO));
    }

    @Test
    void subtipo_es_compatible_con_su_tipo_base_en_ambos_sentidos() {
        TipoAda.TipoSubtipo grado = new TipoAda.TipoSubtipo("Grado", TipoAda.INTEGER);
        assertTrue(grado.compatibleCon(TipoAda.INTEGER));
        assertTrue(TipoAda.INTEGER.compatibleCon(grado));
    }

    @Test
    void arreglo_y_registro_se_comparan_estructuralmente() {
        TipoAda.TipoArreglo a1 = new TipoAda.TipoArreglo("Vector", TipoAda.INTEGER);
        TipoAda.TipoArreglo a2 = new TipoAda.TipoArreglo("Vector", TipoAda.INTEGER);
        assertTrue(a1.compatibleCon(a2));

        TipoAda.TipoRegistro r1 = new TipoAda.TipoRegistro("Punto",
                Map.of("x", TipoAda.INTEGER, "y", TipoAda.INTEGER));
        TipoAda.TipoRegistro r2 = new TipoAda.TipoRegistro("Punto",
                Map.of("x", TipoAda.INTEGER, "y", TipoAda.INTEGER));
        assertTrue(r1.compatibleCon(r2));
    }

    @Test
    void enumerado_guarda_sus_literales_en_orden() {
        TipoAda.TipoEnumerado color = new TipoAda.TipoEnumerado("Color", List.of("Rojo", "Verde", "Azul"));
        assertEquals(List.of("Rojo", "Verde", "Azul"), color.literales());
    }

    @Test
    void subprograma_guarda_parametros_y_retorno() {
        TipoAda.TipoSubprograma f = new TipoAda.TipoSubprograma(
                "Suma", List.of(TipoAda.INTEGER, TipoAda.INTEGER), TipoAda.INTEGER);
        assertEquals(2, f.parametros().size());
        assertEquals(TipoAda.INTEGER, f.retorno());
    }

    @Test
    void literal_entero_es_compatible_con_integer_y_con_cualquier_rango() {
        TipoAda.TipoRango nota = new TipoAda.TipoRango("Nota");
        assertTrue(TipoAda.INTEGER.compatibleCon(TipoAda.LITERAL_ENTERO));
        assertTrue(TipoAda.LITERAL_ENTERO.compatibleCon(TipoAda.INTEGER));
        assertTrue(nota.compatibleCon(TipoAda.LITERAL_ENTERO));
        assertTrue(TipoAda.LITERAL_ENTERO.compatibleCon(nota));
    }

    @Test
    void literal_entero_es_compatible_con_un_subtipo_de_base_entera() {
        TipoAda.TipoSubtipo indice = new TipoAda.TipoSubtipo("Indice", TipoAda.INTEGER);
        assertTrue(indice.compatibleCon(TipoAda.LITERAL_ENTERO));
        assertTrue(TipoAda.LITERAL_ENTERO.compatibleCon(indice));
    }

    @Test
    void literal_real_es_compatible_con_float_y_con_un_subtipo_de_base_real() {
        TipoAda.TipoSubtipo precio = new TipoAda.TipoSubtipo("Precio", TipoAda.FLOAT);
        assertTrue(TipoAda.FLOAT.compatibleCon(TipoAda.LITERAL_REAL));
        assertTrue(TipoAda.LITERAL_REAL.compatibleCon(TipoAda.FLOAT));
        assertTrue(precio.compatibleCon(TipoAda.LITERAL_REAL));
    }

    @Test
    void literal_entero_no_es_compatible_con_float_ni_literal_real_con_integer() {
        assertFalse(TipoAda.FLOAT.compatibleCon(TipoAda.LITERAL_ENTERO));
        assertFalse(TipoAda.LITERAL_ENTERO.compatibleCon(TipoAda.FLOAT));
        assertFalse(TipoAda.INTEGER.compatibleCon(TipoAda.LITERAL_REAL));
        assertFalse(TipoAda.LITERAL_REAL.compatibleCon(TipoAda.INTEGER));
    }

    @Test
    void literal_entero_no_es_compatible_con_boolean_ni_registro() {
        assertFalse(TipoAda.BOOLEAN.compatibleCon(TipoAda.LITERAL_ENTERO));
        TipoAda.TipoRegistro punto = new TipoAda.TipoRegistro("Punto", Map.of("x", TipoAda.INTEGER));
        assertFalse(punto.compatibleCon(TipoAda.LITERAL_ENTERO));
    }

    @Test
    void dos_literales_de_la_misma_familia_son_compatibles_entre_si() {
        assertTrue(TipoAda.LITERAL_ENTERO.compatibleCon(TipoAda.LITERAL_ENTERO));
        assertTrue(TipoAda.LITERAL_REAL.compatibleCon(TipoAda.LITERAL_REAL));
        assertFalse(TipoAda.LITERAL_ENTERO.compatibleCon(TipoAda.LITERAL_REAL));
    }
}
