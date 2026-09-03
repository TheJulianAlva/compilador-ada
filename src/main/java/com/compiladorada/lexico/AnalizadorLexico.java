package com.compiladorada.lexico;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.generado.AdaParserConstants;
import com.compiladorada.generado.AdaParserTokenManager;
import com.compiladorada.generado.SimpleCharStream;
import com.compiladorada.generado.Token;
import com.compiladorada.generado.TokenMgrError;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Recorrido léxico dedicado: produce la lista completa de {@link TokenLexico}
 * (incluidos comentarios y tokens de error) y la lista de {@link ErrorCompilacion}
 * de categoría léxica, para alimentar la tabla de tokens del IDE.
 *
 * <p>Nunca lanza: si {@code getNextToken()} lanza {@link TokenMgrError}, se
 * registra el error y se detiene el recorrido.
 */
public final class AnalizadorLexico {

    private final List<TokenLexico> tokens = new ArrayList<>();
    private final List<ErrorCompilacion> errores = new ArrayList<>();

    public AnalizadorLexico(String fuente) {
        AdaParserTokenManager tm =
                new AdaParserTokenManager(new SimpleCharStream(new StringReader(fuente)));
        try {
            Token t = tm.getNextToken();
            while (t.kind != AdaParserConstants.EOF) {
                agregarEspeciales(t);
                agregar(t);
                t = tm.getNextToken();
            }
            agregarEspeciales(t); // comentarios pegados justo antes de EOF
        } catch (TokenMgrError e) {
            errores.add(new ErrorCompilacion(Categoria.LEXICO, 1, 1,
                    "no se pudo continuar el análisis léxico: " + e.getMessage()));
        }
    }

    public List<TokenLexico> tokens() {
        return List.copyOf(tokens);
    }

    public List<ErrorCompilacion> errores() {
        return List.copyOf(errores);
    }

    /** Los SPECIAL_TOKEN (comentarios) cuelgan de t.specialToken en orden inverso. */
    private void agregarEspeciales(Token t) {
        if (t.specialToken == null) {
            return;
        }
        List<Token> especiales = new ArrayList<>();
        Token s = t.specialToken;
        while (s != null) {
            especiales.add(0, s);
            s = s.specialToken;
        }
        for (Token esp : especiales) {
            tokens.add(new TokenLexico(esp.image, TipoToken.COMENTARIO,
                    esp.beginLine, esp.beginColumn));
        }
    }

    private void agregar(Token t) {
        TipoToken tipo = clasificar(t);
        tokens.add(new TokenLexico(t.image, tipo, t.beginLine, t.beginColumn));
        if (tipo == TipoToken.ERROR) {
            errores.add(new ErrorCompilacion(Categoria.LEXICO, t.beginLine, t.beginColumn,
                    mensajeLexico(t)));
        }
    }

    private String mensajeLexico(Token t) {
        switch (t.kind) {
            case AdaParserConstants.CADENA_SIN_CERRAR:
                return "cadena sin cerrar antes de fin de línea";
            case AdaParserConstants.CARACTER_MALFORMADO:
                return "literal de carácter mal formado: " + t.image;
            case AdaParserConstants.IDENT_MALFORMADO:
                return "identificador no válido '" + t.image
                        + "': no puede terminar en '_' ni contener '__'";
            default:
                return "carácter no válido '" + t.image + "'";
        }
    }

    private TipoToken clasificar(Token t) {
        switch (t.kind) {
            case AdaParserConstants.IDENTIFICADOR:
                return PalabrasReservadas.esReservada(t.image)
                        ? TipoToken.PALABRA_RESERVADA
                        : TipoToken.IDENTIFICADOR;
            case AdaParserConstants.ENTERO:
                return TipoToken.ENTERO;
            case AdaParserConstants.REAL:
                return TipoToken.REAL;
            case AdaParserConstants.BASADO:
                return TipoToken.BASADO;
            case AdaParserConstants.CARACTER:
                return TipoToken.CARACTER;
            case AdaParserConstants.CADENA:
                return TipoToken.CADENA;
            case AdaParserConstants.ERROR_LEXICO:
            case AdaParserConstants.CADENA_SIN_CERRAR:
            case AdaParserConstants.CARACTER_MALFORMADO:
            case AdaParserConstants.IDENT_MALFORMADO:
                return TipoToken.ERROR;
            default:
                if (esCompuesto(t.kind)) {
                    return TipoToken.DELIMITADOR_COMPUESTO;
                }
                return esReservadaKind(t.kind)
                        ? TipoToken.PALABRA_RESERVADA
                        : TipoToken.DELIMITADOR_SIMPLE;
        }
    }

    /**
     * Delimitadores compuestos. En el {@code AdaParserConstants} generado ocupan
     * el rango contiguo ASIGNA(45)..BARRA(55): := => .. ** /= >= <= &lt;&lt; &gt;&gt; &lt;&gt; |
     */
    private boolean esCompuesto(int kind) {
        return kind >= AdaParserConstants.ASIGNA && kind <= AdaParserConstants.BARRA;
    }

    /**
     * Palabras reservadas declaradas como token propio en {@code Ada.jjt}.
     * Verificado contra el {@code AdaParserConstants} generado: forman el rango
     * contiguo KW_PROCEDURE(6)..KW_REM(44) sin nada intercalado.
     */
    private boolean esReservadaKind(int kind) {
        return kind >= AdaParserConstants.KW_PROCEDURE && kind <= AdaParserConstants.KW_REM;
    }
}
