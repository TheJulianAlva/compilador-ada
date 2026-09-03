package com.compiladorada.sintactico;

import com.compiladorada.generado.ParseException;
import com.compiladorada.generado.Token;

import java.util.Map;

/**
 * Traduce las {@link ParseException} que produce el parser generado a mensajes
 * en español legible, con nombres amigables para los tokens.
 */
public final class TraductorMensajes {

    private static final Map<String, String> NOMBRES = Map.ofEntries(
            Map.entry("\";\"", "';'"),
            Map.entry("\":\"", "':'"),
            Map.entry("\"(\"", "'('"),
            Map.entry("\")\"", "')'"),
            Map.entry("\",\"", "','"),
            Map.entry("\":=\"", "':='"),
            Map.entry("\"=>\"", "'=>'"),
            Map.entry("\"..\"", "'..'"),
            Map.entry("\"**\"", "'**'"),
            Map.entry("<IDENTIFICADOR>", "un identificador"),
            Map.entry("<ENTERO>", "un número entero"),
            Map.entry("<REAL>", "un número real"),
            Map.entry("<BASADO>", "un número con base"),
            Map.entry("<CARACTER>", "un literal de carácter"),
            Map.entry("<CADENA>", "una cadena"),
            Map.entry("<EOF>", "el fin del archivo"),
            Map.entry("\"then\"", "'then'"),
            Map.entry("\"loop\"", "'loop'"),
            Map.entry("\"is\"", "'is'"),
            Map.entry("\"begin\"", "'begin'"),
            Map.entry("\"end\"", "'end'"),
            Map.entry("\"record\"", "'record'"),
            Map.entry("\"return\"", "'return'"),
            Map.entry("\"if\"", "'if'"),
            Map.entry("\"of\"", "'of'"));

    private TraductorMensajes() {
    }

    public static String traducir(ParseException e) {
        Token ofensor = e.currentToken != null && e.currentToken.next != null
                ? e.currentToken.next
                : e.currentToken;
        String encontrado = ofensor != null ? ofensor.image : "?";
        if (ofensor != null && ofensor.kind == 0) {
            encontrado = "fin del archivo";
        }

        java.util.LinkedHashSet<String> vistos = new java.util.LinkedHashSet<>();
        if (e.expectedTokenSequences != null && e.tokenImage != null) {
            for (int[] seq : e.expectedTokenSequences) {
                if (seq.length > 0) {
                    vistos.add(amigable(e.tokenImage[seq[0]]));
                }
            }
        }

        if (!vistos.isEmpty()) {
            return "se esperaba " + String.join(" o ", vistos)
                    + " pero se encontró '" + encontrado + "'";
        }
        return "construcción no válida cerca de '" + encontrado + "'";
    }

    private static String amigable(String imagenToken) {
        return NOMBRES.getOrDefault(imagenToken, imagenToken);
    }
}
