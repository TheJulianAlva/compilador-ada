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
        AnalizadorLexico lexico = new AnalizadorLexico(fuente);

        List<ErrorCompilacion> sintacticos = new ArrayList<>();
        SimpleNode ast = null;
        AdaParser parser = new AdaParser(new StringReader(fuente));
        try {
            ast = parser.programa();
        } catch (Throwable t) {
            // la recuperación se rindió, o error inesperado: se preserva lo acumulado
        }
        sintacticos.addAll(parser.getErroresSintacticos());
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
