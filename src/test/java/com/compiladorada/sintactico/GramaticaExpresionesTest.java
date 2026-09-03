package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaExpresionesTest {

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
        assertFalse(exprConErrores("1 + 2 * 3 - 4 / 2"), "aritmética debería parsear sin errores");
        assertFalse(exprConErrores("(2 ** 3) ** 2"), "potencia entre paréntesis debería parsear sin errores");
        // ** no es asociativo en Ada
        assertTrue(exprConErrores("2 ** 3 ** 2"));
        assertFalse(exprConErrores("A mod B rem C"), "mod/rem debería parsear sin errores");
    }

    @Test
    void logica_y_cortocircuito() {
        assertFalse(exprConErrores("A and then B or else C"), "cortocircuito debería parsear sin errores");
        assertFalse(exprConErrores("not A xor (B and C)"), "lógica debería parsear sin errores");
    }

    @Test
    void relacionales_y_pertenencia() {
        assertFalse(exprConErrores("X >= 1 and X <= 10"), "relacionales deberían parsear sin errores");
        assertFalse(exprConErrores("X in 1 .. 100"), "pertenencia debería parsear sin errores");
        assertFalse(exprConErrores("X not in 1 .. 100"), "no pertenencia debería parsear sin errores");
    }

    @Test
    void concatenacion_llamada_y_campo() {
        assertFalse(exprConErrores("Nombre & \" \" & Apellido"), "concatenación debería parsear sin errores");
        assertFalse(exprConErrores("Max(A, B) + Registro.Campo"), "llamada y campo deberían parsear sin errores");
        assertFalse(exprConErrores("abs (-X)"), "abs debería parsear sin errores");
    }

    @Test
    void parentesis_desbalanceado_se_reporta() {
        assertTrue(exprConErrores("(1 + 2"));
    }
}
