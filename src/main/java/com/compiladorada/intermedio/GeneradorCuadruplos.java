package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Unario;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuádruplos por recorrido en postorden: cada operador produce un temporal
 * {@code t1, t2…} (reiniciados en cada expresión). Al final se asigna el
 * resultado a cada destino con {@code (:=, lugar, _, destino)}.
 */
public final class GeneradorCuadruplos {

    private final List<Cuadruplo> salida = new ArrayList<>();
    private int temporales;

    private GeneradorCuadruplos() {
    }

    public static List<Cuadruplo> generar(ArbolExpresion arbol, List<String> destinos) {
        GeneradorCuadruplos g = new GeneradorCuadruplos();
        String lugar = g.postorden(arbol);
        for (String destino : destinos) {
            g.salida.add(new Cuadruplo(":=", lugar, "", destino));
        }
        return g.salida;
    }

    /** Devuelve el "lugar" donde queda el valor: hoja tal cual, o un temporal. */
    private String postorden(ArbolExpresion a) {
        if (a instanceof Binario b) {
            String izq = postorden(b.izq());
            String der = postorden(b.der());
            String t = "t" + (++temporales);
            salida.add(new Cuadruplo(b.op(), izq, der, t));
            return t;
        }
        if (a instanceof Unario u) {
            String o = postorden(u.operando());
            String t = "t" + (++temporales);
            salida.add(new Cuadruplo(u.op(), o, "", t));
            return t;
        }
        return Notaciones.hoja(a);
    }
}
