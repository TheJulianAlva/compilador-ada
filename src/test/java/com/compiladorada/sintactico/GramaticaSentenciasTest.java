package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaSentenciasTest {

    private boolean tieneErrores(String cuerpo) {
        String fuente = "procedure P is begin " + cuerpo + " end;";
        AdaParser p = new AdaParser(new StringReader(fuente));
        try {
            p.programa();
        } catch (Exception ignorada) {
            // la recuperación puede rendirse; los errores acumulados siguen valiendo
        }
        return !p.getErroresSintacticos().isEmpty();
    }

    @Test
    void asignacion_y_llamada() {
        assertFalse(tieneErrores("X := 1; Poner(X); Iniciar;"),
                "asignación y llamada deberían parsear sin errores");
    }

    @Test
    void asignacion_a_elemento_de_arreglo_y_campo_de_registro() {
        assertFalse(tieneErrores("A(1) := 0; R.C := 0;"),
                "asignación a A(1) y a R.C deberían parsear sin errores");
        assertFalse(tieneErrores("Ada.Text_IO.Put_Line(\"x\");"),
                "llamada calificada con argumentos debería parsear sin errores");
    }

    @Test
    void if_elsif_else() {
        assertFalse(tieneErrores(
                "if X = 1 then Y := 1; elsif X = 2 then Y := 2; else Y := 0; end if;"),
                "if/elsif/else debería parsear sin errores");
    }

    @Test
    void for_con_reverse() {
        assertFalse(tieneErrores(
                "for I in reverse 1 .. 10 loop Sumar(I); end loop;"),
                "for con reverse debería parsear sin errores");
    }

    @Test
    void while_loop() {
        assertFalse(tieneErrores("while X < 10 loop X := X + 1; end loop;"),
                "while loop debería parsear sin errores");
    }

    @Test
    void raise_con_y_sin_nombre() {
        assertFalse(tieneErrores("raise; raise Constraint_Error;"),
                "raise con y sin nombre debería parsear sin errores");
    }

    @Test
    void if_sin_then_se_reporta() {
        assertTrue(tieneErrores("if X = 1 Y := 1; end if;"));
    }

    @Test
    void end_loop_sin_loop_se_reporta() {
        assertTrue(tieneErrores("for I in 1 .. 3 Sumar(I); end loop;"));
    }
}
