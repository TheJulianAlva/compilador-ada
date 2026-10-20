package com.compiladorada.intermedio;

/**
 * Un paso de la expresión en tres direcciones: {@code # | operador | arg1 |
 * arg2 | resultado}. {@code numero} empieza en 1 en cada expresión. Los
 * operadores de un solo argumento ({@code neg}, {@code abs}) dejan {@code arg2} vacío.
 */
public record Cuadruplo(int numero, String op, String arg1, String arg2, String resultado) {

    @Override
    public String toString() {
        return numero + " | " + op + " | " + arg1 + " | " + arg2 + " | " + resultado;
    }
}
