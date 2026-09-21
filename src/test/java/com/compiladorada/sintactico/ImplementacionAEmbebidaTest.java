package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ImplementacionAEmbebidaTest {

    private AdaParser parsear(String fuente) throws Exception {
        AdaParser p = new AdaParser(new StringReader(fuente));
        p.programa();
        return p;
    }

    @Test
    void variable_no_declarada_reporta_error_semantico() throws Exception {
        AdaParser p = parsear("procedure P is begin X := 1; end;");
        assertEquals(1, p.getErroresSemanticos().size());
        assertTrue(p.getErroresSemanticos().get(0).mensaje().contains("no está declarado"));
    }

    @Test
    void programa_valido_no_reporta_errores_semanticos() throws Exception {
        AdaParser p = parsear("procedure P is X : Integer; begin X := 1 + 2; end;");
        assertTrue(p.getErroresSemanticos().isEmpty());
    }

    @Test
    void asignar_tipo_incompatible_reporta_error_semantico() throws Exception {
        AdaParser p = parsear("procedure P is X : Integer; begin X := True; end;");
        assertEquals(1, p.getErroresSemanticos().size());
        assertTrue(p.getErroresSemanticos().get(0).mensaje().contains("incompatibles"));
    }

    @Test
    void redeclarar_una_variable_reporta_error_semantico() throws Exception {
        AdaParser p = parsear("procedure P is X : Integer; X : Float; begin null; end;");
        assertEquals(1, p.getErroresSemanticos().size());
        assertTrue(p.getErroresSemanticos().get(0).mensaje().contains("ya está declarado"));
    }

    @Test
    void llamada_recursiva_no_reporta_error_de_no_declarado() throws Exception {
        AdaParser p = parsear(
                "function Fib (N : Integer) return Integer is "
              + "begin return Fib(N); end;");
        assertTrue(p.getErroresSemanticos().isEmpty());
    }
}
