package com.compiladorada.semantico;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ámbito léxico enlazado a su ámbito envolvente (padre null = ámbito
 * global/Standard). Las dos implementaciones comparten esta clase: la A
 * mantiene un único puntero "ámbito actual" que avanza durante el parseo
 * (ver VerificadorSemantico.entrarAmbito); la B construye el árbol completo
 * en su primera pasada y lo reutiliza en la segunda (ver
 * VerificadorSemantico.entrarAmbitoExistente).
 */
public final class Ambito {

    private final Ambito padre;
    private final Map<String, Simbolo> simbolos = new HashMap<>();

    public Ambito(Ambito padre) {
        this.padre = padre;
    }

    public Ambito padre() {
        return padre;
    }

    private static String clave(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /** true si se declaró; false si ya existía en ESTE ámbito (redeclaración). */
    public boolean declarar(Simbolo simbolo) {
        String clave = clave(simbolo.nombre());
        if (simbolos.containsKey(clave)) {
            return false;
        }
        simbolos.put(clave, simbolo);
        return true;
    }

    /** Busca en este ámbito y, si no está, en los envolventes; null si no
     * está declarado en ninguno. */
    public Simbolo resolver(String nombre) {
        String clave = clave(nombre);
        for (Ambito a = this; a != null; a = a.padre) {
            Simbolo s = a.simbolos.get(clave);
            if (s != null) {
                return s;
            }
        }
        return null;
    }
}
