package com.compiladorada.semantico;

/**
 * Valor guardado en el nodo #Paquete: lo que Ada.jjt#paquete() ya calculó
 * durante el parseo — nombre, si es la forma con {@code body} (que abre su
 * propio ámbito privado) o la forma spec (que es transparente y declara su
 * contenido en el ámbito envolvente, ver docs/unidad-0-diseno-ide.md §9.4),
 * y su posición.
 */
public record PaqueteAst(String nombre, boolean esBody, int linea, int columna) {
}
