package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AstTest {

    private SimpleNode ast(String fuente) throws Exception {
        return new AdaParser(new StringReader(fuente)).programa();
    }

    private void recolectar(SimpleNode n, List<String> acc) {
        acc.add(n.toString());
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            recolectar((SimpleNode) n.jjtGetChild(i), acc);
        }
    }

    @Test
    void la_raiz_es_Programa() throws Exception {
        SimpleNode raiz = ast("procedure P is begin null; end;");
        assertEquals("Programa", raiz.toString());
    }

    @Test
    void el_arbol_contiene_los_nodos_esperados() throws Exception {
        SimpleNode raiz = ast(
                "procedure P (X : Integer) is C : constant Integer := 1; "
              + "begin if X = 1 then P2(C); end if; end P;");
        List<String> nombres = new ArrayList<>();
        recolectar(raiz, nombres);
        assertTrue(nombres.contains("Procedimiento"));
        assertTrue(nombres.contains("Parametro"));
        assertTrue(nombres.contains("DeclaracionVar"));
        assertTrue(nombres.contains("If"));
        assertTrue(nombres.contains("LlamadaProc"));
    }

    @Test
    void los_nodos_conservan_su_posicion() throws Exception {
        SimpleNode raiz = ast("procedure P is\n begin null; end;");
        SimpleNode proc = (SimpleNode) raiz.jjtGetChild(0);
        assertEquals("Procedimiento", proc.toString());
        assertEquals(1, proc.jjtGetFirstToken().beginLine);
    }

    @Test
    void distingue_tipo_de_subtipo() throws Exception {
        SimpleNode raiz = ast(
                "procedure P is type T is range 1 .. 10; "
              + "subtype S is Integer range 0 .. 5; begin null; end;");
        List<String> nombres = new ArrayList<>();
        recolectar(raiz, nombres);
        assertTrue(nombres.contains("DeclaracionTipo"));
        assertTrue(nombres.contains("DeclaracionSubtipo"));
        assertTrue(nombres.contains("DefinicionTipo"));
    }

    @Test
    void los_componentes_de_registro_son_nodos() throws Exception {
        SimpleNode raiz = ast(
                "procedure P is type R is record A : Integer; B : Float; end record; "
              + "begin null; end;");
        List<String> nombres = new ArrayList<>();
        recolectar(raiz, nombres);
        assertTrue(nombres.contains("ComponenteRegistro"));
    }
}
