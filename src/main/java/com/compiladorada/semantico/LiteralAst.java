package com.compiladorada.semantico;

/** Valor guardado en el nodo #Literal: el texto crudo del token junto con el
 * tipo ya calculado durante el parseo (Implementación A), para que la
 * Implementación B (Task 9) no tenga que re-derivar el tipo a partir del
 * texto. */
public record LiteralAst(String texto, TipoAda tipo) {
}
