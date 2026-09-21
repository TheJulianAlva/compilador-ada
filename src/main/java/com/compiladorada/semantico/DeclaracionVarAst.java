package com.compiladorada.semantico;

import java.util.List;

/** Valor guardado en el nodo #DeclaracionVar: lo que Ada.jjt#declaracionVar()
 * ya calculó durante el parseo (Implementación A), para que la
 * Implementación B (Task 9) no tenga que volver a interpretar los tokens. */
public record DeclaracionVarAst(List<String> nombres, TipoAda tipo, boolean esConstante,
                                 int linea, int columna) {
}
