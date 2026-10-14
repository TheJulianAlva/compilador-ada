package com.compiladorada.intermedio;

/**
 * Árbol de una expresión aritmética, construido desde el AST de JJTree por
 * {@link ConstructorArbol}. De este árbol salen las tres representaciones:
 * prefijo (preorden), posfijo y cuádruplos (postorden).
 */
public sealed interface ArbolExpresion {

    /** Literal numérico; {@code texto} es como se muestra en las notaciones. */
    record Literal(String texto, Valor valor) implements ArbolExpresion {
    }

    /** Variable o constante numérica, con el nombre tal como aparece en el fuente. */
    record Variable(String nombre) implements ArbolExpresion {
    }

    /** Operador unario: {@code neg} (signo menos) o {@code abs}. */
    record Unario(String op, ArbolExpresion operando) implements ArbolExpresion {
    }

    /** Operador binario: {@code + - * / mod rem **}. */
    record Binario(String op, ArbolExpresion izq, ArbolExpresion der) implements ArbolExpresion {
    }
}
