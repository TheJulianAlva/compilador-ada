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
    void con_errores_lexicos_no_se_ejecuta_el_analisis_sintactico() {
        //  El '$' es un error léxico; además falta un ';'. Las fases son
        //  secuenciales: al haber error léxico, el parser NO corre.
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is X : Integer $ begin null end;", "p.ada");
        assertFalse(r.erroresLexicos().isEmpty());
        assertTrue(r.erroresSintacticos().isEmpty(), "el análisis sintáctico no debía ejecutarse");
        assertNull(r.ast());
        assertTrue(r.sintacticoOmitido());
    }

    @Test
    void sin_errores_lexicos_si_se_ejecuta_el_analisis_sintactico() {
        //  Léxicamente limpio pero falta un ';': el parser sí corre y lo detecta.
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is X : Integer begin null; end;", "p.ada");
        assertTrue(r.erroresLexicos().isEmpty());
        assertFalse(r.erroresSintacticos().isEmpty());
        assertFalse(r.sintacticoOmitido());
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

    @Test
    void expone_errores_semanticos_cuando_no_hay_errores_lexicos_ni_sintacticos() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is begin X := 1; end;", "t.ada");
        assertTrue(r.erroresLexicos().isEmpty());
        assertTrue(r.erroresSintacticos().isEmpty());
        assertFalse(r.erroresSemanticos().isEmpty());
    }

    @Test
    void no_corre_el_semantico_si_hay_errores_sintacticos() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is begin X := ; end;", "t.ada");
        assertFalse(r.erroresSintacticos().isEmpty());
        assertTrue(r.erroresSemanticos().isEmpty());
    }

    @Test
    void programa_valido_genera_codigo_intermedio() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is A : Integer := 2; begin A := A + 1; end P;", "p.ada");
        assertFalse(r.intermedioOmitido());
        assertNotNull(r.intermedio());
        assertEquals(2, r.intermedio().filas().size());
    }

    @Test
    void con_errores_en_cualquier_fase_no_hay_codigo_intermedio() {
        ResultadoCompilacion lexico = Compilador.analizar("procedure P is X : Integer $ begin null; end;", "p.ada");
        ResultadoCompilacion sintactico = Compilador.analizar("procedure P is X : Integer begin null; end;", "p.ada");
        ResultadoCompilacion semantico = Compilador.analizar(
                "procedure P is A : Integer := 1; begin B := A + 1; end P;", "p.ada");
        for (ResultadoCompilacion r : new ResultadoCompilacion[] {lexico, sintactico, semantico}) {
            assertTrue(r.tieneErrores());
            assertTrue(r.intermedioOmitido());
            assertNull(r.intermedio());
        }
        assertFalse(semantico.erroresSemanticos().isEmpty());
    }
}
