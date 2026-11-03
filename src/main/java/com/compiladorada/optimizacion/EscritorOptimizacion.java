package com.compiladorada.optimizacion;

import com.compiladorada.ResultadoCompilacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Vuelca el fuente optimizado a {@code codigo_optimizado.ada} y su código intermedio con la
 * validación a {@code codigo_intermedio_optimizado.txt} (se sobrescriben en cada compilación).
 */
public final class EscritorOptimizacion {

    public static final String ARCHIVO = "codigo_optimizado.ada";
    public static final String ARCHIVO_INTERMEDIO = "codigo_intermedio_optimizado.txt";

    private EscritorOptimizacion() {
    }

    public static void volcar(ResultadoCompilacion r, Path dirSalida) {
        String contenido = r.optimizacionOmitida() || r.optimizacion() == null
                ? "Optimización OMITIDA: el programa tiene errores léxicos, "
                        + "sintácticos o semánticos pendientes.\n"
                : r.optimizacion().optimizado() + "\n";
        String intermedio = r.optimizacionOmitida() || r.optimizacion() == null || r.optimizacion().intermedio() == null
                ? "Generación OMITIDA: el programa tiene errores léxicos, "
                        + "sintácticos o semánticos pendientes.\n"
                : r.optimizacion().intermedio().aTexto() + "\n" + r.optimizacion().validacion().texto() + "\n";
        try {
            Files.createDirectories(dirSalida);
            Files.writeString(dirSalida.resolve(ARCHIVO), contenido);
            Files.writeString(dirSalida.resolve(ARCHIVO_INTERMEDIO), intermedio);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
