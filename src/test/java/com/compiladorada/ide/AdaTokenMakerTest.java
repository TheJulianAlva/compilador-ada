package com.compiladorada.ide;

import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;

import javax.swing.text.Segment;

import static org.junit.jupiter.api.Assertions.*;

class AdaTokenMakerTest {

    private Token primerToken(String linea) {
        Segment s = new Segment(linea.toCharArray(), 0, linea.length());
        return new AdaTokenMaker().getTokenList(s, TokenTypes.NULL, 0);
    }

    @Test
    void marca_palabra_reservada() {
        Token t = primerToken("procedure");
        assertEquals(TokenTypes.RESERVED_WORD, t.getType());
    }

    @Test
    void palabra_reservada_es_case_insensitive() {
        Token t = primerToken("PROCEDURE");
        assertEquals(TokenTypes.RESERVED_WORD, t.getType());
    }

    @Test
    void marca_comentario_de_linea() {
        Token t = primerToken("-- esto es comentario");
        assertEquals(TokenTypes.COMMENT_EOL, t.getType());
    }

    @Test
    void identificador_no_reservado_no_es_palabra_reservada() {
        Token t = primerToken("Contador");
        assertNotEquals(TokenTypes.RESERVED_WORD, t.getType());
        assertEquals(TokenTypes.IDENTIFIER, t.getType());
    }

    @Test
    void marca_numero_decimal() {
        Token t = primerToken("123");
        assertEquals(TokenTypes.LITERAL_NUMBER_DECIMAL_INT, t.getType());
    }

    @Test
    void marca_cadena() {
        Token t = primerToken("\"hola\"");
        assertEquals(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, t.getType());
    }
}
