package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaSentenciasTest {

    private void parsear(String cuerpo) throws ParseException {
        String fuente = "procedure P is begin " + cuerpo + " end;";
        new AdaParser(new StringReader(fuente)).programa();
    }

    @Test
    void asignacion_y_llamada() {
        assertDoesNotThrow(() -> parsear("X := 1; Poner(X); Iniciar;"));
    }

    @Test
    void if_elsif_else() {
        assertDoesNotThrow(() -> parsear(
                "if X = 1 then Y := 1; elsif X = 2 then Y := 2; else Y := 0; end if;"));
    }

    @Test
    void for_con_reverse() {
        assertDoesNotThrow(() -> parsear(
                "for I in reverse 1 .. 10 loop Sumar(I); end loop;"));
    }

    @Test
    void while_loop() {
        assertDoesNotThrow(() -> parsear("while X < 10 loop X := X + 1; end loop;"));
    }

    @Test
    void raise_con_y_sin_nombre() {
        assertDoesNotThrow(() -> parsear("raise; raise Constraint_Error;"));
    }

    @Test
    void if_sin_then_lanza() {
        assertThrows(ParseException.class, () -> parsear("if X = 1 Y := 1; end if;"));
    }

    @Test
    void end_loop_sin_loop_lanza() {
        assertThrows(ParseException.class, () -> parsear(
                "for I in 1 .. 3 Sumar(I); end loop;"));
    }
}
