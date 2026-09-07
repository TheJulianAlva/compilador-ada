package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.generado.AdaParser;
import com.compiladorada.lexico.AnalizadorLexico;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class Compilador {

    private Compilador() {
    }

    public static ResultadoCompilacion analizar(String fuente, String nombreArchivo) {
        // nombreArchivo lo usa el IDE al volcar errores; el análisis no lo necesita.
        if (fuente == null) {
            fuente = "";
        }
        AnalizadorLexico lexico = new AnalizadorLexico(fuente);

        // Fases secuenciales: el análisis sintáctico solo se ejecuta si la fase
        // léxica está limpia. Con errores léxicos se devuelve el resultado con la
        // lista sintáctica vacía y sin AST (ver ResultadoCompilacion.sintacticoOmitido()).
        if (!lexico.errores().isEmpty()) {
            return new ResultadoCompilacion(lexico.tokens(), lexico.errores(), List.of(), null);
        }

        List<ErrorCompilacion> sintacticos = new ArrayList<>();
        SimpleNode ast = null;
        AdaParser parser = new AdaParser(new StringReader(fuente));
        boolean interrumpido = false;
        try {
            ast = parser.programa();
        } catch (Throwable t) {
            // la recuperación se rindió, o error inesperado: se preserva lo acumulado
            interrumpido = true;
        }
        sintacticos.addAll(parser.getErroresSintacticos());
        if (interrumpido) {
            sintacticos.add(new ErrorCompilacion(Categoria.SINTACTICO, 1, 1,
                    "análisis interrumpido: demasiados errores"));
        }
        if (ast == null && sintacticos.isEmpty()) {
            sintacticos.add(new ErrorCompilacion(Categoria.SINTACTICO, 1, 1,
                    "no se pudo construir el árbol sintáctico"));
        }

        return new ResultadoCompilacion(
                lexico.tokens(),
                lexico.errores(),
                List.copyOf(sintacticos),
                ast);
    }
}
