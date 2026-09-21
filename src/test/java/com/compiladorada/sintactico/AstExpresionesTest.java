package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class AstExpresionesTest {

    private SimpleNode ast(String fuente) throws Exception {
        return new AdaParser(new StringReader(fuente)).programa();
    }

    private SimpleNode buscar(SimpleNode n, String nombre) {
        if (n.toString().equals(nombre)) {
            return n;
        }
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            SimpleNode r = buscar((SimpleNode) n.jjtGetChild(i), nombre);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @Test
    void una_expresion_sin_operadores_no_crea_nodos_envoltorio() throws Exception {
        // "X := Y;" — ninguno de Expresion/Relacion/Simple/Termino/Factor
        // debería aparecer: el único nodo bajo Asignacion es el Nombre "Y".
        SimpleNode raiz = ast("procedure P is X, Y : Integer; begin X := Y; end;");
        SimpleNode asignacion = buscar(raiz, "Asignacion");
        assertNotNull(asignacion);
        assertNull(buscar(asignacion, "Expresion"));
        assertNull(buscar(asignacion, "Simple"));
        assertNotNull(buscar(asignacion, "Nombre"));
    }

    @Test
    void una_expresion_aritmetica_crea_los_nodos_correspondientes() throws Exception {
        SimpleNode raiz = ast("procedure P is X : Integer; begin X := 1 + 2 * 3; end;");
        assertNotNull(buscar(raiz, "Simple"));
        assertNotNull(buscar(raiz, "Termino"));
    }

    @Test
    void un_literal_entero_se_reconoce_como_nodo_Literal() throws Exception {
        SimpleNode raiz = ast("procedure P is X : Integer; begin X := 42; end;");
        SimpleNode lit = buscar(raiz, "Literal");
        assertNotNull(lit);
        assertEquals("42", lit.jjtGetValue());
    }
}
