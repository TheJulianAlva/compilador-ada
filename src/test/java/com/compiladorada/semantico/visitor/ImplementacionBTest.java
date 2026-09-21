package com.compiladorada.semantico.visitor;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ImplementacionBTest {

    private SimpleNode ast(String fuente) throws Exception {
        return new AdaParser(new StringReader(fuente)).programa();
    }

    private VerificadorSemantico verificarConB(String fuente) throws Exception {
        SimpleNode raiz = ast(fuente);
        RecolectorDeclaraciones pasada1 = RecolectorDeclaraciones.recolectar(raiz);
        VerificadorUsos.verificar(raiz, pasada1);
        return pasada1.verificador();
    }

    @Test
    void variable_no_declarada_reporta_error_semantico() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is begin X := 1; end;");
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("no está declarado"));
    }

    @Test
    void programa_valido_no_reporta_errores_semanticos() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is X : Integer; begin X := 1 + 2; end;");
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void asignar_tipo_incompatible_reporta_error_semantico() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is X : Integer; begin X := True; end;");
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("incompatibles"));
    }

    @Test
    void redeclarar_una_variable_reporta_error_semantico() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is X : Integer; X : Float; begin null; end;");
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("ya está declarado"));
    }

    @Test
    void llamada_recursiva_no_reporta_error_de_no_declarado() throws Exception {
        VerificadorSemantico v = verificarConB(
                "function Fib (N : Integer) return Integer is "
              + "begin return Fib(N); end;");
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void variables_de_un_procedimiento_hermano_no_son_visibles() throws Exception {
        VerificadorSemantico v = verificarConB(
                "procedure Primero is begin Solo_En_Segundo := 1; end; "
              + "procedure Segundo is Solo_En_Segundo : Integer; begin null; end;");
        assertFalse(v.errores().isEmpty());
    }
}
