package com.compiladorada.semantico;

/** Valor guardado en el nodo #ComponenteRegistro: lo que
 * Ada.jjt#componenteRegistro() ya calculó durante el parseo
 * (Implementación A) — el tipo declarado del/de los campo(s) y la posición
 * a reportar — para que la Implementación B (VerificadorUsos) pueda
 * verificar el inicializador opcional sin volver a interpretar los
 * tokens. */
public record ComponenteRegistroAst(TipoAda tipo, int linea, int columna) {
}
