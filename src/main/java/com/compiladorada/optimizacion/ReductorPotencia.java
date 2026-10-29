package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ArbolExpresion;
import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import com.compiladorada.intermedio.Valor;

import java.util.function.Function;

/**
 * Reducción de potencia: cambia una operación cara por otra equivalente más
 * barata, solo cuando el operando es un identificador de tipo exactamente
 * Integer o Float (duplicar una llamada la evaluaría dos veces).
 * <ul>
 *   <li>{@code X ** 2} → {@code X * X}</li>
 *   <li>{@code X * 2}, {@code 2 * X} → {@code X + X}</li>
 *   <li>{@code X / 2.0} (Float) → {@code X * 0.5}</li>
 * </ul>
 * Devuelve la misma instancia si nada cambió.
 */
final class ReductorPotencia {

    enum ClaseNumerica { ENTERO, REAL, OTRA }

    private ReductorPotencia() {
    }

    static ArbolExpresion reducir(ArbolExpresion a, Function<String, ClaseNumerica> clase) {
        if (a instanceof Unario u) {
            ArbolExpresion op = reducir(u.operando(), clase);
            return op == u.operando() ? u : new Unario(u.op(), op);
        }
        if (!(a instanceof Binario b)) {
            return a;
        }
        ArbolExpresion izq = reducir(b.izq(), clase);
        ArbolExpresion der = reducir(b.der(), clase);
        Binario nuevo = izq == b.izq() && der == b.der() ? b : new Binario(b.op(), izq, der);
        ArbolExpresion reducido = reglas(nuevo, clase);
        return reducido != null ? reducido : nuevo;
    }

    private static ArbolExpresion reglas(Binario b, Function<String, ClaseNumerica> clase) {
        switch (b.op()) {
            case "**" -> {
                if (b.izq() instanceof Variable x && esEnteroConValor(b.der(), 2)
                        && clase.apply(x.nombre()) != ClaseNumerica.OTRA) {
                    return new Binario("*", x, x);
                }
            }
            case "*" -> {
                if (b.izq() instanceof Variable x && esDos(b.der(), clase.apply(x.nombre()))) {
                    return new Binario("+", x, x);
                }
                if (b.der() instanceof Variable x && esDos(b.izq(), clase.apply(x.nombre()))) {
                    return new Binario("+", x, x);
                }
            }
            case "/" -> {
                if (b.izq() instanceof Variable x && clase.apply(x.nombre()) == ClaseNumerica.REAL
                        && esRealConValor(b.der(), 2.0)) {
                    return new Binario("*", x, new Literal("0.5", Valor.real(0.5)));
                }
            }
            default -> { }
        }
        return null;
    }

    /** El literal 2 de la misma familia que el identificador (2 para Integer, 2.0 para Float). */
    private static boolean esDos(ArbolExpresion lit, ClaseNumerica clase) {
        return switch (clase) {
            case ENTERO -> esEnteroConValor(lit, 2);
            case REAL -> esRealConValor(lit, 2.0);
            case OTRA -> false;
        };
    }

    private static boolean esEnteroConValor(ArbolExpresion a, long valor) {
        return a instanceof Literal l && !l.valor().esReal() && l.valor().entero() == valor;
    }

    private static boolean esRealConValor(ArbolExpresion a, double valor) {
        return a instanceof Literal l && l.valor().esReal() && l.valor().real() == valor;
    }
}
