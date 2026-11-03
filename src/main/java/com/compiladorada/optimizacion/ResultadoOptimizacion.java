package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ResultadoIntermedio;

/**
 * Fuente original y su versión optimizada (compactada, plegada y con potencia
 * reducida), con el código intermedio del optimizado y la validación contra
 * el del original. {@code intermedio} y {@code validacion} son {@code null}
 * hasta que {@link #validar} los calcula.
 */
public record ResultadoOptimizacion(String original, String optimizado,
                                    ResultadoIntermedio intermedio, Validacion validacion) {

    public ResultadoOptimizacion(String original, String optimizado) {
        this(original, optimizado, null, null);
    }

    /**
     * @param intermedioOptimizado código intermedio generado del texto optimizado
     *                             ({@code null} si ese texto no se pudo analizar)
     * @param intermedioOriginal   código intermedio del fuente original
     */
    public ResultadoOptimizacion validar(ResultadoIntermedio intermedioOriginal,
                                         ResultadoIntermedio intermedioOptimizado) {
        if (intermedioOptimizado == null) {
            return this;
        }
        return new ResultadoOptimizacion(original, optimizado, intermedioOptimizado,
                Validacion.comparar(intermedioOriginal, intermedioOptimizado));
    }
}
