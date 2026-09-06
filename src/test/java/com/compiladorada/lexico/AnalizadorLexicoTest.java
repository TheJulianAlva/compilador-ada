package com.compiladorada.lexico;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalizadorLexicoTest {

    @Test
    void clasifica_identificador_reservada_y_delimitadores() {
        AnalizadorLexico a = new AnalizadorLexico("procedure P is begin end;");
        List<TokenLexico> t = a.tokens();
        assertEquals(TipoToken.PALABRA_RESERVADA, t.get(0).tipo());
        assertEquals("procedure", t.get(0).lexema());
        assertEquals(TipoToken.IDENTIFICADOR, t.get(1).tipo());
        assertEquals(TipoToken.DELIMITADOR_SIMPLE, t.get(t.size() - 1).tipo());
        assertTrue(a.errores().isEmpty());
    }

    @Test
    void clasifica_literales_por_subtipo() {
        List<TokenLexico> t = new AnalizadorLexico("1_000 3.14 16#FF# 'a' \"hi\"").tokens();
        assertEquals(TipoToken.ENTERO, t.get(0).tipo());
        assertEquals(TipoToken.REAL, t.get(1).tipo());
        assertEquals(TipoToken.BASADO, t.get(2).tipo());
        assertEquals(TipoToken.CARACTER, t.get(3).tipo());
        assertEquals(TipoToken.CADENA, t.get(4).tipo());
    }

    @Test
    void guion_bajo_agrupa_digitos_en_entero() {
        List<TokenLexico> t = new AnalizadorLexico("1_000_000").tokens();
        assertEquals(1, t.size());
        assertEquals(TipoToken.ENTERO, t.get(0).tipo());
        assertEquals("1_000_000", t.get(0).lexema());
    }

    @Test
    void entero_no_puede_terminar_en_guion_bajo() {
        List<TokenLexico> t = new AnalizadorLexico("1_").tokens();
        // '1_' no es un ENTERO limpio: debe partirse o marcarse como error,
        // nunca clasificarse como un único ENTERO.
        assertFalse(t.size() == 1 && t.get(0).tipo() == TipoToken.ENTERO
                && t.get(0).lexema().equals("1_"));
    }

    @Test
    void los_comentarios_aparecen_en_la_lista_de_tokens() {
        List<TokenLexico> t = new AnalizadorLexico("x -- nota\ny").tokens();
        assertTrue(t.stream().anyMatch(tok -> tok.tipo() == TipoToken.COMENTARIO
                && tok.lexema().equals("-- nota")));
    }

    @Test
    void caracter_ilegal_es_token_ERROR_y_genera_error_lexico() {
        AnalizadorLexico a = new AnalizadorLexico("x $ y");
        assertTrue(a.tokens().stream().anyMatch(tok -> tok.tipo() == TipoToken.ERROR));
        List<ErrorCompilacion> e = a.errores();
        assertEquals(1, e.size());
        assertEquals(ErrorCompilacion.Categoria.LEXICO, e.get(0).categoria());
        assertEquals(1, e.get(0).linea());
        assertEquals(3, e.get(0).columna());
        assertTrue(e.get(0).mensaje().contains("$"));
    }

    @Test
    void caracter_ajeno_dentro_de_palabra_produce_un_solo_token_error() {
        //  "i@f" no debe partirse en i / @ / f: es UN token invalido.
        AnalizadorLexico a = new AnalizadorLexico("procedure P is begin i@f X; end P;");
        List<TokenLexico> errores = a.tokens().stream()
                .filter(t -> t.tipo() == TipoToken.ERROR)
                .toList();
        assertEquals(1, errores.size());
        assertEquals("i@f", errores.get(0).lexema());

        List<ErrorCompilacion> e = a.errores();
        assertEquals(1, e.size());
        assertEquals(ErrorCompilacion.Categoria.LEXICO, e.get(0).categoria());
        assertTrue(e.get(0).mensaje().contains("i@f"));
    }

    @Test
    void numero_con_caracter_ajeno_pegado_es_un_solo_token_error() {
        List<TokenLexico> t = new AnalizadorLexico("X := 10$2;").tokens();
        assertTrue(t.stream().anyMatch(tok -> tok.tipo() == TipoToken.ERROR
                && tok.lexema().equals("10$2")));
        // el ';' final se conserva como delimitador, no se lo traga el token invalido
        assertEquals(TipoToken.DELIMITADOR_SIMPLE, t.get(t.size() - 1).tipo());
    }

    @Test
    void reporta_linea_y_columna_correctas() {
        List<TokenLexico> t = new AnalizadorLexico("procedure\n  P").tokens();
        assertEquals(2, t.get(1).linea());
        assertEquals(3, t.get(1).columna());
    }

    @Test
    void entrada_basura_no_lanza() {
        // incluye NUL, espacio y acentos graves en la cadena: nada debe lanzar
        String basura = "###\0 @@@" + "`".repeat(3);
        assertDoesNotThrow(() -> new AnalizadorLexico(basura).tokens());
    }

    /**
     * Guarda de regresion: si un reordenamiento futuro de tokens en Ada.jjt rompe
     * la contiguidad de KW_PROCEDURE..KW_REM o ASIGNA..BARRA, estas aserciones fallan
     * en vez de misclasificar en silencio.
     */
    @Test
    void guarda_clasificacion_de_rangos() {
        // delimitador compuesto
        List<TokenLexico> c = new AnalizadorLexico("X := 1").tokens();
        assertEquals(TipoToken.DELIMITADOR_COMPUESTO,
                c.stream().filter(t -> t.lexema().equals(":=")).findFirst().orElseThrow().tipo());

        // limite superior del rango de reservadas: KW_MOD / KW_REM
        List<TokenLexico> m = new AnalizadorLexico("A mod B rem C").tokens();
        assertEquals(TipoToken.PALABRA_RESERVADA,
                m.stream().filter(t -> t.lexema().equals("mod")).findFirst().orElseThrow().tipo());
        assertEquals(TipoToken.PALABRA_RESERVADA,
                m.stream().filter(t -> t.lexema().equals("rem")).findFirst().orElseThrow().tipo());

        // limite inferior del rango de reservadas: KW_PROCEDURE
        List<TokenLexico> p = new AnalizadorLexico("procedure ; ").tokens();
        assertEquals(TipoToken.PALABRA_RESERVADA, p.get(0).tipo());
        // delimitador simple
        assertEquals(TipoToken.DELIMITADOR_SIMPLE,
                p.stream().filter(t -> t.lexema().equals(";")).findFirst().orElseThrow().tipo());
    }
}
