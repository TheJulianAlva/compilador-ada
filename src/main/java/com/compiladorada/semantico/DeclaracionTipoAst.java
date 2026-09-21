package com.compiladorada.semantico;

/** Valor guardado en los nodos #DeclaracionTipo y #DeclaracionSubtipo: lo que
 * Ada.jjt#declaracionTipo()/declaracionSubtipo() ya calculó durante el
 * parseo (Implementación A), para que la Implementación B (Task 9) no tenga
 * que volver a interpretar los tokens. */
public record DeclaracionTipoAst(String nombre, TipoAda tipo, int linea, int columna) {
}
