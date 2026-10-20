package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Unario;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuádruplos de una expresión por recorrido en postorden: cada operador es un
 * paso numerado que deja su resultado en un temporal {@code T1, T2…}. La
 * numeración y los temporales se reinician en cada expresión. No se emiten
 * asignaciones: solo las operaciones de la expresión.
 */
public final class GeneradorCuadruplos {

    private final List<Cuadruplo> salida = new ArrayList<>();

    private GeneradorCuadruplos() {
    }

    public static List<Cuadruplo> generar(ArbolExpresion arbol) {
        GeneradorCuadruplos g = new GeneradorCuadruplos();
        g.postorden(arbol);
        return g.salida;
    }

    /** Devuelve el "lugar" donde queda el valor: la hoja tal cual, o el temporal del paso. */
    private String postorden(ArbolExpresion a) {
        if (a instanceof Binario b) {
            String izq = postorden(b.izq());
            String der = postorden(b.der());
            return paso(b.op(), izq, der);
        }
        if (a instanceof Unario u) {
            return paso(u.op(), postorden(u.operando()), "");
        }
        return Notaciones.hoja(a);
    }

    private String paso(String op, String arg1, String arg2) {
        int n = salida.size() + 1;
        String t = "T" + n;
        salida.add(new Cuadruplo(n, op, arg1, arg2, t));
        return t;
    }
}
