package com.compiladorada.errores;

import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.lexico.TokenLexico;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class EscritorErrores {

    private EscritorErrores() {
    }

    public static void volcar(ResultadoCompilacion r, Path dirSalida, String nombreArchivoFuente) {
        try {
            Files.createDirectories(dirSalida);
            escribirErrores(dirSalida.resolve("errores_lexicos.txt"),
                    "ERRORES LÉXICOS", r.erroresLexicos(), nombreArchivoFuente);
            escribirErrores(dirSalida.resolve("errores_sintacticos.txt"),
                    "ERRORES SINTÁCTICOS", r.erroresSintacticos(), nombreArchivoFuente);
            escribirTokens(dirSalida.resolve("tokens.txt"), r.tokens());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escribirErrores(Path destino, String titulo,
                                        List<ErrorCompilacion> errores, String nombre) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("== ").append(titulo).append(" ==\n");
        sb.append("Compilado: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("Total: ").append(errores.size())
          .append(errores.size() == 1 ? " error" : " errores").append("\n\n");
        if (errores.isEmpty()) {
            sb.append("Sin errores.\n");
        } else {
            for (ErrorCompilacion e : errores) {
                sb.append(e.formatear(nombre)).append('\n');
            }
        }
        Files.writeString(destino, sb.toString());
    }

    private static void escribirTokens(Path destino, List<TokenLexico> tokens) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("== TABLA DE TOKENS ==\n");
        sb.append("Compilado: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("Total: ").append(tokens.size()).append("\n\n");
        sb.append(String.format("%-24s %-22s %6s %8s%n", "LEXEMA", "TIPO", "LINEA", "COLUMNA"));
        sb.append("-".repeat(62)).append('\n');
        for (TokenLexico t : tokens) {
            sb.append(String.format("%-24s %-22s %6d %8d%n",
                    recortar(t.lexema()), t.tipo(), t.linea(), t.columna()));
        }
        Files.writeString(destino, sb.toString());
    }

    private static String recortar(String s) {
        String limpio = s.replace("\n", "\\n").replace("\t", "\\t");
        return limpio.length() <= 24 ? limpio : limpio.substring(0, 21) + "...";
    }
}
