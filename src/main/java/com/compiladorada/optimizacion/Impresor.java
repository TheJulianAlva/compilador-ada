package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ArbolExpresion;
import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;

import java.util.ArrayList;
import java.util.List;

/**
 * Árbol de expresión → lexemas Ada. Conserva la estructura del árbol: solo
 * escribe los paréntesis que hacen falta para que el texto vuelva a
 * analizarse al mismo árbol.
 *
 * <p>Niveles de precedencia: hoja 5; {@code abs} y {@code **} 4;
 * {@code * / mod rem} 3; {@code + -} y el signo menos unario 2. El signo
 * menos solo puede encabezar una cadena aditiva (por eso el parámetro
 * {@code cabeza}).
 */
final class Impresor {

    private Impresor() {
    }

    /**
     * @param requerido nivel mínimo que exige el contexto donde se inserta
     * @param cabeza    {@code true} si el contexto admite un menos unario al inicio
     */
    static List<String> imprimir(ArbolExpresion a, int requerido, boolean cabeza) {
        List<String> salida = new ArrayList<>();
        emitir(a, requerido, cabeza, salida);
        return salida;
    }

    private static int nivel(ArbolExpresion a) {
        if (a instanceof Unario u) {
            return "abs".equals(u.op()) ? 4 : 2;
        }
        if (a instanceof Binario b) {
            return switch (b.op()) {
                case "**" -> 4;
                case "*", "/", "mod", "rem" -> 3;
                default -> 2;
            };
        }
        return 5;
    }

    private static void emitir(ArbolExpresion a, int requerido, boolean cabeza, List<String> out) {
        boolean esNeg = a instanceof Unario u && "neg".equals(u.op());
        boolean parentesis = nivel(a) < requerido || (esNeg && !cabeza);
        if (parentesis) {
            out.add("(");
        }
        // dentro de paréntesis un menos inicial es válido
        boolean cab = parentesis || cabeza;
        if (a instanceof Literal l) {
            out.add(l.texto());
        } else if (a instanceof ArbolExpresion.Variable v) {
            out.add(v.nombre());
        } else if (a instanceof Unario u) {
            if ("abs".equals(u.op())) {
                out.add("abs");
                emitir(u.operando(), 5, false, out);
            } else {
                out.add("-");
                emitir(u.operando(), 3, false, out);
            }
        } else {
            Binario b = (Binario) a;
            switch (b.op()) {
                case "**" -> {
                    emitir(b.izq(), 5, false, out);
                    out.add("**");
                    emitir(b.der(), 5, false, out);
                }
                case "*", "/", "mod", "rem" -> {
                    emitir(b.izq(), 3, false, out);
                    out.add(b.op());
                    emitir(b.der(), 4, false, out);
                }
                default -> {
                    emitir(b.izq(), 2, cab, out);
                    out.add(b.op());
                    emitir(b.der(), 3, false, out);
                }
            }
        }
        if (parentesis) {
            out.add(")");
        }
    }
}
