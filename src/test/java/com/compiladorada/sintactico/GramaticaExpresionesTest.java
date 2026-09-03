package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaExpresionesTest {

    private void expr(String e) throws ParseException {
        new AdaParser(new StringReader(
                "procedure P is begin X := " + e + "; end;")).programa();
    }

    private boolean exprConErrores(String e) {
        AdaParser p = new AdaParser(new StringReader(
                "procedure P is begin X := " + e + "; end;"));
        try {
            p.programa();
        } catch (Exception ignorada) {
            // la recuperación puede rendirse; los errores acumulados siguen valiendo
        }
        return !p.getErroresSintacticos().isEmpty();
    }

    @Test
    void aritmetica_con_precedencia() {
        assertDoesNotThrow(() -> expr("1 + 2 * 3 - 4 / 2"));
        assertDoesNotThrow(() -> expr("(2 ** 3) ** 2"));
        // ** no es asociativo en Ada
        assertTrue(exprConErrores("2 ** 3 ** 2"));
        assertDoesNotThrow(() -> expr("A mod B rem C"));
    }

    @Test
    void logica_y_cortocircuito() {
        assertDoesNotThrow(() -> expr("A and then B or else C"));
        assertDoesNotThrow(() -> expr("not A xor (B and C)"));
    }

    @Test
    void relacionales_y_pertenencia() {
        assertDoesNotThrow(() -> expr("X >= 1 and X <= 10"));
        assertDoesNotThrow(() -> expr("X in 1 .. 100"));
        assertDoesNotThrow(() -> expr("X not in 1 .. 100"));
    }

    @Test
    void concatenacion_llamada_y_campo() {
        assertDoesNotThrow(() -> expr("Nombre & \" \" & Apellido"));
        assertDoesNotThrow(() -> expr("Max(A, B) + Registro.Campo"));
        assertDoesNotThrow(() -> expr("abs (-X)"));
    }

    @Test
    void parentesis_desbalanceado_se_reporta() {
        assertTrue(exprConErrores("(1 + 2"));
    }
}
