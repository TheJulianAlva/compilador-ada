package com.compiladorada;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CompiladorTest {

    @Test
    void programa_valido_sin_errores_y_con_ast() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is begin null; end;", "p.ada");
        assertFalse(r.tieneErrores());
        assertNotNull(r.ast());
        assertFalse(r.tokens().isEmpty());
    }

    @Test
    void separa_errores_lexicos_y_sintacticos() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is X : Integer $ begin null end;", "p.ada");
        assertFalse(r.erroresLexicos().isEmpty());
        assertFalse(r.erroresSintacticos().isEmpty());
    }

    @Test
    void nunca_lanza_con_entrada_valida_vacia_o_basura() {
        assertDoesNotThrow(() -> Compilador.analizar("", "x.ada"));
        assertDoesNotThrow(() -> Compilador.analizar("   \n\n  ", "x.ada"));
        Random rnd = new Random(42);
        for (int i = 0; i < 200; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 80; j++) sb.append((char) (rnd.nextInt(94) + 32));
            String basura = sb.toString();
            assertDoesNotThrow(() -> Compilador.analizar(basura, "fuzz.ada"));
        }
    }

    @Test
    void entrada_vacia_reporta_al_menos_un_error_sintactico() {
        ResultadoCompilacion r = Compilador.analizar("", "x.ada");
        assertTrue(r.tieneErrores());
    }
}
