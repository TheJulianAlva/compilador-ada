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
     * tipado: por defecto solo es compatible con un tipo de igual nombre
     * declarado (comparación nominal, vía {@code equals()} de record — dos
     * tipos con la misma forma pero nombres distintos, p. ej. dos
     * {@code range} independientes, NO son compatibles entre sí).
     * TipoAda.DESCONOCIDO es el centinela de error: siempre compatible, para
     * no encadenar errores tras uno ya reportado. LITERAL_ENTERO/LITERAL_REAL
     * son los tipos "universales" de un literal numérico (ver docs/
     * unidad-0-diseno-ide.md §9.4): compatibles con cualquier tipo numérico
     * concreto de su misma familia (entero/real), en cualquiera de los dos
     * lados de la comparación.
     */
    default boolean compatibleCon(TipoAda otro) {
        if (this == DESCONOCIDO || otro == DESCONOCIDO) {
            return true;
        }
        if (otro instanceof TipoSubtipo st) {
            return this.compatibleCon(st.base());
        }
        if (this == LITERAL_ENTERO || otro == LITERAL_ENTERO) {
            TipoAda concreto = this == LITERAL_ENTERO ? otro : this;
            return concreto == LITERAL_ENTERO || esEnteroConcreto(concreto);
        }
        if (this == LITERAL_REAL || otro == LITERAL_REAL) {
            TipoAda concreto = this == LITERAL_REAL ? otro : this;
            return concreto == LITERAL_REAL || esRealConcreto(concreto);
        }
        return this.equals(otro);
    }

    private static boolean esEnteroConcreto(TipoAda t) {
        return t == INTEGER || t instanceof TipoRango;
    }

    private static boolean esRealConcreto(TipoAda t) {
        return t == FLOAT;
    }

    TipoAda DESCONOCIDO = new TipoDesconocido();
    TipoEscalar INTEGER = new TipoEscalar("Integer");
    TipoEscalar FLOAT = new TipoEscalar("Float");
    TipoEscalar BOOLEAN = new TipoEscalar("Boolean");
    TipoEscalar CHARACTER = new TipoEscalar("Character");
    TipoString STRING = new TipoString();
    TipoLiteralNumerico LITERAL_ENTERO = new TipoLiteralNumerico(true);
    TipoLiteralNumerico LITERAL_REAL = new TipoLiteralNumerico(false);

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

    /** Tipo "universal" de un literal numérico (ver {@link #compatibleCon}) —
     * nunca se declara explícitamente en código Ada ni se guarda como tipo
     * de un símbolo; solo existe transitoriamente al tipar una expresión.
     * Instancias únicas: {@link #LITERAL_ENTERO}/{@link #LITERAL_REAL}. */
    record TipoLiteralNumerico(boolean esEntero) implements TipoAda {
        public String nombre() {
            return esEntero ? "<entero universal>" : "<real universal>";
        }
    }
}
