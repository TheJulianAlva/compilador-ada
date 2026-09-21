package com.compiladorada.semantico;

/** Valor guardado en el nodo #Funcion: a diferencia de #Procedimiento y
 * #Paquete (que usan NombreAst), una función también necesita cargar su
 * tipo de RETORNO — Ada.jjt#funcion() (Task 7) ya lo calcula en
 * `tipoRetorno=tipoRef()` antes de declarar el subprograma, así que la
 * Implementación B (Task 9) no tiene que re-derivarlo ni asumir
 * TipoAda.DESCONOCIDO. */
public record FuncionAst(String nombre, TipoAda retorno, int linea, int columna) {
}
