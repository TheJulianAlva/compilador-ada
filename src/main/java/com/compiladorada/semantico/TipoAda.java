package com.compiladorada.semantico;

import java.util.List;
import java.util.Map;

/**
 * Modelo de tipos del subconjunto de Ada soportado (ver docs/CLAUDE.md,
 * sección "Tipos de dato principales"). Jerarquía sellada: todas las
 * variantes se declaran en este mismo archivo, así que no hace falta un
 * `permits` explícito.
 */
public sealed interface TipoAda {

    String nombre();

    /**
     * Compatibilidad de asignación / paso de parámetro. Ada es fuertemente
     * tipado: por defecto solo es compatible con un tipo estructuralmente
     * igual (records comparan campo a campo, arreglos por su componente).
     * TipoAda.DESCONOCIDO es el centinela de error: siempre compatible, para
     * no encadenar errores tras uno ya reportado.
     */
    default boolean compatibleCon(TipoAda otro) {
        if (this == DESCONOCIDO || otro == DESCONOCIDO) {
            return true;
        }
        if (otro instanceof TipoSubtipo st) {
            return this.compatibleCon(st.base());
        }
        return this.equals(otro);
    }

    TipoAda DESCONOCIDO = new TipoDesconocido();
    TipoEscalar INTEGER = new TipoEscalar("Integer");
    TipoEscalar FLOAT = new TipoEscalar("Float");
    TipoEscalar BOOLEAN = new TipoEscalar("Boolean");
    TipoEscalar CHARACTER = new TipoEscalar("Character");
    TipoString STRING = new TipoString();

    /** Centinela de error: nunca se declara explícitamente en código Ada. */
    record TipoDesconocido() implements TipoAda {
        public String nombre() {
            return "<desconocido>";
        }
    }

    /** Integer, Float, Boolean, Character — instancias únicas arriba. */
    record TipoEscalar(String nombre) implements TipoAda {
    }

    /** String: arreglo de longitud fija de Character (ver CLAUDE.md). */
    record TipoString() implements TipoAda {
        public String nombre() {
            return "String";
        }
    }

    /** Entero con rango: {@code type Grado is range 0..100}. Los límites no
     * se evalúan en este corte (no hay evaluación de constantes estáticas
     * todavía); solo se modela la forma "entero restringido por rango". */
    record TipoRango(String nombre) implements TipoAda {
    }

    /** {@code type Color is (Rojo, Verde, Azul)}. */
    record TipoEnumerado(String nombre, List<String> literales) implements TipoAda {
    }

    /** {@code type Vector is array (1..10) of Integer}. */
    record TipoArreglo(String nombre, TipoAda componente) implements TipoAda {
    }

    /** {@code type Punto is record X, Y : Integer; end record}. Claves de
     * `campos` en minúsculas (Ada es case-insensitive). */
    record TipoRegistro(String nombre, Map<String, TipoAda> campos) implements TipoAda {
    }

    /** {@code subtype Edad is Integer range 0..120}. Compatible en ambos
     * sentidos con su tipo base — Ada trata subtipo y base como el mismo
     * tipo a efectos de asignación, solo añade una restricción de rango. */
    record TipoSubtipo(String nombre, TipoAda base) implements TipoAda {
        public boolean compatibleCon(TipoAda otro) {
            if (otro instanceof TipoSubtipo st) {
                return base.compatibleCon(st.base());
            }
            return base.compatibleCon(otro);
        }
    }

    /** Firma de un subprograma. {@code retorno == null} significa
     * procedimiento (sin valor de retorno); no nulo significa función. */
    record TipoSubprograma(String nombre, List<TipoAda> parametros, TipoAda retorno) implements TipoAda {
    }
}
