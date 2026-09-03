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

    @Test
    void aritmetica_con_precedencia() {
        assertDoesNotThrow(() -> expr("1 + 2 * 3 - 4 / 2"));
        assertDoesNotThrow(() -> expr("2 ** 3 ** 2"));
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
    void parentesis_desbalanceado_lanza() {
        assertThrows(ParseException.class, () -> expr("(1 + 2"));
    }
}
