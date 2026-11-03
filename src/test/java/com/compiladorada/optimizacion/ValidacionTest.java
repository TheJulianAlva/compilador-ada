package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ResultadoIntermedio;
import com.compiladorada.intermedio.ValorFinal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidacionTest {

    private static ResultadoIntermedio con(ValorFinal... valores) {
        return new ResultadoIntermedio(List.of(), List.of(valores), List.of());
    }

    @Test
    void valores_iguales_coinciden_aunque_el_motivo_cite_otra_linea() {
        Validacion v = Validacion.comparar(
                con(new ValorFinal("A", "Integer", "7", null),
                    new ValorFinal("B", "Integer", null, "falló la expresión de la línea 12")),
                con(new ValorFinal("A", "Integer", "7", null),
                    new ValorFinal("B", "Integer", null, "falló la expresión de la línea 4")));
        assertTrue(v.coincide());
        assertTrue(v.texto().contains("coinciden"), v.texto());
    }

    @Test
    void valores_distintos_se_listan() {
        Validacion v = Validacion.comparar(
                con(new ValorFinal("A", "Integer", "7", null), new ValorFinal("B", "Float", "2.5", null)),
                con(new ValorFinal("A", "Integer", "8", null), new ValorFinal("B", "Float", null, "x")));
        assertFalse(v.coincide());
        assertEquals(2, v.diferencias().size());
        assertTrue(v.texto().contains("A : Integer = 7 (original) frente a A : Integer = 8 (optimizado)"), v.texto());
        assertTrue(v.texto().contains("NO coinciden"), v.texto());
    }

    @Test
    void distinto_numero_de_variables_es_una_diferencia() {
        Validacion v = Validacion.comparar(con(new ValorFinal("A", "Integer", "1", null)), con());
        assertFalse(v.coincide());
    }
}
