package com.compiladorada.semantico;

import java.util.List;

/**
 * Valor almacenado en el nodo #Nombre del AST (ver Ada.jjt#nombre()): la
 * cadena de accesos (índices y campos) tal como aparece en el código
 * fuente, en orden. Existe para que la Implementación B (visitor de dos
 * pasadas, Task 9) pueda reproducir EXACTAMENTE los mismos chequeos que la
 * Implementación A hace en línea durante el parseo — sin este valor, B solo
 * podría resolver el identificador base y perdería la indexación/acceso a
 * campos, dejando de reportar los mismos errores que A.
 */
public record NombreAst(String base, int linea, int columna, List<Segmento> segmentos) {

    public sealed interface Segmento {
        /** Un "(" argumentos ")" — los tipos de los argumentos los computa
         * quien procesa esto visitando los hijos correspondientes del nodo
         * #Nombre (los argumentos SÍ son hijos reales del árbol: cada
         * expresion() que los compone burbujea su propio nodo). */
        record Indexacion(int cantidadArgumentos, int linea, int columna) implements Segmento {
        }

        record Campo(String nombre, int linea, int columna) implements Segmento {
        }
    }
}
