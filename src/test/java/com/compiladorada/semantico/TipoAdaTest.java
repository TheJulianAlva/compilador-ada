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
}
