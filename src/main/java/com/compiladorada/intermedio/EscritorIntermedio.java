package com.compiladorada.intermedio;

import com.compiladorada.ResultadoCompilacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Vuelca el reporte de código intermedio a {@code codigo_intermedio.txt} (se sobrescribe en cada compilación). */
public final class EscritorIntermedio {

    public static final String ARCHIVO = "codigo_intermedio.txt";

    private EscritorIntermedio() {
    }

    public static void volcar(ResultadoCompilacion r, Path dirSalida) {
        String contenido = r.intermedioOmitido() || r.intermedio() == null
                ? "== CÓDIGO INTERMEDIO ==\n\nGeneración OMITIDA: el programa tiene errores léxicos, "
                        + "sintácticos o semánticos pendientes.\n"
                : r.intermedio().aTexto();
        try {
            Files.createDirectories(dirSalida);
            Files.writeString(dirSalida.resolve(ARCHIVO), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
