package com.compiladorada.intermedio;

import java.util.List;

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
            "Código intermedio: cuádruplos (#, operador, arg1, arg2, resultado) de tres direcciones, un paso "
                    + "por operación, emitidos durante el recorrido en postorden con temporales T1, T2…",
            "Evaluación: recorrido en postorden del árbol con una memoria (variable → valor) que se "
                    + "actualiza con cada declaración o asignación; Integer de 32 bits, Float en doble precisión.");

    /** Reporte en texto plano (el que se guarda en {@code output/codigo_intermedio.txt}). */
    public String aTexto() {
        StringBuilder sb = new StringBuilder();
        for (FilaIntermedia f : filas) {
            sb.append("Línea ").append(f.linea()).append(": ").append(f.fuente()).append("\n\n");
            sb.append(tablaCuadruplos(f.cuadruplos())).append('\n');
            sb.append("Prefijo : ").append(f.prefijo()).append('\n');
            sb.append("Posfijo : ").append(f.posfijo()).append('\n');
            sb.append("Resultado: ").append(f.resultado());
            if (f.tipo() != null) {
                sb.append(" (").append(f.tipo()).append(')');
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

    /** Tabla {@code # | Operador | Arg 1 | Arg 2 | Resultado} con columnas alineadas. */
    static String tablaCuadruplos(List<Cuadruplo> cuadruplos) {
        String[] titulos = {"#", "Operador", "Arg 1", "Arg 2", "Resultado"};
        int[] ancho = new int[titulos.length];
        for (int c = 0; c < titulos.length; c++) {
            ancho[c] = titulos[c].length();
        }
        for (Cuadruplo q : cuadruplos) {
            String[] celdas = celdas(q);
            for (int c = 0; c < celdas.length; c++) {
                ancho[c] = Math.max(ancho[c], celdas[c].length());
            }
        }
        StringBuilder sb = new StringBuilder();
        linea(sb, titulos, ancho);
        for (Cuadruplo q : cuadruplos) {
            linea(sb, celdas(q), ancho);
        }
        return sb.toString();
    }

    private static String[] celdas(Cuadruplo q) {
        return new String[] {Integer.toString(q.numero()), q.op(), q.arg1(), q.arg2(), q.resultado()};
    }

    private static void linea(StringBuilder sb, String[] celdas, int[] ancho) {
        for (int c = 0; c < celdas.length; c++) {
            sb.append(c == 0 ? "" : " | ");
            sb.append(c == celdas.length - 1 ? celdas[c] : String.format("%-" + ancho[c] + "s", celdas[c]));
        }
        sb.append('\n');
    }
}
