package com.compiladorada.lexico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.AdaParserConstants;
import com.compiladorada.generado.AdaParserTokenManager;
import com.compiladorada.generado.SimpleCharStream;
import com.compiladorada.generado.Token;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerHumoTest {

    private List<Token> tokenizar(String fuente) {
        AdaParserTokenManager tm =
                new AdaParserTokenManager(new SimpleCharStream(new StringReader(fuente)));
        List<Token> tokens = new ArrayList<>();
        Token t;
        while ((t = tm.getNextToken()).kind != AdaParserConstants.EOF) {
            tokens.add(t);
        }
        return tokens;
    }

    @Test
    void reconoce_palabras_reservadas_identificadores_y_delimitadores() {
        List<Token> t = tokenizar("procedure Hola is begin end;");
        assertEquals(AdaParserConstants.KW_PROCEDURE, t.get(0).kind);
        assertEquals(AdaParserConstants.IDENTIFICADOR, t.get(1).kind);
        assertEquals("Hola", t.get(1).image);
        assertEquals(AdaParserConstants.KW_IS, t.get(2).kind);
        assertEquals(AdaParserConstants.PYC, t.get(t.size() - 1).kind);
    }

    @Test
    void ada_es_case_insensitive_en_palabras_reservadas() {
        List<Token> t = tokenizar("PROCEDURE x IS BEGIN END;");
        assertEquals(AdaParserConstants.KW_PROCEDURE, t.get(0).kind);
        assertEquals(AdaParserConstants.KW_IS, t.get(2).kind);
    }

    @Test
    void reconoce_literales() {
        List<Token> t = tokenizar("1_000 3.14 16#FF# 'a' \"hola\"");
        assertEquals(AdaParserConstants.ENTERO, t.get(0).kind);
        assertEquals(AdaParserConstants.REAL, t.get(1).kind);
        assertEquals(AdaParserConstants.BASADO, t.get(2).kind);
        assertEquals(AdaParserConstants.CARACTER, t.get(3).kind);
        assertEquals(AdaParserConstants.CADENA, t.get(4).kind);
    }

    @Test
    void caracter_ilegal_aislado_produce_token_LEXEMA_INVALIDO() {
        List<Token> t = tokenizar("x $ y");
        assertTrue(t.stream().anyMatch(tok -> tok.kind == AdaParserConstants.LEXEMA_INVALIDO));
    }

    @Test
    void caracter_ilegal_dentro_de_palabra_no_parte_el_token() {
        //  "i@f" debe salir como UN solo token de error, no como i / @ / f.
        List<Token> t = tokenizar("i@f");
        assertEquals(1, t.size());
        assertEquals(AdaParserConstants.LEXEMA_INVALIDO, t.get(0).kind);
        assertEquals("i@f", t.get(0).image);
    }
}
