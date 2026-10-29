package com.compiladorada.optimizacion;

import com.compiladorada.ResultadoCompilacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Vuelca el fuente optimizado a {@code codigo_optimizado.ada} (se sobrescribe en cada compilación). */
public final class EscritorOptimizacion {

    public static final String ARCHIVO = "codigo_optimizado.ada";

    private EscritorOptimizacion() {
    }

    public static void volcar(ResultadoCompilacion r, Path dirSalida) {
        String contenido = r.optimizacionOmitida() || r.optimizacion() == null
                ? "Optimización OMITIDA: el programa tiene errores léxicos, "
                        + "sintácticos o semánticos pendientes.\n"
                : r.optimizacion().optimizado() + "\n";
        try {
            Files.createDirectories(dirSalida);
            Files.writeString(dirSalida.resolve(ARCHIVO), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
