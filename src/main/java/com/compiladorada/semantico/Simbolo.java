package com.compiladorada.semantico;

/**
 * Una entrada de la tabla de símbolos: nombre declarado, su categoría y tipo,
 * si es mutable, y dónde se declaró (usado en mensajes de error y por la
 * regla de declarado-antes-de-usar de la Implementación B, ver
 * VerificadorSemantico.resolverUsoConOrden).
 */
public record Simbolo(
        String nombre,
        Categoria categoria,
        TipoAda tipo,
        boolean esConstante,
        int lineaDeclaracion,
        int columnaDeclaracion) {

    public enum Categoria {
        VARIABLE, TIPO, PARAMETRO, SUBPROGRAMA, PAQUETE
    }
}
