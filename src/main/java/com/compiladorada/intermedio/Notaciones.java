package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;

/** Notaciones de una expresión: prefija = preorden, posfija = postorden. */
public final class Notaciones {

    private Notaciones() {
    }

    /** Recorrido en preorden: operador, operando izquierdo, operando derecho. */
    public static String prefijo(ArbolExpresion a) {
        StringBuilder sb = new StringBuilder();
        preorden(a, sb);
        return sb.toString();
    }

    /** Recorrido en postorden: operando izquierdo, operando derecho, operador. */
    public static String posfijo(ArbolExpresion a) {
        StringBuilder sb = new StringBuilder();
        postorden(a, sb);
        return sb.toString();
    }

    private static void preorden(ArbolExpresion a, StringBuilder sb) {
        if (a instanceof Binario b) {
            agregar(sb, b.op());
            preorden(b.izq(), sb);
            preorden(b.der(), sb);
        } else if (a instanceof Unario u) {
            agregar(sb, u.op());
            preorden(u.operando(), sb);
        } else {
            agregar(sb, hoja(a));
        }
    }

    private static void postorden(ArbolExpresion a, StringBuilder sb) {
        if (a instanceof Binario b) {
            postorden(b.izq(), sb);
            postorden(b.der(), sb);
            agregar(sb, b.op());
        } else if (a instanceof Unario u) {
            postorden(u.operando(), sb);
            agregar(sb, u.op());
        } else {
            agregar(sb, hoja(a));
        }
    }

    static String hoja(ArbolExpresion a) {
        if (a instanceof Literal l) {
            return l.texto();
        }
        return ((Variable) a).nombre();
    }

    private static void agregar(StringBuilder sb, String token) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(token);
    }
}
