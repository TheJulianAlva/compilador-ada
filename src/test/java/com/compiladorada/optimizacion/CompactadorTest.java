package com.compiladorada.optimizacion;

import com.compiladorada.lexico.AnalizadorLexico;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactadorTest {

    private static String compactar(String fuente) {
        return Compactador.compactar(new AnalizadorLexico(fuente).tokens(), Map.of());
    }

    @Test
    void espaciado_minimo_en_expresiones_y_declaraciones() {
        assertEquals("Total:=A+B*2;", Compactador.unir(List.of("Total", ":=", "A", "+", "B", "*", "2", ";")));
        assertEquals("x:Integer;", compactar("x   :   Integer  ;"));
    }

    @Test
    void estructura_de_un_procedimiento_una_sentencia_por_linea() {
        assertEquals("procedure P is\nbegin\nnull;\nend P;",
                compactar("-- hola\nprocedure P   is\n\n   begin   -- x\n   null;\n\n end P;\n"));
    }

    @Test
    void no_pega_tokens_que_cambiarian_de_significado() {
        assertEquals("A- -1", Compactador.unir(List.of("A", "-", "-", "1")));
        assertEquals("abs A", Compactador.unir(List.of("abs", "A")));
        assertEquals("A mod 7", Compactador.unir(List.of("A", "mod", "7")));
    }

    @Test
    void construcciones_de_varias_palabras_no_se_parten() {
        assertEquals("while X loop\nnull;\nend loop;",
                compactar("while X loop null; end loop;"));
        assertEquals("if A or else B then\nnull;\nend if;",
                compactar("if A or else B then null; end if;"));
        assertEquals("if A and then B then\nnull;\nend if;",
                compactar("if A and then B then null; end if;"));
    }

    @Test
    void is_solo_salta_en_subprogramas_y_punto_y_coma_no_salta_dentro_de_parentesis() {
        assertEquals("type T is(A,B);", compactar("type T is (A, B);"));
        assertEquals("procedure P(X:in Integer;Y:out Integer)is",
                compactar("procedure P (X : in Integer; Y : out Integer) is"));
        assertEquals("type R is record\nX:Integer;\nend record;",
                compactar("type R is record X : Integer; end record;"));
    }

    @Test
    void manejadores_saltan_despues_de_la_flecha() {
        assertEquals("exception\nwhen others=>\nnull;",
                compactar("exception when others => null;"));
    }

    @Test
    void cadenas_intactas_y_comentarios_eliminados() {
        assertEquals("T:String:=\"a  --b\";",
                compactar("T : String := \"a  --b\"; -- comentario"));
    }

    @Test
    void reemplazo_sustituye_el_rango_de_tokens() {
        var tokens = new AnalizadorLexico("X := 2 * 3 ;").tokens();
        long inicio = Compactador.clave(1, 6);
        long fin = Compactador.clave(1, 10);
        String r = Compactador.compactar(tokens,
                Map.of(inicio, new Compactador.Reemplazo(List.of("6"), fin)));
        assertEquals("X:=6;", r);
    }
}
