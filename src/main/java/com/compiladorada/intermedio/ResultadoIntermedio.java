package com.compiladorada.intermedio;

import java.util.List;
import java.util.stream.Collectors;

/** Salida de la fase de código intermedio: una fila por expresión, los valores finales y los métodos usados. */
public record ResultadoIntermedio(
        List<FilaIntermedia> filas,
        List<ValorFinal> valoresFinales,
        List<String> metodos) {

    public static final List<String> METODOS = List.of(
            "Notación prefija: recorrido en preorden (operador, operando izquierdo, operando derecho) "
                    + "del árbol de la expresión construido desde el AST.",
            "Notación posfija: recorrido en postorden (operando izquierdo, operando derecho, operador) "
                    + "del mismo árbol.",
            "Código intermedio: cuádruplos (operador, arg1, arg2, resultado) de tres direcciones, "
                    + "emitidos durante el recorrido en postorden con temporales t1, t2…",
            "Evaluación: recorrido en postorden del árbol con una memoria (variable → valor) que se "
                    + "actualiza con cada declaración o asignación; Integer de 32 bits, Float en doble precisión.");

    /** Reporte en texto plano (el que se guarda en {@code output/codigo_intermedio.txt}). */
    public String aTexto() {
        StringBuilder sb = new StringBuilder();
        for (FilaIntermedia f : filas) {
            sb.append("Línea ").append(f.linea()).append(": ").append(f.fuente()).append('\n');
            sb.append("  Prefijo   : ").append(f.prefijo()).append('\n');
            sb.append("  Posfijo   : ").append(f.posfijo()).append('\n');
            sb.append("  Cuádruplos: ").append(f.cuadruplos().stream()
                    .map(Cuadruplo::toString).collect(Collectors.joining("  "))).append('\n');
            sb.append("  Resultado : ").append(f.resultado());
            if (f.tipo() != null) {
                sb.append("  (").append(f.tipo()).append(')');
            }
            sb.append("\n\n");
        }
        sb.append("-- Métodos utilizados --\n");
        for (String m : metodos) {
            sb.append("* ").append(m).append('\n');
        }
        sb.append("\n-- Valores finales --\n");
        if (valoresFinales.isEmpty()) {
            sb.append("(sin variables numéricas)\n");
        }
        for (ValorFinal v : valoresFinales) {
            sb.append(v.nombre()).append(" : ").append(v.tipo()).append(" = ").append(v.texto()).append('\n');
        }
        return sb.toString();
    }
}
