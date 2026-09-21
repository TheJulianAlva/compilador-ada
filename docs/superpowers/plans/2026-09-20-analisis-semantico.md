# Análisis Semántico (Unidad 1) — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir análisis semántico al compilador de Ada con dos implementaciones que comparten un mismo núcleo (tabla de símbolos + modelo de tipos + reglas), difiriendo solo en cómo se disparan: (A) acciones embebidas en `Ada.jjt` durante el parseo, (B) visitor de dos pasadas sobre el AST ya construido.

**Architecture:** Nuevo paquete `com.compiladorada.semantico` con el núcleo (`TipoAda`, `Simbolo`, `Ambito`, `MensajesSemanticos`, `VerificadorSemantico`). La Implementación A vive embebida en `Ada.jjt` (el `AdaParser` generado usa una instancia de `VerificadorSemantico`). La Implementación B vive en `com.compiladorada.semantico.visitor` (`RecolectorDeclaraciones` + `VerificadorUsos`, ambos `AdaParserDefaultVisitor` de JJTree) y opera sobre el mismo AST con su propia instancia de `VerificadorSemantico`.

**Tech Stack:** Java 17, Maven, JavaCC/JJTree 7.0.13 (`javacc-maven-plugin` 3.0.3, ya configurado), JUnit 5 (Jupiter).

**Spec:** `docs/superpowers/specs/2026-09-20-analisis-semantico-design.md`

## Global Constraints

- Java 17 (`maven.compiler.release=17` en `pom.xml`) — no usar record patterns (Java 21+); usar `instanceof T t` clásico.
- Ada es case-insensitive: toda clave de nombre en el núcleo semántico se compara en minúsculas (`Locale.ROOT`), igual que `PalabrasReservadas` y `IGNORE_CASE` en la gramática.
- El AST se genera en `com.compiladorada.sintactico.nodos` (paquete `nodePackage` del plugin); el parser en `com.compiladorada.generado`. Ambos son código GENERADO en `target/generated-sources` — nunca se edita a mano, solo `Ada.jjt`.
- Regla base (spec, sección "Regla base"): todo identificador declarado al menos una vez, con un solo tipo, antes de cualquier uso en su ámbito o uno anidado. Ada no tiene conversión implícita entre tipos con nombre distinto.
- Las dos implementaciones deben reportar exactamente los mismos errores para el mismo programa de entrada — ambas invocan únicamente los métodos de `VerificadorSemantico`, ninguna reimplementa una regla de tipos por su cuenta.
- Convención de mensajes en español, estilo `com.compiladorada.sintactico.TraductorMensajes` (minúsculas, nombres entre comillas simples).
- Limitaciones explícitas de este corte (documentar en comentarios donde aplique, no ocultarlas):
  - No hay evaluación estática de expresiones constantes: los límites de `range A..B` no se calculan, solo se valida que ambos extremos sean de tipos compatibles. `TipoAda.TipoRango` no almacena límites numéricos.
  - Arreglos multidimensionales (sintaxis `A(I, J)`) solo verifican el primer índice; es una limitación conocida, no un bug.
  - Nombres de excepciones (`raise`, `when X`) no se resuelven contra la tabla de símbolos en este corte — quedan fuera de alcance del modelo de tipos.
  - Una llamada a subprograma sin paréntesis (`Foo;` para un procedimiento sin parámetros) no valida aridad si el subprograma en realidad esperaba parámetros; solo la forma con paréntesis lo hace.

---

## Task 1: `ErrorCompilacion` gana la categoría `SEMANTICO`

**Files:**
- Modify: `src/main/java/com/compiladorada/errores/ErrorCompilacion.java`
- Test: `src/test/java/com/compiladorada/errores/ErrorCompilacionTest.java` (crear)

**Interfaces:**
- Produces: `ErrorCompilacion.Categoria.SEMANTICO` (enum constant), usado por todas las tareas siguientes.

- [ ] **Step 1: Escribir la prueba que falla**

```java
package com.compiladorada.errores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCompilacionTest {

    @Test
    void categoria_semantico_tiene_etiqueta_propia() {
        ErrorCompilacion e = new ErrorCompilacion(
                ErrorCompilacion.Categoria.SEMANTICO, 3, 7, "prueba");
        assertEquals("error semántico", e.categoria().etiqueta());
        assertEquals("archivo.ada:3:7: error semántico: prueba", e.formatear("archivo.ada"));
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=ErrorCompilacionTest`
Expected: FAIL — `Categoria.SEMANTICO` no existe (error de compilación).

- [ ] **Step 3: Añadir el valor al enum**

Editar `ErrorCompilacion.java`, en el `enum Categoria`:

```java
    public enum Categoria {
        LEXICO("error léxico"),
        SINTACTICO("error sintáctico"),
        SEMANTICO("error semántico");

        private final String etiqueta;

        Categoria(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=ErrorCompilacionTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/errores/ErrorCompilacion.java src/test/java/com/compiladorada/errores/ErrorCompilacionTest.java
git commit -m "feat(semantico): añade categoría SEMANTICO a ErrorCompilacion"
```

---

## Task 2: Modelo de tipos `TipoAda`

**Files:**
- Create: `src/main/java/com/compiladorada/semantico/TipoAda.java`
- Test: `src/test/java/com/compiladorada/semantico/TipoAdaTest.java`

**Interfaces:**
- Consumes: nada (tipo base del núcleo).
- Produces: `TipoAda` (interfaz sellada) con singletons `TipoAda.DESCONOCIDO`, `TipoAda.INTEGER`, `TipoAda.FLOAT`, `TipoAda.BOOLEAN`, `TipoAda.CHARACTER`, `TipoAda.STRING`; records anidados `TipoEscalar`, `TipoString`, `TipoRango(String nombre)`, `TipoEnumerado(String nombre, List<String> literales)`, `TipoArreglo(String nombre, TipoAda componente)`, `TipoRegistro(String nombre, Map<String,TipoAda> campos)`, `TipoSubtipo(String nombre, TipoAda base)`, `TipoSubprograma(String nombre, List<TipoAda> parametros, TipoAda retorno)`; método `nombre()` y `boolean compatibleCon(TipoAda otro)`. Usado por todas las tareas siguientes.

- [ ] **Step 1: Escribir las pruebas que fallan**

```java
package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TipoAdaTest {

    @Test
    void escalares_predefinidos_tienen_su_nombre() {
        assertEquals("Integer", TipoAda.INTEGER.nombre());
        assertEquals("Float", TipoAda.FLOAT.nombre());
        assertEquals("Boolean", TipoAda.BOOLEAN.nombre());
        assertEquals("Character", TipoAda.CHARACTER.nombre());
        assertEquals("String", TipoAda.STRING.nombre());
    }

    @Test
    void tipos_distintos_no_son_compatibles() {
        assertFalse(TipoAda.INTEGER.compatibleCon(TipoAda.FLOAT));
        assertTrue(TipoAda.INTEGER.compatibleCon(TipoAda.INTEGER));
    }

    @Test
    void desconocido_es_compatible_con_cualquier_cosa_para_no_encadenar_errores() {
        assertTrue(TipoAda.DESCONOCIDO.compatibleCon(TipoAda.INTEGER));
        assertTrue(TipoAda.INTEGER.compatibleCon(TipoAda.DESCONOCIDO));
    }

    @Test
    void subtipo_es_compatible_con_su_tipo_base_en_ambos_sentidos() {
        TipoAda.TipoSubtipo grado = new TipoAda.TipoSubtipo("Grado", TipoAda.INTEGER);
        assertTrue(grado.compatibleCon(TipoAda.INTEGER));
        assertTrue(TipoAda.INTEGER.compatibleCon(grado));
    }

    @Test
    void arreglo_y_registro_se_comparan_estructuralmente() {
        TipoAda.TipoArreglo a1 = new TipoAda.TipoArreglo("Vector", TipoAda.INTEGER);
        TipoAda.TipoArreglo a2 = new TipoAda.TipoArreglo("Vector", TipoAda.INTEGER);
        assertTrue(a1.compatibleCon(a2));

        TipoAda.TipoRegistro r1 = new TipoAda.TipoRegistro("Punto",
                Map.of("x", TipoAda.INTEGER, "y", TipoAda.INTEGER));
        TipoAda.TipoRegistro r2 = new TipoAda.TipoRegistro("Punto",
                Map.of("x", TipoAda.INTEGER, "y", TipoAda.INTEGER));
        assertTrue(r1.compatibleCon(r2));
    }

    @Test
    void enumerado_guarda_sus_literales_en_orden() {
        TipoAda.TipoEnumerado color = new TipoAda.TipoEnumerado("Color", List.of("Rojo", "Verde", "Azul"));
        assertEquals(List.of("Rojo", "Verde", "Azul"), color.literales());
    }

    @Test
    void subprograma_guarda_parametros_y_retorno() {
        TipoAda.TipoSubprograma f = new TipoAda.TipoSubprograma(
                "Suma", List.of(TipoAda.INTEGER, TipoAda.INTEGER), TipoAda.INTEGER);
        assertEquals(2, f.parametros().size());
        assertEquals(TipoAda.INTEGER, f.retorno());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=TipoAdaTest`
Expected: FAIL — el paquete `com.compiladorada.semantico` no existe todavía.

- [ ] **Step 3: Escribir `TipoAda.java`**

```java
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
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=TipoAdaTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/semantico/TipoAda.java src/test/java/com/compiladorada/semantico/TipoAdaTest.java
git commit -m "feat(semantico): añade el modelo de tipos TipoAda"
```

---

## Task 3: `Simbolo`

**Files:**
- Create: `src/main/java/com/compiladorada/semantico/Simbolo.java`
- Test: `src/test/java/com/compiladorada/semantico/SimboloTest.java`

**Interfaces:**
- Consumes: `TipoAda` (Task 2).
- Produces: `record Simbolo(String nombre, Simbolo.Categoria categoria, TipoAda tipo, boolean esConstante, int lineaDeclaracion, int columnaDeclaracion)` con `enum Categoria { VARIABLE, TIPO, PARAMETRO, SUBPROGRAMA, PAQUETE }`. Usado por `Ambito` (Task 4) y `VerificadorSemantico` (Task 6).

- [ ] **Step 1: Escribir la prueba que falla**

```java
package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimboloTest {

    @Test
    void guarda_sus_datos_tal_cual() {
        Simbolo s = new Simbolo("Contador", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 3, 5);
        assertEquals("Contador", s.nombre());
        assertEquals(Simbolo.Categoria.VARIABLE, s.categoria());
        assertEquals(TipoAda.INTEGER, s.tipo());
        assertFalse(s.esConstante());
        assertEquals(3, s.lineaDeclaracion());
        assertEquals(5, s.columnaDeclaracion());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=SimboloTest`
Expected: FAIL — `Simbolo` no existe.

- [ ] **Step 3: Escribir `Simbolo.java`**

```java
package com.compiladorada.semantico;

/**
 * Una entrada de la tabla de símbolos: nombre declarado, su categoría y tipo,
 * si es mutable, y dónde se declaró (usado en mensajes de error y por la
 * regla de declarado-antes-de-usar de la Implementación B, ver
 * VerificadorSemantico.resolverUsoConOrden).
 */
public record Simbolo(
        String nombre,
        Categoria categoria,
        TipoAda tipo,
        boolean esConstante,
        int lineaDeclaracion,
        int columnaDeclaracion) {

    public enum Categoria {
        VARIABLE, TIPO, PARAMETRO, SUBPROGRAMA, PAQUETE
    }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=SimboloTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/semantico/Simbolo.java src/test/java/com/compiladorada/semantico/SimboloTest.java
git commit -m "feat(semantico): añade el record Simbolo"
```

---

## Task 4: `Ambito` (ámbito léxico con enlace al padre)

**Files:**
- Create: `src/main/java/com/compiladorada/semantico/Ambito.java`
- Test: `src/test/java/com/compiladorada/semantico/AmbitoTest.java`

**Interfaces:**
- Consumes: `Simbolo` (Task 3).
- Produces: `class Ambito` con constructor `Ambito(Ambito padre)`, `Ambito padre()`, `boolean declarar(Simbolo)` (false si ya existe en ESTE ámbito), `Simbolo resolver(String nombre)` (busca en este ámbito y en los envolventes; null si no está en ninguno). Búsqueda case-insensitive. Usado por `VerificadorSemantico` (Task 6) y ambos drivers (Tasks 7 y 9).

- [ ] **Step 1: Escribir las pruebas que fallan**

```java
package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbitoTest {

    @Test
    void declara_y_resuelve_en_el_mismo_ambito() {
        Ambito a = new Ambito(null);
        assertTrue(a.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1)));
        assertNotNull(a.resolver("X"));
        assertEquals(TipoAda.INTEGER, a.resolver("X").tipo());
    }

    @Test
    void la_busqueda_no_distingue_mayusculas() {
        Ambito a = new Ambito(null);
        a.declarar(new Simbolo("Contador", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        assertNotNull(a.resolver("CONTADOR"));
        assertNotNull(a.resolver("contador"));
    }

    @Test
    void redeclarar_en_el_mismo_ambito_devuelve_false() {
        Ambito a = new Ambito(null);
        a.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        assertFalse(a.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.FLOAT, false, 2, 1)));
    }

    @Test
    void resuelve_hacia_afuera_por_los_ambitos_envolventes() {
        Ambito global = new Ambito(null);
        global.declarar(new Simbolo("Global", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        Ambito interno = new Ambito(global);
        assertNotNull(interno.resolver("Global"));
    }

    @Test
    void un_ambito_interno_puede_sombrear_al_externo() {
        Ambito global = new Ambito(null);
        global.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.INTEGER, false, 1, 1));
        Ambito interno = new Ambito(global);
        assertTrue(interno.declarar(new Simbolo("X", Simbolo.Categoria.VARIABLE, TipoAda.FLOAT, false, 2, 1)));
        assertEquals(TipoAda.FLOAT, interno.resolver("X").tipo());
        assertEquals(TipoAda.INTEGER, global.resolver("X").tipo());
    }

    @Test
    void resolver_nombre_no_declarado_devuelve_null() {
        Ambito a = new Ambito(null);
        assertNull(a.resolver("Fantasma"));
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=AmbitoTest`
Expected: FAIL — `Ambito` no existe.

- [ ] **Step 3: Escribir `Ambito.java`**

```java
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
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=AmbitoTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/semantico/Ambito.java src/test/java/com/compiladorada/semantico/AmbitoTest.java
git commit -m "feat(semantico): añade Ambito, el ámbito léxico enlazado al padre"
```

---

## Task 5: `MensajesSemanticos`

**Files:**
- Create: `src/main/java/com/compiladorada/semantico/MensajesSemanticos.java`
- Test: `src/test/java/com/compiladorada/semantico/MensajesSemanticosTest.java`

**Interfaces:**
- Consumes: nada (solo `String`s).
- Produces: métodos estáticos `redeclarado`, `tipoNoDeclarado`, `noDeclarado`, `noEsArreglo`, `indiceNoEntero`, `noEsRegistro`, `campoNoExiste`, `asignacionAConstante`, `tiposIncompatibles`, `operadorRequiereTipo`, `aridadIncorrecta`, `argumentoIncompatible` — todos `String -> ... -> String`. Usados exclusivamente por `VerificadorSemantico` (Task 6).

- [ ] **Step 1: Escribir la prueba que falla**

```java
package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MensajesSemanticosTest {

    @Test
    void mensajes_incluyen_los_nombres_relevantes_entre_comillas() {
        assertEquals("'X' ya está declarado en este ámbito", MensajesSemanticos.redeclarado("X"));
        assertEquals("'Integer' no es un tipo declarado", MensajesSemanticos.tipoNoDeclarado("Integer"));
        assertEquals("'X' no está declarado", MensajesSemanticos.noDeclarado("X"));
        assertEquals("'X' no es un arreglo", MensajesSemanticos.noEsArreglo("X"));
        assertEquals("el índice debe ser de tipo entero, se encontró 'Boolean'",
                MensajesSemanticos.indiceNoEntero("Boolean"));
        assertEquals("'X' no es un registro", MensajesSemanticos.noEsRegistro("X"));
        assertEquals("'Punto' no tiene el campo 'Z'", MensajesSemanticos.campoNoExiste("Z", "Punto"));
        assertEquals("no se puede asignar a la constante 'X'", MensajesSemanticos.asignacionAConstante("X"));
        assertEquals("tipos incompatibles: 'Integer' y 'Boolean'",
                MensajesSemanticos.tiposIncompatibles("Integer", "Boolean"));
        assertEquals("el operador '+' requiere operandos de tipo numérico",
                MensajesSemanticos.operadorRequiereTipo("+", "numérico"));
        assertEquals("'Suma' espera 2 argumento(s), se encontraron 1",
                MensajesSemanticos.aridadIncorrecta("Suma", 2, 1));
        assertEquals("el argumento 1 de 'Suma' debe ser 'Integer', se encontró 'Boolean'",
                MensajesSemanticos.argumentoIncompatible("Suma", 1, "Integer", "Boolean"));
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=MensajesSemanticosTest`
Expected: FAIL — `MensajesSemanticos` no existe.

- [ ] **Step 3: Escribir `MensajesSemanticos.java`**

```java
package com.compiladorada.semantico;

/**
 * Mensajes de error en español para el análisis semántico, mismo estilo que
 * com.compiladorada.sintactico.TraductorMensajes.
 */
public final class MensajesSemanticos {

    private MensajesSemanticos() {
    }

    public static String redeclarado(String nombre) {
        return "'" + nombre + "' ya está declarado en este ámbito";
    }

    public static String tipoNoDeclarado(String nombre) {
        return "'" + nombre + "' no es un tipo declarado";
    }

    public static String noDeclarado(String nombre) {
        return "'" + nombre + "' no está declarado";
    }

    public static String noEsArreglo(String nombre) {
        return "'" + nombre + "' no es un arreglo";
    }

    public static String indiceNoEntero(String tipoEncontrado) {
        return "el índice debe ser de tipo entero, se encontró '" + tipoEncontrado + "'";
    }

    public static String noEsRegistro(String nombre) {
        return "'" + nombre + "' no es un registro";
    }

    public static String campoNoExiste(String campo, String nombreRegistro) {
        return "'" + nombreRegistro + "' no tiene el campo '" + campo + "'";
    }

    public static String asignacionAConstante(String nombre) {
        return "no se puede asignar a la constante '" + nombre + "'";
    }

    public static String tiposIncompatibles(String a, String b) {
        return "tipos incompatibles: '" + a + "' y '" + b + "'";
    }

    public static String operadorRequiereTipo(String operador, String tipoEsperado) {
        return "el operador '" + operador + "' requiere operandos de tipo " + tipoEsperado;
    }

    public static String aridadIncorrecta(String nombre, int esperados, int recibidos) {
        return "'" + nombre + "' espera " + esperados + " argumento(s), se encontraron " + recibidos;
    }

    public static String argumentoIncompatible(String nombre, int posicion, String esperado, String recibido) {
        return "el argumento " + posicion + " de '" + nombre + "' debe ser '" + esperado
                + "', se encontró '" + recibido + "'";
    }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=MensajesSemanticosTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/semantico/MensajesSemanticos.java src/test/java/com/compiladorada/semantico/MensajesSemanticosTest.java
git commit -m "feat(semantico): añade los mensajes de error MensajesSemanticos"
```

---

## Task 6: `VerificadorSemantico` — el motor de reglas compartido

Este es el núcleo que ambas implementaciones invocan. Ninguna otra tarea
reimplementa una regla de tipos: todo pasa por aquí.

**Files:**
- Create: `src/main/java/com/compiladorada/semantico/VerificadorSemantico.java`
- Test: `src/test/java/com/compiladorada/semantico/VerificadorSemanticoTest.java`

**Interfaces:**
- Consumes: `TipoAda`, `Simbolo`, `Ambito`, `MensajesSemanticos` (Tasks 2-5), `ErrorCompilacion` (Task 1).
- Produces: `class VerificadorSemantico` con:
  - Ámbitos: `Ambito ambitoActual()`, `Ambito entrarAmbito()`, `void entrarAmbitoExistente(Ambito)`, `void salirAmbito()`, `int profundidad()`, `void salirHasta(int profundidad)`.
  - Declaración: `void declararVariables(List<String> nombres, TipoAda tipo, boolean esConstante, int linea, int columna)`, `void declararTipo(String nombre, TipoAda tipo, int linea, int columna)`, `void declararParametro(String nombre, TipoAda tipo, boolean modoOut, int linea, int columna)`, `void declararSubprograma(String nombre, List<TipoAda> parametros, TipoAda retorno, int linea, int columna)`, `void declararPaquete(String nombre, int linea, int columna)`.
  - Resolución: `TipoAda tipoDeclarado(String nombreTipo, int linea, int columna)`, `Simbolo resolverUso(String nombre, int linea, int columna)`, `Simbolo resolverUsoConOrden(String nombre, int lineaUso, int columnaUso)`, `TipoAda tipoDeIndexacion(...)`, `TipoAda tipoDeCampo(...)`, `TipoAda tipoDeLlamadaOIndexacion(...)`.
  - Verificación: `void verificarInicializacion(...)`, `void verificarAsignacionMutabilidad(...)`, `void verificarCondicion(...)`, `TipoAda tipoOperadorLogico(...)`, `tipoOperadorRelacional(...)`, `tipoDePertenencia(...)`, `tipoOperadorAditivo(...)`, `tipoOperadorMultiplicativo(...)`, `tipoOperadorUnario(...)`, `tipoOperadorPotencia(...)`, `tipoDeRango(...)`.
  - `List<ErrorCompilacion> errores()`.
  - Records anidados usados como valores de nodo del AST (Task 7 los guarda vía `jjtSetValue`, Task 9 los lee): `NombreResuelto(TipoAda tipo, Simbolo simboloBase)`, `FactorOp(String unario, boolean tienePotencia)`, `SimpleOp(String signo, List<String> operadores)`, `RelacionOp(String comparador, boolean pertenencia, boolean negada)`.
  - Usado por: Task 7 (Implementación A, embebida en `Ada.jjt`) y Task 9 (Implementación B, visitors).

- [ ] **Step 1: Escribir las pruebas que fallan**

```java
package com.compiladorada.semantico;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerificadorSemanticoTest {

    @Test
    void tipos_predefinidos_estan_disponibles_desde_el_inicio() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertEquals(TipoAda.INTEGER, v.tipoDeclarado("Integer", 1, 1));
        assertEquals(TipoAda.BOOLEAN, v.tipoDeclarado("Boolean", 1, 1));
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void declarar_variable_y_usarla_no_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        Simbolo s = v.resolverUso("X", 2, 1);
        assertNotNull(s);
        assertEquals(TipoAda.INTEGER, s.tipo());
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void usar_identificador_no_declarado_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertNull(v.resolverUso("Fantasma", 5, 3));
        assertEquals(1, v.errores().size());
        assertEquals(ErrorCompilacion.Categoria.SEMANTICO, v.errores().get(0).categoria());
        assertEquals("'Fantasma' no está declarado", v.errores().get(0).mensaje());
    }

    @Test
    void redeclarar_en_el_mismo_ambito_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        v.declararVariables(List.of("X"), TipoAda.FLOAT, false, 2, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("ya está declarado"));
    }

    @Test
    void sombrear_en_un_ambito_anidado_no_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        v.entrarAmbito();
        v.declararVariables(List.of("X"), TipoAda.FLOAT, false, 2, 1);
        assertTrue(v.errores().isEmpty());
        v.salirAmbito();
        assertEquals(TipoAda.INTEGER, v.resolverUso("X", 3, 1).tipo());
    }

    @Test
    void salir_de_ambito_hace_visible_de_nuevo_al_externo() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.entrarAmbito();
        v.declararVariables(List.of("Local"), TipoAda.INTEGER, false, 1, 1);
        v.salirAmbito();
        assertNull(v.resolverUso("Local", 2, 1));
    }

    @Test
    void asignar_a_constante_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("Pi"), TipoAda.FLOAT, true, 1, 1);
        Simbolo s = v.resolverUso("Pi", 2, 1);
        v.verificarAsignacionMutabilidad(s, 3, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("constante"));
    }

    @Test
    void inicializar_con_tipo_incompatible_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.verificarInicializacion(TipoAda.INTEGER, TipoAda.BOOLEAN, 1, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("incompatibles"));
    }

    @Test
    void operador_logico_exige_booleanos() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda r = v.tipoOperadorLogico(TipoAda.INTEGER, TipoAda.BOOLEAN, "and", 1, 1);
        assertEquals(TipoAda.DESCONOCIDO, r);
        assertEquals(1, v.errores().size());

        VerificadorSemantico v2 = new VerificadorSemantico();
        assertEquals(TipoAda.BOOLEAN, v2.tipoOperadorLogico(TipoAda.BOOLEAN, TipoAda.BOOLEAN, "and", 1, 1));
        assertTrue(v2.errores().isEmpty());
    }

    @Test
    void operador_aditivo_numerico_devuelve_el_mismo_tipo() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertEquals(TipoAda.INTEGER, v.tipoOperadorAditivo(TipoAda.INTEGER, TipoAda.INTEGER, "+", 1, 1));
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void concatenacion_acepta_string_y_character() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertEquals(TipoAda.STRING, v.tipoOperadorAditivo(TipoAda.STRING, TipoAda.CHARACTER, "&", 1, 1));
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void indexar_algo_que_no_es_arreglo_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda r = v.tipoDeIndexacion(TipoAda.INTEGER, TipoAda.INTEGER, 1, 1);
        assertEquals(TipoAda.DESCONOCIDO, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void indexar_un_arreglo_con_indice_no_entero_reporta_error_pero_devuelve_el_componente() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda.TipoArreglo vector = new TipoAda.TipoArreglo("Vector", TipoAda.FLOAT);
        TipoAda r = v.tipoDeIndexacion(vector, TipoAda.BOOLEAN, 1, 1);
        assertEquals(TipoAda.FLOAT, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void campo_inexistente_en_un_registro_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda.TipoRegistro punto = new TipoAda.TipoRegistro("Punto",
                java.util.Map.of("x", TipoAda.INTEGER, "y", TipoAda.INTEGER));
        TipoAda r = v.tipoDeCampo(punto, "Z", 1, 1);
        assertEquals(TipoAda.DESCONOCIDO, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void llamada_con_aridad_incorrecta_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararSubprograma("Suma", List.of(TipoAda.INTEGER, TipoAda.INTEGER), TipoAda.INTEGER, 1, 1);
        Simbolo suma = v.resolverUso("Suma", 2, 1);
        TipoAda r = v.tipoDeLlamadaOIndexacion(suma, suma.tipo(), List.of(TipoAda.INTEGER), 2, 1);
        assertEquals(TipoAda.INTEGER, r);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("2 argumento"));
    }

    @Test
    void llamada_con_tipo_de_argumento_incorrecto_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararSubprograma("Suma", List.of(TipoAda.INTEGER, TipoAda.INTEGER), TipoAda.INTEGER, 1, 1);
        Simbolo suma = v.resolverUso("Suma", 2, 1);
        TipoAda r = v.tipoDeLlamadaOIndexacion(suma, suma.tipo(),
                List.of(TipoAda.INTEGER, TipoAda.BOOLEAN), 2, 1);
        assertEquals(TipoAda.INTEGER, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void resolver_con_orden_rechaza_un_uso_antes_de_la_declaracion() {
        VerificadorSemantico v = new VerificadorSemantico();
        // "uso" en la línea 1, "declaración" en la línea 5: no es válido.
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 5, 1);
        Simbolo s = v.resolverUsoConOrden("X", 1, 1);
        assertNull(s);
        assertEquals(1, v.errores().size());
    }

    @Test
    void resolver_con_orden_acepta_un_uso_despues_de_la_declaracion() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        Simbolo s = v.resolverUsoConOrden("X", 5, 1);
        assertNotNull(s);
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void profundidad_y_salirHasta_restauran_el_ambito() {
        VerificadorSemantico v = new VerificadorSemantico();
        int prof = v.profundidad();
        v.entrarAmbito();
        v.entrarAmbito();
        v.salirHasta(prof);
        assertEquals(prof, v.profundidad());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=VerificadorSemanticoTest`
Expected: FAIL — `VerificadorSemantico` no existe.

- [ ] **Step 3: Escribir `VerificadorSemantico.java`**

```java
package com.compiladorada.semantico;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Motor de reglas semánticas compartido por las dos implementaciones (ver
 * docs/superpowers/specs/2026-09-20-analisis-semantico-design.md):
 * acciones embebidas en Ada.jjt durante el parseo (Implementación A) y un
 * visitor de dos pasadas sobre el AST (Implementación B). Cada
 * implementación crea su PROPIA instancia — con su propio árbol de
 * Ambito —, pero ambas invocan exactamente estos métodos, así que las dos
 * reportan los mismos errores para el mismo programa.
 */
public final class VerificadorSemantico {

    private Ambito actual;
    private final List<ErrorCompilacion> errores = new ArrayList<>();

    public VerificadorSemantico() {
        actual = new Ambito(null);
        sembrarStandard(actual);
    }

    private static void sembrarStandard(Ambito global) {
        global.declarar(new Simbolo("Integer", Simbolo.Categoria.TIPO, TipoAda.INTEGER, false, 0, 0));
        global.declarar(new Simbolo("Float", Simbolo.Categoria.TIPO, TipoAda.FLOAT, false, 0, 0));
        global.declarar(new Simbolo("Boolean", Simbolo.Categoria.TIPO, TipoAda.BOOLEAN, false, 0, 0));
        global.declarar(new Simbolo("Character", Simbolo.Categoria.TIPO, TipoAda.CHARACTER, false, 0, 0));
        global.declarar(new Simbolo("String", Simbolo.Categoria.TIPO, TipoAda.STRING, false, 0, 0));
        global.declarar(new Simbolo("True", Simbolo.Categoria.VARIABLE, TipoAda.BOOLEAN, true, 0, 0));
        global.declarar(new Simbolo("False", Simbolo.Categoria.VARIABLE, TipoAda.BOOLEAN, true, 0, 0));
    }

    public List<ErrorCompilacion> errores() {
        return errores;
    }

    private void reportar(int linea, int columna, String mensaje) {
        errores.add(new ErrorCompilacion(Categoria.SEMANTICO, linea, columna, mensaje));
    }

    // ---------------------------------------------------------------- ámbitos

    public Ambito ambitoActual() {
        return actual;
    }

    public Ambito entrarAmbito() {
        actual = new Ambito(actual);
        return actual;
    }

    /** Solo para la segunda pasada de la Implementación B: reentra a un
     * ámbito que la primera pasada ya creó y pobló, en vez de crear uno
     * vacío nuevo. */
    public void entrarAmbitoExistente(Ambito ambito) {
        actual = ambito;
    }

    public void salirAmbito() {
        actual = actual.padre();
    }

    public int profundidad() {
        int n = 0;
        for (Ambito a = actual; a.padre() != null; a = a.padre()) {
            n++;
        }
        return n;
    }

    /** Restaura el ámbito actual a la profundidad dada, saliendo de los
     * ámbitos intermedios. Usado en la recuperación de errores: si el
     * parseo de un procedimiento/función/paquete se abortó a mitad de
     * camino, esto evita dejar "actual" apuntando a un ámbito huérfano. */
    public void salirHasta(int profundidad) {
        while (profundidad() > profundidad) {
            salirAmbito();
        }
    }

    // ------------------------------------------------------------ declarar

    public void declararVariables(List<String> nombres, TipoAda tipo, boolean esConstante,
                                   int linea, int columna) {
        for (String nombre : nombres) {
            Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.VARIABLE, tipo, esConstante, linea, columna);
            if (!actual.declarar(simbolo)) {
                reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
            }
        }
    }

    public void declararTipo(String nombre, TipoAda tipo, int linea, int columna) {
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.TIPO, tipo, false, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    public void declararParametro(String nombre, TipoAda tipo, boolean modoOut, int linea, int columna) {
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.PARAMETRO, tipo, !modoOut, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    public void declararSubprograma(String nombre, List<TipoAda> parametros, TipoAda retorno,
                                     int linea, int columna) {
        TipoAda.TipoSubprograma firma = new TipoAda.TipoSubprograma(nombre, parametros, retorno);
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.SUBPROGRAMA, firma, false, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    public void declararPaquete(String nombre, int linea, int columna) {
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.PAQUETE, TipoAda.DESCONOCIDO, false, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    // ---------------------------------------------------------- resolución

    public TipoAda tipoDeclarado(String nombreTipo, int linea, int columna) {
        Simbolo s = actual.resolver(nombreTipo);
        if (s == null || s.categoria() != Simbolo.Categoria.TIPO) {
            reportar(linea, columna, MensajesSemanticos.tipoNoDeclarado(nombreTipo));
            return TipoAda.DESCONOCIDO;
        }
        return s.tipo();
    }

    /** Resuelve un identificador en una posición de uso. Usado por la
     * Implementación A: como se dispara durante el parseo, el orden
     * declarado-antes-de-usar ya lo garantiza el propio recorrido
     * izquierda-a-derecha del parser. */
    public Simbolo resolverUso(String nombre, int linea, int columna) {
        Simbolo s = actual.resolver(nombre);
        if (s == null) {
            reportar(linea, columna, MensajesSemanticos.noDeclarado(nombre));
        }
        return s;
    }

    /** Igual que resolverUso, pero para la segunda pasada de la
     * Implementación B: como la primera pasada ya registró TODAS las
     * declaraciones del árbol (incluidas las que aparecen después de este
     * uso en el texto fuente), hay que rechazar explícitamente una
     * resolución cuya declaración esté después del uso — si no, B validaría
     * usos-antes-de-declarar que A sí rechaza, y las dos implementaciones
     * dejarían de reportar los mismos errores. */
    public Simbolo resolverUsoConOrden(String nombre, int lineaUso, int columnaUso) {
        Simbolo s = actual.resolver(nombre);
        if (s == null || esDespues(s, lineaUso, columnaUso)) {
            reportar(lineaUso, columnaUso, MensajesSemanticos.noDeclarado(nombre));
            return null;
        }
        return s;
    }

    private boolean esDespues(Simbolo s, int lineaUso, int columnaUso) {
        if (s.lineaDeclaracion() == 0) {
            return false; // predefinido (Standard): siempre visible.
        }
        if (s.lineaDeclaracion() != lineaUso) {
            return s.lineaDeclaracion() > lineaUso;
        }
        return s.columnaDeclaracion() > columnaUso;
    }

    public TipoAda tipoDeIndexacion(TipoAda base, TipoAda tipoIndice, int linea, int columna) {
        if (base == TipoAda.DESCONOCIDO) {
            return TipoAda.DESCONOCIDO;
        }
        if (!(base instanceof TipoAda.TipoArreglo arreglo)) {
            reportar(linea, columna, MensajesSemanticos.noEsArreglo(base.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        if (!tipoIndice.compatibleCon(TipoAda.INTEGER)) {
            reportar(linea, columna, MensajesSemanticos.indiceNoEntero(tipoIndice.nombre()));
        }
        return arreglo.componente();
    }

    public TipoAda tipoDeCampo(TipoAda base, String campo, int linea, int columna) {
        if (base == TipoAda.DESCONOCIDO) {
            return TipoAda.DESCONOCIDO;
        }
        if (!(base instanceof TipoAda.TipoRegistro registro)) {
            reportar(linea, columna, MensajesSemanticos.noEsRegistro(base.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        TipoAda tipoCampo = registro.campos().get(campo.toLowerCase(Locale.ROOT));
        if (tipoCampo == null) {
            reportar(linea, columna, MensajesSemanticos.campoNoExiste(campo, base.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return tipoCampo;
    }

    /**
     * Resuelve tanto una llamada a subprograma ({@code Foo(1, 2)}) como una
     * indexación de arreglo ({@code A(1)}) — comparten sintaxis en la
     * gramática. {@code base} es el símbolo resuelto para el identificador
     * base SOLO si esta es la primera "(" de la cadena de acceso (null en
     * cualquier otro caso, ver Ada.jjt#nombre()); si es un SUBPROGRAMA se
     * verifica aridad y tipos de argumento, si no se trata como indexación
     * (solo se valida el primer argumento — no hay arreglos
     * multidimensionales en el subconjunto soportado).
     */
    public TipoAda tipoDeLlamadaOIndexacion(Simbolo base, TipoAda tipoActual,
                                             List<TipoAda> argumentos, int linea, int columna) {
        if (base != null && base.categoria() == Simbolo.Categoria.SUBPROGRAMA) {
            TipoAda.TipoSubprograma firma = (TipoAda.TipoSubprograma) base.tipo();
            if (argumentos.size() != firma.parametros().size()) {
                reportar(linea, columna, MensajesSemanticos.aridadIncorrecta(
                        base.nombre(), firma.parametros().size(), argumentos.size()));
            } else {
                for (int i = 0; i < argumentos.size(); i++) {
                    if (!firma.parametros().get(i).compatibleCon(argumentos.get(i))) {
                        reportar(linea, columna, MensajesSemanticos.argumentoIncompatible(
                                base.nombre(), i + 1, firma.parametros().get(i).nombre(),
                                argumentos.get(i).nombre()));
                    }
                }
            }
            return firma.retorno() != null ? firma.retorno() : TipoAda.DESCONOCIDO;
        }
        TipoAda tipoIndice = argumentos.isEmpty() ? TipoAda.DESCONOCIDO : argumentos.get(0);
        return tipoDeIndexacion(tipoActual, tipoIndice, linea, columna);
    }

    // -------------------------------------------------------- verificación

    public void verificarInicializacion(TipoAda destino, TipoAda origen, int linea, int columna) {
        if (!destino.compatibleCon(origen)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(destino.nombre(), origen.nombre()));
        }
    }

    public void verificarAsignacionMutabilidad(Simbolo destino, int linea, int columna) {
        if (destino != null && destino.esConstante()) {
            reportar(linea, columna, MensajesSemanticos.asignacionAConstante(destino.nombre()));
        }
    }

    public void verificarCondicion(TipoAda tipo, int linea, int columna) {
        if (!tipo.compatibleCon(TipoAda.BOOLEAN)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("condición", "Boolean"));
        }
    }

    public TipoAda tipoOperadorLogico(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if (!izq.compatibleCon(TipoAda.BOOLEAN) || !der.compatibleCon(TipoAda.BOOLEAN)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo(operador, "Boolean"));
            return TipoAda.DESCONOCIDO;
        }
        return TipoAda.BOOLEAN;
    }

    public TipoAda tipoOperadorRelacional(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if (!izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
        }
        return TipoAda.BOOLEAN;
    }

    public TipoAda tipoDePertenencia(TipoAda valor, TipoAda tipoRango, int linea, int columna) {
        if (!valor.compatibleCon(tipoRango)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(valor.nombre(), tipoRango.nombre()));
        }
        return TipoAda.BOOLEAN;
    }

    public TipoAda tipoOperadorAditivo(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if ("&".equals(operador)) {
            if (!esConcatenable(izq) || !esConcatenable(der)) {
                reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("&", "String/Character"));
                return TipoAda.DESCONOCIDO;
            }
            return TipoAda.STRING;
        }
        if (!esNumerico(izq) || !esNumerico(der) || !izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return izq;
    }

    public TipoAda tipoOperadorMultiplicativo(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if (!esNumerico(izq) || !esNumerico(der) || !izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return izq;
    }

    public TipoAda tipoOperadorUnario(String operador, TipoAda operando, int linea, int columna) {
        if ("not".equals(operador)) {
            if (!operando.compatibleCon(TipoAda.BOOLEAN)) {
                reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("not", "Boolean"));
                return TipoAda.DESCONOCIDO;
            }
            return TipoAda.BOOLEAN;
        }
        if (!esNumerico(operando)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo(operador, "numérico"));
            return TipoAda.DESCONOCIDO;
        }
        return operando;
    }

    public TipoAda tipoOperadorPotencia(TipoAda base, TipoAda exponente, int linea, int columna) {
        if (!esNumerico(base) || !exponente.compatibleCon(TipoAda.INTEGER)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("**", "numérico"));
            return TipoAda.DESCONOCIDO;
        }
        return base;
    }

    public TipoAda tipoDeRango(TipoAda izq, TipoAda der, int linea, int columna) {
        if (!izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return izq;
    }

    private boolean esNumerico(TipoAda t) {
        return t == TipoAda.INTEGER || t == TipoAda.FLOAT || t instanceof TipoAda.TipoRango
                || t == TipoAda.DESCONOCIDO;
    }

    private boolean esConcatenable(TipoAda t) {
        return t == TipoAda.STRING || t == TipoAda.CHARACTER || t == TipoAda.DESCONOCIDO;
    }

    // ------------------------------------------- valores de nodo del AST
    // (los guarda la Tarea 7 vía jjtSetValue, los lee la Tarea 9)

    /** Valor del nodo #Nombre. */
    public record NombreResuelto(TipoAda tipo, Simbolo simboloBase) {
    }

    /** Valor del nodo #Factor cuando se crea (unario y/o potencia). */
    public record FactorOp(String unario, boolean tienePotencia) {
    }

    /** Valor del nodo #Simple cuando se crea (signo inicial y/o cadena de
     * operadores aditivos). */
    public record SimpleOp(String signo, List<String> operadores) {
    }

    /** Valor del nodo #Relacion cuando se crea: o bien un comparador, o bien
     * una prueba de pertenencia ({@code in}/{@code not in}). */
    public record RelacionOp(String comparador, boolean pertenencia, boolean negada) {
    }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=VerificadorSemanticoTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/semantico/VerificadorSemantico.java src/test/java/com/compiladorada/semantico/VerificadorSemanticoTest.java
git commit -m "feat(semantico): añade VerificadorSemantico, el motor de reglas compartido"
```

---

## Task 7: Gramática — nodos/atributos de expresión + Implementación A embebida

La tarea más grande y de más riesgo. Modifica `Ada.jjt` para que:
(a) las producciones de expresión sinteticen un `TipoAda` y construyan nodos
AST reales (hoy son `void`), infraestructura que necesitan **ambas**
implementaciones; y (b) la Implementación A quede embebida: cada
declaración/uso/operación llama a `VerificadorSemantico` en línea durante el
parseo.

Dos construcciones JJTree usadas aquí se verificaron empíricamente antes de
escribir este plan (spikes descartables, no forman parte del repo):
`#Nombre(condiciónBooleana)` para creación condicional de nodo con una
expresión booleana arbitraria (no solo el atajo `>1`), y
`try {... } catch (ParseException e) {...} finally {...}` sobre una
expansión — ambas compilan con `javacc-maven-plugin` 3.0.3 /
javacc 7.0.13 (las versiones ya fijadas en `pom.xml`).

**Files:**
- Modify: `src/main/javacc/Ada.jjt`
- Test: `src/test/java/com/compiladorada/sintactico/AstExpresionesTest.java` (crear)

**Interfaces:**
- Consumes: `VerificadorSemantico`, `TipoAda`, `Simbolo` (Tasks 2, 3, 6).
- Produces: el `AdaParser` generado gana un campo `verificador` y el método
  `List<ErrorCompilacion> getErroresSemanticos()`; el AST generado gana los
  nodos `#Nombre`, `#Literal`, `#Expresion`, `#Relacion`, `#Simple`,
  `#Termino`, `#Factor` (los últimos cinco, condicionales — solo aparecen si
  hay un operador real, si no el hijo se "burbujea" directamente). Usado por
  Task 8 (exposición en `Compilador`/`ResultadoCompilacion`) y Task 9
  (Implementación B lee estos mismos nodos).

> **Nota:** los bloques de código below muestran el contenido FINAL de cada
> producción tocada. Edítense directamente sobre `src/main/javacc/Ada.jjt`
> reemplazando la producción homónima existente (o añadiéndola, si es
> nueva). El orden relativo de las producciones en el archivo no cambia.

- [ ] **Step 1: `PARSER_BEGIN` — añadir el campo `verificador`**

En el bloque `PARSER_BEGIN(AdaParser) ... PARSER_END(AdaParser)`, junto al
campo `erroresSintacticos` ya existente, añadir:

```java
    private final com.compiladorada.semantico.VerificadorSemantico verificador =
            new com.compiladorada.semantico.VerificadorSemantico();

    public java.util.List<com.compiladorada.errores.ErrorCompilacion> getErroresSemanticos() {
        return verificador.errores();
    }
```

(el resto del bloque `PARSER_BEGIN` — `erroresSintacticos`, `registrar`,
`esFantasma`, `sincronizar` — no cambia.)

- [ ] **Step 2: `tipoRef()` — de `void` a `TipoAda`**

Reemplazar:

```
void tipoRef() : {}
{
    <IDENTIFICADOR>
}
```

por:

```
TipoAda tipoRef() :
{ Token t; }
{
    t=<IDENTIFICADOR>
    { return verificador.tipoDeclarado(t.image, t.beginLine, t.beginColumn); }
}
```

- [ ] **Step 3: `parametros()`/`parametro()` — devolver info de parámetro sin declarar todavía**

Reemplazar ambas producciones por:

```
java.util.List<VerificadorSemantico.ParametroInfo> parametros() :
{
    java.util.List<VerificadorSemantico.ParametroInfo> lista =
            new java.util.ArrayList<VerificadorSemantico.ParametroInfo>();
    VerificadorSemantico.ParametroInfo p;
}
{
    p=parametro() { lista.add(p); } ( ";" p=parametro() { lista.add(p); } )*
    { return lista; }
}

VerificadorSemantico.ParametroInfo parametro() #Parametro :
{
    java.util.List<String> nombres = new java.util.ArrayList<String>();
    Token t;
    boolean modoOut = false;
    TipoAda tipo;
}
{
    t=<IDENTIFICADOR> { nombres.add(t.image); }
    ( "," t=<IDENTIFICADOR> { nombres.add(t.image); } )*
    ":"
    ( LOOKAHEAD(2) <KW_IN> <KW_OUT> { modoOut = true; }
    | <KW_IN>
    | <KW_OUT> { modoOut = true; }
    )?
    tipo=tipoRef()
    { return new VerificadorSemantico.ParametroInfo(nombres, tipo, modoOut); }
}
```

Añadir el record `ParametroInfo` a `VerificadorSemantico.java` (Task 6), como
otro record anidado junto a `NombreResuelto`/`FactorOp`/etc.:

```java
    /** Info cruda de un grupo de parámetros ("A, B : in Integer") antes de
     * declararlos — se calcula ANTES de entrar al ámbito del subprograma
     * (para conocer los tipos y poder declarar la firma del subprograma en
     * el ámbito EXTERNO), y se declara cada nombre DESPUÉS de entrar (ver
     * Ada.jjt#procedimiento()/funcion()). */
    public record ParametroInfo(List<String> nombres, TipoAda tipo, boolean modoOut) {
    }
```

- [ ] **Step 4: `procedimiento()` — declarar la firma, entrar/salir de ámbito**

Reemplazar la producción completa por:

```
void procedimiento() #Procedimiento :
{
    Token nombreTok;
    java.util.List<VerificadorSemantico.ParametroInfo> parametros = java.util.List.of();
    int prof;
}
{
    { prof = verificador.profundidad(); }
    try {
        <KW_PROCEDURE> nombreTok=<IDENTIFICADOR>
        ( "(" parametros=parametros() ")" )?
        {
            java.util.List<TipoAda> tiposParam = new java.util.ArrayList<TipoAda>();
            for (VerificadorSemantico.ParametroInfo p : parametros) {
                for (String n : p.nombres()) { tiposParam.add(p.tipo()); }
            }
            verificador.declararSubprograma(nombreTok.image, tiposParam, null,
                    nombreTok.beginLine, nombreTok.beginColumn);
            verificador.entrarAmbito();
            for (VerificadorSemantico.ParametroInfo p : parametros) {
                for (String n : p.nombres()) {
                    verificador.declararParametro(n, p.tipo(), p.modoOut(),
                            nombreTok.beginLine, nombreTok.beginColumn);
                }
            }
        }
        <KW_IS>
            ( declaracion() )*
        <KW_BEGIN>
            ( sentencia() )*
        ( bloqueExcepcion() )?
        <KW_END> ( <IDENTIFICADOR> )? ";"
    } catch (ParseException e) {
        registrar(e);
        sincronizar(KW_PROCEDURE, KW_FUNCTION, KW_PACKAGE);
    } finally {
        verificador.salirHasta(prof);
    }
}
```

Nótese el `finally`: garantiza que, aunque el `catch` se dispare a mitad del
cuerpo (después de `entrarAmbito()`), el ámbito siempre se restaura a la
profundidad de antes de entrar — si no, un procedimiento mal formado dejaría
`verificador` "atascado" dentro de su propio ámbito para el resto del
archivo.

- [ ] **Step 5: `funcion()` — igual que procedimiento(), más el tipo de retorno**

```
void funcion() #Funcion :
{
    Token nombreTok;
    java.util.List<VerificadorSemantico.ParametroInfo> parametros = java.util.List.of();
    TipoAda tipoRetorno;
    int prof;
}
{
    { prof = verificador.profundidad(); }
    try {
        <KW_FUNCTION> nombreTok=<IDENTIFICADOR>
        ( "(" parametros=parametros() ")" )?
        <KW_RETURN> tipoRetorno=tipoRef()
        {
            java.util.List<TipoAda> tiposParam = new java.util.ArrayList<TipoAda>();
            for (VerificadorSemantico.ParametroInfo p : parametros) {
                for (String n : p.nombres()) { tiposParam.add(p.tipo()); }
            }
            verificador.declararSubprograma(nombreTok.image, tiposParam, tipoRetorno,
                    nombreTok.beginLine, nombreTok.beginColumn);
            verificador.entrarAmbito();
            for (VerificadorSemantico.ParametroInfo p : parametros) {
                for (String n : p.nombres()) {
                    verificador.declararParametro(n, p.tipo(), p.modoOut(),
                            nombreTok.beginLine, nombreTok.beginColumn);
                }
            }
        }
        <KW_IS>
            ( declaracion() )*
        <KW_BEGIN>
            ( sentencia() )*
        ( bloqueExcepcion() )?
        <KW_END> ( <IDENTIFICADOR> )? ";"
    } catch (ParseException e) {
        registrar(e);
        sincronizar(KW_PROCEDURE, KW_FUNCTION, KW_PACKAGE);
    } finally {
        verificador.salirHasta(prof);
    }
}
```

- [ ] **Step 6: `paquete()` — declarar el paquete, entrar/salir de ámbito en ambas formas**

```
void paquete() #Paquete :
{
    Token nombreTok;
    int prof;
}
{
    { prof = verificador.profundidad(); }
    try {
        <KW_PACKAGE>
        (
            <KW_BODY> nombreTok=<IDENTIFICADOR>
            {
                verificador.declararPaquete(nombreTok.image, nombreTok.beginLine, nombreTok.beginColumn);
                verificador.entrarAmbito();
            }
            <KW_IS>
                ( declaracion() )*
            ( <KW_BEGIN> ( sentencia() )* )?
            ( bloqueExcepcion() )?
            <KW_END> ( <IDENTIFICADOR> )? ";"
          |
            nombreTok=<IDENTIFICADOR>
            {
                verificador.declararPaquete(nombreTok.image, nombreTok.beginLine, nombreTok.beginColumn);
                verificador.entrarAmbito();
            }
            <KW_IS>
                ( declaracion() )*
            ( <KW_PRIVATE> ( declaracion() )* )?
            <KW_END> ( <IDENTIFICADOR> )? ";"
        )
    } catch (ParseException e) {
        registrar(e);
        sincronizar(KW_PROCEDURE, KW_FUNCTION, KW_PACKAGE);
    } finally {
        verificador.salirHasta(prof);
    }
}
```

- [ ] **Step 7: `unidadNoReconocida()` — envolver también en ámbito**

Reemplazar por:

```
void unidadNoReconocida() #Procedimiento :
{ int prof; }
{
    { prof = verificador.profundidad(); }
    {
        Token ofensor = getToken(1);
        erroresSintacticos.add(new com.compiladorada.errores.ErrorCompilacion(
                com.compiladorada.errores.ErrorCompilacion.Categoria.SINTACTICO,
                ofensor.beginLine, ofensor.beginColumn,
                com.compiladorada.sintactico.TraductorMensajes.esperadoUnidad(ofensor.image)));
        getNextToken();
        verificador.entrarAmbito();
    }
    try {
        <IDENTIFICADOR> ( "(" parametros() ")" )? <KW_IS>
            ( declaracion() )*
        <KW_BEGIN>
            ( sentencia() )*
        ( bloqueExcepcion() )?
        <KW_END> ( <IDENTIFICADOR> )? ";"
    } catch (ParseException e) {
        registrar(e);
        sincronizar(KW_PROCEDURE, KW_FUNCTION, KW_PACKAGE);
    } finally {
        verificador.salirHasta(prof);
    }
}
```

- [ ] **Step 8: `declaracionVar()`, `declaracionTipo()`, `declaracionSubtipo()`, `definicionTipo()`, `componenteRegistro()`**

Reemplazar las cinco producciones por:

```
void declaracionVar() #DeclaracionVar :
{
    java.util.List<String> nombres = new java.util.ArrayList<String>();
    Token inicio;
    Token t;
    boolean esConstante = false;
    TipoAda tipo;
    TipoAda tipoInicial;
}
{
    t=<IDENTIFICADOR> { inicio = t; nombres.add(t.image); }
    ( "," t=<IDENTIFICADOR> { nombres.add(t.image); } )*
    ":" ( <KW_CONSTANT> { esConstante = true; } )?
    tipo=tipoRef()
    {
        verificador.declararVariables(nombres, tipo, esConstante,
                inicio.beginLine, inicio.beginColumn);
    }
    ( ":=" tipoInicial=expresion()
        { verificador.verificarInicializacion(tipo, tipoInicial, inicio.beginLine, inicio.beginColumn); }
    )?
    ";"
}

void declaracionTipo() #DeclaracionTipo :
{ Token t; TipoAda tipo; }
{
    t=<IDENTIFICADOR> <KW_IS> tipo=definicionTipo() ";"
    { verificador.declararTipo(t.image, tipo, t.beginLine, t.beginColumn); }
}

void declaracionSubtipo() #DeclaracionSubtipo :
{ Token t; TipoAda base; }
{
    t=<IDENTIFICADOR> <KW_IS> base=tipoRef() ( <KW_RANGE> rango() )? ";"
    {
        verificador.declararTipo(t.image, new TipoAda.TipoSubtipo(t.image, base),
                t.beginLine, t.beginColumn);
    }
}

TipoAda definicionTipo() #DefinicionTipo :
{
    Token t;
    TipoAda componente;
    java.util.LinkedHashMap<String, TipoAda> campos;
    java.util.List<String> literales;
}
{
    <KW_RANGE> rango()
    { return new TipoAda.TipoRango("<rango>"); }
  |
    "(" t=<IDENTIFICADOR> { literales = new java.util.ArrayList<String>(); literales.add(t.image); }
        ( "," t=<IDENTIFICADOR> { literales.add(t.image); } )* ")"
    { return new TipoAda.TipoEnumerado("<enumerado>", literales); }
  |
    <KW_RECORD>
    { campos = new java.util.LinkedHashMap<String, TipoAda>(); }
    ( componenteRegistro(campos) )*
    <KW_END> <KW_RECORD>
    { return new TipoAda.TipoRegistro("<registro>", campos); }
  |
    <KW_ARRAY> "(" rango() ")" <KW_OF> componente=tipoRef()
    { return new TipoAda.TipoArreglo("<arreglo>", componente); }
}

void componenteRegistro(java.util.Map<String, TipoAda> campos) #ComponenteRegistro :
{
    java.util.List<String> nombres = new java.util.ArrayList<String>();
    Token t;
    TipoAda tipo;
    TipoAda tipoInicial;
}
{
    t=<IDENTIFICADOR> { nombres.add(t.image); }
    ( "," t=<IDENTIFICADOR> { nombres.add(t.image); } )*
    ":" tipo=tipoRef()
    {
        for (String n : nombres) {
            campos.put(n.toLowerCase(java.util.Locale.ROOT), tipo);
        }
    }
    ( ":=" tipoInicial=expresion()
        { verificador.verificarInicializacion(tipo, tipoInicial, t.beginLine, t.beginColumn); }
    )?
    ";"
}
```

Nota: `TipoAda.TipoRango("<rango>")` y los nombres `"<enumerado>"`,
`"<registro>"`, `"<arreglo>"` son placeholders de nombre — el nombre real
del tipo (p. ej. "Color", "Punto") lo asigna quien LLAMA a `definicionTipo()`
(`declaracionTipo()`), que ya conoce el identificador declarado pero no lo
pasa hacia dentro en este corte. Es una simplificación aceptada: el
`nombre()` de estos records solo se usa hoy en mensajes de error
(`tiposIncompatibles`, etc.), donde mostrar `<registro>` en vez de `Punto` es
menos informativo pero no incorrecto. Documentarlo como limitación conocida
si se decide no arreglarlo en esta pasada.

- [ ] **Step 9: `rango()` — de `void` a `TipoAda`**

```
TipoAda rango() :
{ TipoAda izq; TipoAda der; Token inicio; }
{
    { inicio = getToken(1); }
    izq=expresion() ".." der=expresion()
    { return verificador.tipoDeRango(izq, der, inicio.beginLine, inicio.beginColumn); }
}
```

- [ ] **Step 10: `nombre()` — nodo `#Nombre`, resolución + cadena de accesos**

```
VerificadorSemantico.NombreResuelto nombre() #Nombre :
{
    Token base;
    Simbolo simboloBase;
    TipoAda tipo;
    java.util.List<TipoAda> argumentos;
    Token campo;
    boolean esPrimero = true;
    java.util.List<NombreAst.Segmento> segmentos = new java.util.ArrayList<NombreAst.Segmento>();
}
{
    base=<IDENTIFICADOR>
    {
        simboloBase = verificador.resolverUso(base.image, base.beginLine, base.beginColumn);
        tipo = simboloBase != null ? simboloBase.tipo() : TipoAda.DESCONOCIDO;
    }
    (
        "(" argumentos=argumentosExpresion() ")"
            {
                tipo = verificador.tipoDeLlamadaOIndexacion(
                        esPrimero ? simboloBase : null, tipo, argumentos,
                        base.beginLine, base.beginColumn);
                segmentos.add(new NombreAst.Segmento.Indexacion(
                        argumentos.size(), base.beginLine, base.beginColumn));
                esPrimero = false;
            }
      | "." campo=<IDENTIFICADOR>
            {
                tipo = verificador.tipoDeCampo(tipo, campo.image, campo.beginLine, campo.beginColumn);
                segmentos.add(new NombreAst.Segmento.Campo(campo.image, campo.beginLine, campo.beginColumn));
                esPrimero = false;
            }
    )*
    {
        jjtThis.jjtSetValue(new NombreAst(base.image, base.beginLine, base.beginColumn, segmentos));
        return new VerificadorSemantico.NombreResuelto(tipo, simboloBase);
    }
}

java.util.List<TipoAda> argumentosExpresion() :
{
    java.util.List<TipoAda> lista = new java.util.ArrayList<TipoAda>();
    TipoAda t;
}
{
    t=expresion() { lista.add(t); } ( "," t=expresion() { lista.add(t); } )*
    { return lista; }
}
```

Crear el pequeño archivo de datos que el nodo `#Nombre` guarda (necesario
para que la Implementación B, Task 9, pueda reproducir exactamente la misma
cadena de accesos sin volver a parsear texto):

`src/main/java/com/compiladorada/semantico/NombreAst.java`:

```java
package com.compiladorada.semantico;

import java.util.List;

/**
 * Valor almacenado en el nodo #Nombre del AST (ver Ada.jjt#nombre()): la
 * cadena de accesos (índices y campos) tal como aparece en el código
 * fuente, en orden. Existe para que la Implementación B (visitor de dos
 * pasadas, Task 9) pueda reproducir EXACTAMENTE los mismos chequeos que la
 * Implementación A hace en línea durante el parseo — sin este valor, B solo
 * podría resolver el identificador base y perdería la indexación/acceso a
 * campos, dejando de reportar los mismos errores que A.
 */
public record NombreAst(String base, int linea, int columna, List<Segmento> segmentos) {

    public sealed interface Segmento {
        /** Un "(" argumentos ")" — los tipos de los argumentos los computa
         * quien procesa esto visitando los hijos correspondientes del nodo
         * #Nombre (los argumentos SÍ son hijos reales del árbol: cada
         * expresion() que los compone burbujea su propio nodo). */
        record Indexacion(int cantidadArgumentos, int linea, int columna) implements Segmento {
        }

        record Campo(String nombre, int linea, int columna) implements Segmento {
        }
    }
}
```

- [ ] **Step 11: `literal()` (nueva) y `primario()`**

```
TipoAda literal() #Literal :
{ Token t; }
{
    ( t=<ENTERO> { jjtThis.jjtSetValue(t.image); return TipoAda.INTEGER; } )
  | ( t=<REAL> { jjtThis.jjtSetValue(t.image); return TipoAda.FLOAT; } )
  | ( t=<BASADO> { jjtThis.jjtSetValue(t.image); return TipoAda.INTEGER; } )
  | ( t=<CARACTER> { jjtThis.jjtSetValue(t.image); return TipoAda.CHARACTER; } )
  | ( t=<CADENA> { jjtThis.jjtSetValue(t.image); return TipoAda.STRING; } )
  | ( <KW_NULL> { jjtThis.jjtSetValue("null"); return TipoAda.DESCONOCIDO; } )
}

TipoAda primario() :
{ TipoAda t; VerificadorSemantico.NombreResuelto nr; }
{
    "(" t=expresion() ")" { return t; }
  | t=literal() { return t; }
  | nr=nombre() { return nr.tipo(); }
}
```

(la elección entre `literal()` y `nombre()` es LL(1) directa: sus conjuntos
de tokens iniciales — `ENTERO/REAL/BASADO/CARACTER/CADENA/KW_NULL` vs.
`IDENTIFICADOR` — son disjuntos, igual que en la gramática original.)

- [ ] **Step 12: `factor()`, `termino()`, `simple()`, `relacion()`, `expresion()`**

```
TipoAda factor() #Factor(operadorUnario != null || tienePotencia) :
{
    TipoAda base;
    TipoAda exp;
    Token inicio;
    String operadorUnario = null;
    boolean tienePotencia = false;
}
{
    { inicio = getToken(1); }
    ( <KW_ABS> { operadorUnario = "abs"; } | <KW_NOT> { operadorUnario = "not"; } )?
    base=primario()
    {
        if (operadorUnario != null) {
            base = verificador.tipoOperadorUnario(operadorUnario, base, inicio.beginLine, inicio.beginColumn);
        }
    }
    ( "**" exp=primario()
        {
            tienePotencia = true;
            base = verificador.tipoOperadorPotencia(base, exp, inicio.beginLine, inicio.beginColumn);
        }
    )?
    { jjtThis.jjtSetValue(new VerificadorSemantico.FactorOp(operadorUnario, tienePotencia)); return base; }
}

TipoAda termino() #Termino(huboOperador) :
{
    TipoAda izq;
    TipoAda der;
    Token opTok = null;
    boolean huboOperador = false;
    java.util.List<String> ops = new java.util.ArrayList<String>();
}
{
    izq=factor()
    (
        ( "*" { opTok = token; } | "/" { opTok = token; } | <KW_MOD> { opTok = token; } | <KW_REM> { opTok = token; } )
        der=factor()
        {
            huboOperador = true;
            ops.add(opTok.image);
            izq = verificador.tipoOperadorMultiplicativo(izq, der, opTok.image, opTok.beginLine, opTok.beginColumn);
        }
    )*
    { jjtThis.jjtSetValue(ops); return izq; }
}

TipoAda simple() #Simple(huboOperador) :
{
    TipoAda izq;
    TipoAda der;
    Token opTok = null;
    Token inicio;
    String signo = null;
    boolean huboOperador = false;
    java.util.List<String> ops = new java.util.ArrayList<String>();
}
{
    { inicio = getToken(1); }
    ( "+" { signo = "+"; } | "-" { signo = "-"; } )?
    izq=termino()
    {
        if (signo != null) {
            huboOperador = true;
            izq = verificador.tipoOperadorUnario(signo, izq, inicio.beginLine, inicio.beginColumn);
        }
    }
    (
        ( "+" { opTok = token; } | "-" { opTok = token; } | "&" { opTok = token; } )
        der=termino()
        {
            huboOperador = true;
            ops.add(opTok.image);
            izq = verificador.tipoOperadorAditivo(izq, der, opTok.image, opTok.beginLine, opTok.beginColumn);
        }
    )*
    { jjtThis.jjtSetValue(new VerificadorSemantico.SimpleOp(signo, ops)); return izq; }
}

TipoAda relacion() #Relacion(huboOperador) :
{
    TipoAda izq;
    TipoAda der;
    Token opTok = null;
    boolean huboOperador = false;
    String comparador = null;
    boolean pertenencia = false;
    boolean negada = false;
}
{
    izq=simple()
    (
        ( ( "=" | <NEQ> | "<" | <LEQ> | ">" | <GEQ> ) { opTok = token; comparador = opTok.image; }
          der=simple()
          {
              huboOperador = true;
              izq = verificador.tipoOperadorRelacional(izq, der, comparador, opTok.beginLine, opTok.beginColumn);
          }
        )
      | ( ( <KW_NOT> { negada = true; } )? <KW_IN> { opTok = token; pertenencia = true; }
          der=rango()
          {
              huboOperador = true;
              izq = verificador.tipoDePertenencia(izq, der, opTok.beginLine, opTok.beginColumn);
          }
        )
    )?
    {
        jjtThis.jjtSetValue(new VerificadorSemantico.RelacionOp(comparador, pertenencia, negada));
        return izq;
    }
}

TipoAda expresion() #Expresion(huboOperador) :
{
    TipoAda izq;
    TipoAda der;
    Token opTok = null;
    String operador = null;
    boolean huboOperador = false;
    java.util.List<String> ops = new java.util.ArrayList<String>();
}
{
    izq=relacion()
    (
        (
            <KW_AND> { opTok = token; }
                ( LOOKAHEAD(2) <KW_THEN> { operador = "and then"; } | { operador = "and"; } )
          | <KW_OR> { opTok = token; }
                ( LOOKAHEAD(2) <KW_ELSE> { operador = "or else"; } | { operador = "or"; } )
          | <KW_XOR> { opTok = token; operador = "xor"; }
        )
        der=relacion()
        {
            huboOperador = true;
            ops.add(operador);
            izq = verificador.tipoOperadorLogico(izq, der, operador, opTok.beginLine, opTok.beginColumn);
        }
    )*
    { jjtThis.jjtSetValue(ops); return izq; }
}
```

- [ ] **Step 13: `asignacion()` y `llamadaProc()`**

```
void asignacion() #Asignacion :
{ VerificadorSemantico.NombreResuelto destino; TipoAda origen; Token inicio; }
{
    { inicio = getToken(1); }
    destino=nombre() ":=" origen=expresion() ";"
    {
        verificador.verificarAsignacionMutabilidad(destino.simboloBase(), inicio.beginLine, inicio.beginColumn);
        verificador.verificarInicializacion(destino.tipo(), origen, inicio.beginLine, inicio.beginColumn);
    }
}

void llamadaProc() #LlamadaProc : {}
{
    nombre() ";"
}
```

- [ ] **Step 14: `sentenciaIf()`, `sentenciaFor()`, `sentenciaWhile()`**

```
void sentenciaIf() #If :
{ TipoAda tCond; Token inicio; }
{
    <KW_IF> { inicio = getToken(1); } tCond=expresion()
        { verificador.verificarCondicion(tCond, inicio.beginLine, inicio.beginColumn); }
    <KW_THEN> ( sentencia() )*
    ( <KW_ELSIF> { inicio = getToken(1); } tCond=expresion()
        { verificador.verificarCondicion(tCond, inicio.beginLine, inicio.beginColumn); }
      <KW_THEN> ( sentencia() )* )*
    ( <KW_ELSE> ( sentencia() )* )?
    <KW_END> <KW_IF> ";"
}

void sentenciaFor() #For :
{ Token t; TipoAda tipoRango; int prof; }
{
    <KW_FOR> t=<IDENTIFICADOR> <KW_IN> ( <KW_REVERSE> )? tipoRango=rango()
    {
        prof = verificador.profundidad();
        verificador.entrarAmbito();
        verificador.declararVariables(java.util.List.of(t.image), tipoRango, true,
                t.beginLine, t.beginColumn);
    }
    try {
        <KW_LOOP>
            ( sentencia() )*
        <KW_END> <KW_LOOP> ";"
    } catch (ParseException e) {
        registrar(e);
        sincronizar(PYC, KW_END, KW_ELSIF, KW_ELSE, KW_EXCEPTION, KW_WHEN);
        if (getToken(1).kind == PYC) {
            getNextToken();
        }
    } finally {
        verificador.salirHasta(prof);
    }
}

void sentenciaWhile() #While :
{ TipoAda tCond; Token inicio; }
{
    <KW_WHILE> { inicio = getToken(1); } tCond=expresion()
        { verificador.verificarCondicion(tCond, inicio.beginLine, inicio.beginColumn); }
    <KW_LOOP>
        ( sentencia() )*
    <KW_END> <KW_LOOP> ";"
}
```

`sentenciaFor()` cambia de forma: antes su recuperación de errores la
heredaba entera de `sentencia()` (que ya la envuelve en su propio
`try/catch`); ahora necesita SU PROPIO `try/catch/finally` porque abre un
ámbito (para la variable de control) que debe cerrarse incluso si el cuerpo
del bucle falla al parsear. Se reutiliza el mismo conjunto de tokens de
sincronización que ya usa `sentencia()`.

`sentenciaRaise()`, `bloqueExcepcion()` y `manejador()` **no cambian**: los
nombres de excepción no se resuelven contra la tabla de símbolos en este
corte (ver "Global Constraints" arriba).

- [ ] **Step 15: Compilar y correr toda la suite existente (regresión)**

Run: `mvn -q -o compile`
Expected: compila sin errores. Si `#Nombre(condición)` o el `finally` sobre
una expansión fallan aquí (no debería, se verificaron antes de escribir este
plan), el mensaje de javacc señala la línea exacta — revisar la sintaxis
contra los ejemplos de este step antes de asumir que la construcción no está
soportada.

Run: `mvn -q -o test -Dtest='com.compiladorada.sintactico.*Test,com.compiladorada.CasosDePruebaTest,com.compiladorada.CompiladorTest'`
Expected: PASS — toda la suite léxica/sintáctica existente sigue pasando sin
cambios (la gramática ahora hace más trabajo por dentro, pero acepta/rechaza
exactamente el mismo lenguaje).

- [ ] **Step 16: Escribir una prueba nueva que confirma el AST de expresiones**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class AstExpresionesTest {

    private SimpleNode ast(String fuente) throws Exception {
        return new AdaParser(new StringReader(fuente)).programa();
    }

    private SimpleNode buscar(SimpleNode n, String nombre) {
        if (n.toString().equals(nombre)) {
            return n;
        }
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            SimpleNode r = buscar((SimpleNode) n.jjtGetChild(i), nombre);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @Test
    void una_expresion_sin_operadores_no_crea_nodos_envoltorio() throws Exception {
        // "X := Y;" — ninguno de Expresion/Relacion/Simple/Termino/Factor
        // debería aparecer: el único nodo bajo Asignacion es el Nombre "Y".
        SimpleNode raiz = ast("procedure P is X, Y : Integer; begin X := Y; end;");
        SimpleNode asignacion = buscar(raiz, "Asignacion");
        assertNotNull(asignacion);
        assertNull(buscar(asignacion, "Expresion"));
        assertNull(buscar(asignacion, "Simple"));
        assertNotNull(buscar(asignacion, "Nombre"));
    }

    @Test
    void una_expresion_aritmetica_crea_los_nodos_correspondientes() throws Exception {
        SimpleNode raiz = ast("procedure P is X : Integer; begin X := 1 + 2 * 3; end;");
        assertNotNull(buscar(raiz, "Simple"));
        assertNotNull(buscar(raiz, "Termino"));
    }

    @Test
    void un_literal_entero_se_reconoce_como_nodo_Literal() throws Exception {
        SimpleNode raiz = ast("procedure P is X : Integer; begin X := 42; end;");
        SimpleNode lit = buscar(raiz, "Literal");
        assertNotNull(lit);
        assertEquals("42", lit.jjtGetValue());
    }
}
```

- [ ] **Step 17: Ejecutar y verificar que la prueba nueva pasa**

Run: `mvn -q -o test -Dtest=AstExpresionesTest`
Expected: PASS

- [ ] **Step 18: Escribir una prueba de la Implementación A end-to-end (regla base)**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ImplementacionAEmbebidaTest {

    private AdaParser parsear(String fuente) throws Exception {
        AdaParser p = new AdaParser(new StringReader(fuente));
        p.programa();
        return p;
    }

    @Test
    void variable_no_declarada_reporta_error_semantico() throws Exception {
        AdaParser p = parsear("procedure P is begin X := 1; end;");
        assertEquals(1, p.getErroresSemanticos().size());
        assertTrue(p.getErroresSemanticos().get(0).mensaje().contains("no está declarado"));
    }

    @Test
    void programa_valido_no_reporta_errores_semanticos() throws Exception {
        AdaParser p = parsear("procedure P is X : Integer; begin X := 1 + 2; end;");
        assertTrue(p.getErroresSemanticos().isEmpty());
    }

    @Test
    void asignar_tipo_incompatible_reporta_error_semantico() throws Exception {
        AdaParser p = parsear("procedure P is X : Integer; begin X := True; end;");
        assertEquals(1, p.getErroresSemanticos().size());
        assertTrue(p.getErroresSemanticos().get(0).mensaje().contains("incompatibles"));
    }

    @Test
    void redeclarar_una_variable_reporta_error_semantico() throws Exception {
        AdaParser p = parsear("procedure P is X : Integer; X : Float; begin null; end;");
        assertEquals(1, p.getErroresSemanticos().size());
        assertTrue(p.getErroresSemanticos().get(0).mensaje().contains("ya está declarado"));
    }

    @Test
    void llamada_recursiva_no_reporta_error_de_no_declarado() throws Exception {
        AdaParser p = parsear(
                "function Fib (N : Integer) return Integer is "
              + "begin return Fib(N); end;");
        assertTrue(p.getErroresSemanticos().isEmpty());
    }
}
```

- [ ] **Step 19: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=ImplementacionAEmbebidaTest`
Expected: PASS

- [ ] **Step 20: Commit**

```bash
git add src/main/javacc/Ada.jjt src/main/java/com/compiladorada/semantico/NombreAst.java src/main/java/com/compiladorada/semantico/VerificadorSemantico.java src/test/java/com/compiladorada/sintactico/AstExpresionesTest.java src/test/java/com/compiladorada/sintactico/ImplementacionAEmbebidaTest.java
git commit -m "feat(semantico): Implementación A — acciones semánticas embebidas en Ada.jjt"
```

---

## Task 8: Exponer los errores semánticos en `Compilador`/`ResultadoCompilacion`

**Files:**
- Modify: `src/main/java/com/compiladorada/ResultadoCompilacion.java`
- Modify: `src/main/java/com/compiladorada/Compilador.java`
- Test: `src/test/java/com/compiladorada/CompiladorTest.java` (añadir casos)

**Interfaces:**
- Consumes: `AdaParser.getErroresSemanticos()` (Task 7).
- Produces: `ResultadoCompilacion.erroresSemanticos()`; `Compilador.analizar(...)` sigue con la misma firma pública, ahora también puebla ese campo.

- [ ] **Step 1: Escribir las pruebas que fallan**

Añadir a `CompiladorTest.java` (revisar el archivo existente primero para no
duplicar el nombre de la clase; estos son métodos nuevos dentro de la clase
de prueba ya existente):

```java
    @Test
    void expone_errores_semanticos_cuando_no_hay_errores_lexicos_ni_sintacticos() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is begin X := 1; end;", "t.ada");
        assertTrue(r.erroresLexicos().isEmpty());
        assertTrue(r.erroresSintacticos().isEmpty());
        assertFalse(r.erroresSemanticos().isEmpty());
    }

    @Test
    void no_corre_el_semantico_si_hay_errores_sintacticos() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is begin X := ; end;", "t.ada");
        assertFalse(r.erroresSintacticos().isEmpty());
        assertTrue(r.erroresSemanticos().isEmpty());
    }
```

(Confirmar los imports de `ResultadoCompilacion`/`Compilador` ya existen en
el archivo — si no, añadir `import com.compiladorada.ResultadoCompilacion;`
no es necesario porque la clase de prueba ya está en el paquete
`com.compiladorada`.)

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=CompiladorTest`
Expected: FAIL — `erroresSemanticos()` no existe en `ResultadoCompilacion`.

- [ ] **Step 3: Modificar `ResultadoCompilacion.java`**

```java
package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.lexico.TokenLexico;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.util.List;

public record ResultadoCompilacion(
        List<TokenLexico> tokens,
        List<ErrorCompilacion> erroresLexicos,
        List<ErrorCompilacion> erroresSintacticos,
        List<ErrorCompilacion> erroresSemanticos,
        SimpleNode ast) {

    public boolean tieneErrores() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty() || !erroresSemanticos.isEmpty();
    }

    /**
     * {@code true} si el análisis sintáctico NO se ejecutó por haber errores
     * léxicos. Las fases son secuenciales: el parser solo corre cuando la fase
     * léxica está limpia. Cuando es {@code true}, {@link #erroresSintacticos()}
     * está vacía y {@link #ast()} es {@code null} porque no se analizó, no
     * porque el programa sea sintácticamente correcto.
     */
    public boolean sintacticoOmitido() {
        return !erroresLexicos.isEmpty();
    }

    /**
     * {@code true} si el análisis semántico NO se ejecutó por haber errores
     * léxicos o sintácticos. Igual que {@link #sintacticoOmitido()}, las
     * fases son secuenciales.
     */
    public boolean semanticoOmitido() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty();
    }
}
```

- [ ] **Step 4: Modificar `Compilador.java`**

```java
package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.generado.AdaParser;
import com.compiladorada.lexico.AnalizadorLexico;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class Compilador {

    private Compilador() {
    }

    public static ResultadoCompilacion analizar(String fuente, String nombreArchivo) {
        // nombreArchivo lo usa el IDE al volcar errores; el análisis no lo necesita.
        if (fuente == null) {
            fuente = "";
        }
        AnalizadorLexico lexico = new AnalizadorLexico(fuente);

        // Fases secuenciales: el análisis sintáctico solo se ejecuta si la fase
        // léxica está limpia. Con errores léxicos se devuelve el resultado con la
        // lista sintáctica y semántica vacías y sin AST (ver
        // ResultadoCompilacion.sintacticoOmitido()/semanticoOmitido()).
        if (!lexico.errores().isEmpty()) {
            return new ResultadoCompilacion(lexico.tokens(), lexico.errores(), List.of(), List.of(), null);
        }

        List<ErrorCompilacion> sintacticos = new ArrayList<>();
        SimpleNode ast = null;
        AdaParser parser = new AdaParser(new StringReader(fuente));
        boolean interrumpido = false;
        try {
            ast = parser.programa();
        } catch (Throwable t) {
            // la recuperación se rindió, o error inesperado: se preserva lo acumulado
            interrumpido = true;
        }
        sintacticos.addAll(parser.getErroresSintacticos());
        if (interrumpido) {
            sintacticos.add(new ErrorCompilacion(Categoria.SINTACTICO, 1, 1,
                    "análisis interrumpido: demasiados errores"));
        }
        if (ast == null && sintacticos.isEmpty()) {
            sintacticos.add(new ErrorCompilacion(Categoria.SINTACTICO, 1, 1,
                    "no se pudo construir el árbol sintáctico"));
        }

        // Igual que con léxico -> sintáctico: el análisis semántico (embebido
        // durante el parseo, ver AdaParser/Ada.jjt) solo cuenta si no hubo
        // errores sintácticos — si el árbol quedó mal formado, sus errores
        // "semánticos" serían ruido derivado de la recuperación de errores,
        // no problemas reales del programa.
        List<ErrorCompilacion> semanticos = sintacticos.isEmpty()
                ? parser.getErroresSemanticos()
                : List.of();

        return new ResultadoCompilacion(
                lexico.tokens(),
                lexico.errores(),
                List.copyOf(sintacticos),
                List.copyOf(semanticos),
                ast);
    }
}
```

- [ ] **Step 5: Ejecutar y verificar que pasa**

Run: `mvn -q -o test -Dtest=CompiladorTest`
Expected: PASS

- [ ] **Step 6: Ejecutar toda la suite para confirmar que ningún otro sitio referenciaba el constructor viejo de 4 argumentos**

Run: `mvn -q -o test`
Expected: PASS. Si algo más construye `ResultadoCompilacion` directamente
(fuera de `Compilador.java`), el compilador señalará esos sitios — actualizar
sus llamadas para pasar `List.of()` (o el valor real) como el nuevo cuarto
argumento.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/compiladorada/ResultadoCompilacion.java src/main/java/com/compiladorada/Compilador.java src/test/java/com/compiladorada/CompiladorTest.java
git commit -m "feat(semantico): expone erroresSemanticos() en ResultadoCompilacion"
```

---

## Task 9: Implementación B — visitor de dos pasadas sobre el AST

**Files:**
- Create: `src/main/java/com/compiladorada/semantico/visitor/RecolectorDeclaraciones.java`
- Create: `src/main/java/com/compiladorada/semantico/visitor/VerificadorUsos.java`
- Test: `src/test/java/com/compiladorada/semantico/visitor/ImplementacionBTest.java`

**Interfaces:**
- Consumes: `VerificadorSemantico`, `TipoAda`, `Simbolo`, `Ambito`, `NombreAst` (Tasks 2-6, 7); el AST generado en `com.compiladorada.sintactico.nodos` (`AdaParserDefaultVisitor` y las clases `AST*`, Task 7); `AdaParser`/`Compilador` para producir el AST de entrada (Tasks 7-8).
- Produces: `RecolectorDeclaraciones.recolectar(SimpleNode raiz) -> VerificadorSemantico` (pasada 1: puebla el árbol de ámbitos, sin verificar usos); `VerificadorUsos.verificar(SimpleNode raiz, VerificadorSemantico v)` (pasada 2: reutiliza el mismo `VerificadorSemantico`/árbol de ámbitos, verifica usos). Usado por Task 10 (pruebas duales A vs B).

- [ ] **Step 1: Escribir la prueba end-to-end que falla (define el contrato de las dos pasadas)**

```java
package com.compiladorada.semantico.visitor;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ImplementacionBTest {

    private SimpleNode ast(String fuente) throws Exception {
        return new AdaParser(new StringReader(fuente)).programa();
    }

    private VerificadorSemantico verificarConB(String fuente) throws Exception {
        SimpleNode raiz = ast(fuente);
        VerificadorSemantico v = RecolectorDeclaraciones.recolectar(raiz);
        VerificadorUsos.verificar(raiz, v);
        return v;
    }

    @Test
    void variable_no_declarada_reporta_error_semantico() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is begin X := 1; end;");
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("no está declarado"));
    }

    @Test
    void programa_valido_no_reporta_errores_semanticos() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is X : Integer; begin X := 1 + 2; end;");
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void asignar_tipo_incompatible_reporta_error_semantico() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is X : Integer; begin X := True; end;");
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("incompatibles"));
    }

    @Test
    void redeclarar_una_variable_reporta_error_semantico() throws Exception {
        VerificadorSemantico v = verificarConB("procedure P is X : Integer; X : Float; begin null; end;");
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("ya está declarado"));
    }

    @Test
    void llamada_recursiva_no_reporta_error_de_no_declarado() throws Exception {
        VerificadorSemantico v = verificarConB(
                "function Fib (N : Integer) return Integer is "
              + "begin return Fib(N); end;");
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void un_uso_antes_de_su_declaracion_en_el_mismo_ambito_se_rechaza() throws Exception {
        // Aunque la Pasada 1 ya registró "Y" en todo el ámbito antes de que la
        // Pasada 2 revise ningún uso, "Y" se usa ANTES de declararse en el
        // texto fuente — debe rechazarse igual que lo haría la Implementación A.
        VerificadorSemantico v = verificarConB(
                "procedure P is begin "
              + "X := Y; "
              + "declare_placeholder : Integer; "
              + "end;"
                    .replace("declare_placeholder", "Y"));
        // La línea anterior es solo para dejar explícita la intención; en Ada
        // real las declaraciones van antes de 'begin', así que el caso de
        // prueba representativo real es el de abajo, con dos procedimientos:
        // uno cuyo cuerpo referencia una variable que en verdad pertenece al
        // ámbito de otro procedimiento HERMANO declarado más adelante.
        VerificadorSemantico v2 = verificarConB(
                "procedure Primero is begin Solo_En_Segundo := 1; end; "
              + "procedure Segundo is Solo_En_Segundo : Integer; begin null; end;");
        assertFalse(v2.errores().isEmpty());
    }
}
```

Nota sobre el último test: dado que cada `procedure` abre su PROPIO ámbito
(hijo del global) y los ámbitos hermanos no se ven entre sí, el caso
representativo de "declarado-antes-de-usar" roto por el orden de recolección
de la Pasada 1 en realidad ya está cubierto por el aislamiento de ámbitos
(⁠`Solo_En_Segundo` nunca es visible desde `Primero`, sin importar el orden
de las pasadas). El escenario donde el orden SÍ importa es dentro de un
MISMO ámbito, p. ej. una variable usada en la parte declarativa antes de su
propia declaración textual — pero la gramática de este subconjunto no
permite usar una variable dentro de la definición de otra declaración del
mismo bloque declarativo salvo en su expresión inicializadora, así que el
caso de prueba mínimo y realista es justamente el de ámbitos hermanos de
arriba (confirma que CADA procedimiento tiene su propio ámbito, prerequisito
para que la regla de orden tenga sentido) más la prueba explícita de
`VerificadorSemanticoTest#resolver_con_orden_rechaza_un_uso_antes_de_la_declaracion`
(Task 6), que ya cubre el mecanismo en sí de forma aislada. Simplificar este
test a solo el escenario de ámbitos hermanos:

```java
    @Test
    void variables_de_un_procedimiento_hermano_no_son_visibles() throws Exception {
        VerificadorSemantico v = verificarConB(
                "procedure Primero is begin Solo_En_Segundo := 1; end; "
              + "procedure Segundo is Solo_En_Segundo : Integer; begin null; end;");
        assertFalse(v.errores().isEmpty());
    }
```

(Reemplazar el test `un_uso_antes_de_su_declaracion_en_el_mismo_ambito_se_rechaza`
completo por esta versión simplificada antes de ejecutar — la redacción
larga de arriba documenta el razonamiento pero no debe quedar como código.)

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `mvn -q -o test -Dtest=ImplementacionBTest`
Expected: FAIL — `RecolectorDeclaraciones`/`VerificadorUsos` no existen.

- [ ] **Step 3: Escribir `RecolectorDeclaraciones.java` (Pasada 1)**

```java
package com.compiladorada.semantico.visitor;

import com.compiladorada.semantico.Ambito;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación B, pasada 1: recorre el árbol una vez y registra cada
 * declaración (variables, tipos, parámetros, subprogramas, paquetes) en el
 * ámbito correspondiente — sin verificar ningún uso todavía (eso lo hace
 * {@link VerificadorUsos} en la pasada 2, reutilizando el MISMO
 * VerificadorSemantico y el MISMO árbol de Ambito que esta pasada construyó).
 *
 * <p>Para que la pasada 2 pueda reentrar exactamente los mismos ámbitos que
 * esta pasada abrió (en vez de crear unos vacíos nuevos), esta clase guarda
 * la asociación nodo-que-abre-ámbito → Ambito en {@link #ambitosPorNodo}. La
 * pasada 2 la recibe en su constructor.
 */
public final class RecolectorDeclaraciones extends AdaParserDefaultVisitor {

    private final VerificadorSemantico verificador = new VerificadorSemantico();
    private final Map<Node, Ambito> ambitosPorNodo = new IdentityHashMap<>();

    public static VerificadorSemantico recolectar(SimpleNode raiz) {
        RecolectorDeclaraciones r = new RecolectorDeclaraciones();
        raiz.jjtAccept(r, null);
        r.verificador.ambitosParaPasada2 = r.ambitosPorNodo;
        return r.verificador;
    }

    @Override
    public Object visit(ASTProcedimiento node, Object data) {
        java.util.List<VerificadorSemantico.ParametroInfo> parametros = extraerParametros(node);
        List<TipoAda> tiposParam = new ArrayList<>();
        for (VerificadorSemantico.ParametroInfo p : parametros) {
            for (String n : p.nombres()) {
                tiposParam.add(p.tipo());
            }
        }
        NombreProcedimiento info = (NombreProcedimiento) node.jjtGetValue();
        verificador.declararSubprograma(info.nombre(), tiposParam, null, info.linea(), info.columna());
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        for (VerificadorSemantico.ParametroInfo p : parametros) {
            for (String n : p.nombres()) {
                verificador.declararParametro(n, p.tipo(), p.modoOut(), info.linea(), info.columna());
            }
        }
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    // ... (completar de forma análoga para ASTFuncion, ASTPaquete,
    // ASTDeclaracionVar, ASTDeclaracionTipo, ASTDeclaracionSubtipo, ASTFor —
    // ver Step 4 más abajo para el detalle completo de cada uno)

    private List<VerificadorSemantico.ParametroInfo> extraerParametros(SimpleNode node) {
        List<VerificadorSemantico.ParametroInfo> lista = new ArrayList<>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            if (node.jjtGetChild(i) instanceof ASTParametro p) {
                lista.add((VerificadorSemantico.ParametroInfo) p.jjtGetValue());
            }
        }
        return lista;
    }
}
```

Este primer borrador expone el PROBLEMA de diseño real que hay que resolver
antes de escribir el archivo completo: **la pasada 1 necesita leer, de cada
nodo de declaración, la misma información que la Tarea 7 calculó durante el
parseo** (nombre, tipo, línea/columna, lista de parámetros) — pero
`Ada.jjt` (Task 7) NO guarda esa información en los nodos vía
`jjtSetValue()` salvo para `#Nombre` (`NombreAst`), `#Literal` (el texto) y
`#Parametro` (`ParametroInfo`, ya lo hace el `return` de `parametro()`,
capturado automáticamente porque `#Parametro` es un nodo — pero
`jjtSetValue` no se llama ahí, así que **hace falta añadirlo**). Antes de
escribir el resto de esta tarea, volver a `Ada.jjt` (Task 7) y añadir
`jjtSetValue(...)` en las producciones que construyen nodos de declaración,
para que la Implementación B pueda leer la misma información sin volver a
interpretar tokens crudos:

- [ ] **Step 3a: Volver a `Ada.jjt` y guardar valores en los nodos de declaración**

En `procedimiento()` (Task 7, Step 4), justo después de `nombreTok=<IDENTIFICADOR>`,
añadir `jjtThis.jjtSetValue(new NombreAst(nombreTok.image, nombreTok.beginLine, nombreTok.beginColumn, java.util.List.of()));` —
reutilizando el mismo record `NombreAst` ya creado en Step 10 (con
`segmentos` vacío, ya que aquí no hace falta esa parte). Igual en
`funcion()` y en `paquete()` (para cada uno de sus dos `nombreTok`).

En `parametro()` (Task 7, Step 3), el valor YA es el retorno
(`ParametroInfo`), pero **el retorno no se guarda automáticamente como
`jjtGetValue()`** — hay que añadir explícitamente, justo antes del
`return`:

```
    { VerificadorSemantico.ParametroInfo info =
            new VerificadorSemantico.ParametroInfo(nombres, tipo, modoOut);
      jjtThis.jjtSetValue(info);
      return info; }
```

(sustituye la última línea de acción de `parametro()`, que antes era solo
`{ return new VerificadorSemantico.ParametroInfo(nombres, tipo, modoOut); }`).

En `declaracionVar()`, justo antes de `verificador.declararVariables(...)`,
añadir `jjtThis.jjtSetValue(new DeclaracionVarAst(nombres, tipo, esConstante, inicio.beginLine, inicio.beginColumn));`.

Crear `src/main/java/com/compiladorada/semantico/DeclaracionVarAst.java`:

```java
package com.compiladorada.semantico;

import java.util.List;

/** Valor guardado en el nodo #DeclaracionVar: lo que Ada.jjt#declaracionVar()
 * ya calculó durante el parseo (Implementación A), para que la
 * Implementación B (Task 9) no tenga que volver a interpretar los tokens. */
public record DeclaracionVarAst(List<String> nombres, TipoAda tipo, boolean esConstante,
                                 int linea, int columna) {
}
```

En `declaracionTipo()`, justo antes de `verificador.declararTipo(...)`,
añadir `jjtThis.jjtSetValue(new DeclaracionTipoAst(t.image, tipo, t.beginLine, t.beginColumn));`,
con el record análogo:

```java
package com.compiladorada.semantico;

public record DeclaracionTipoAst(String nombre, TipoAda tipo, int linea, int columna) {
}
```

En `declaracionSubtipo()`, análogo, reutilizando `DeclaracionTipoAst` con
`tipo = new TipoAda.TipoSubtipo(t.image, base)`.

En `sentenciaFor()`, antes de `verificador.declararVariables(...)`, añadir
`jjtThis.jjtSetValue(new DeclaracionVarAst(java.util.List.of(t.image), tipoRango, true, t.beginLine, t.beginColumn));`.

En `paquete()`, análogo a `procedimiento()`/`funcion()`, usando `NombreAst`
con `segmentos` vacío para cada rama (`KW_BODY` y la forma sin `KW_BODY`).

Volver a correr `mvn -q -o compile` y la suite de la Task 7 (Step 15) para
confirmar que estos `jjtSetValue` añadidos no rompen nada (no cambian el
comportamiento de A, solo añaden metadatos al nodo).

- [ ] **Step 4: Escribir `RecolectorDeclaraciones.java` completo**

```java
package com.compiladorada.semantico.visitor;

import com.compiladorada.semantico.Ambito;
import com.compiladorada.semantico.DeclaracionTipoAst;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RecolectorDeclaraciones extends AdaParserDefaultVisitor {

    private final VerificadorSemantico verificador = new VerificadorSemantico();
    private final Map<Node, Ambito> ambitosPorNodo = new IdentityHashMap<>();

    /** Pasada 1 completa: puebla el árbol de ámbitos a partir de la raíz y
     * devuelve el VerificadorSemantico ya poblado (y la asociación
     * nodo→ámbito, recuperable con {@link #ambitosPorNodo(RecolectorDeclaraciones)}
     * para pasarla a la Pasada 2). */
    public static RecolectorDeclaraciones recolectar(SimpleNode raiz) {
        RecolectorDeclaraciones r = new RecolectorDeclaraciones();
        raiz.jjtAccept(r, null);
        return r;
    }

    public VerificadorSemantico verificador() {
        return verificador;
    }

    public Map<Node, Ambito> ambitosPorNodo() {
        return ambitosPorNodo;
    }

    private List<VerificadorSemantico.ParametroInfo> extraerParametros(SimpleNode node) {
        List<VerificadorSemantico.ParametroInfo> lista = new ArrayList<>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            if (node.jjtGetChild(i) instanceof ASTParametro p) {
                lista.add((VerificadorSemantico.ParametroInfo) p.jjtGetValue());
            }
        }
        return lista;
    }

    private void declararSubprogramaYAbrirAmbito(SimpleNode node, NombreAst info,
                                                  TipoAda retorno, Object data) {
        List<VerificadorSemantico.ParametroInfo> parametros = extraerParametros(node);
        List<TipoAda> tiposParam = new ArrayList<>();
        for (VerificadorSemantico.ParametroInfo p : parametros) {
            for (String n : p.nombres()) {
                tiposParam.add(p.tipo());
            }
        }
        verificador.declararSubprograma(info.base(), tiposParam, retorno, info.linea(), info.columna());
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        for (VerificadorSemantico.ParametroInfo p : parametros) {
            for (String n : p.nombres()) {
                verificador.declararParametro(n, p.tipo(), p.modoOut(), info.linea(), info.columna());
            }
        }
        node.childrenAccept(this, data);
        verificador.salirAmbito();
    }

    @Override
    public Object visit(ASTProcedimiento node, Object data) {
        declararSubprogramaYAbrirAmbito(node, (NombreAst) node.jjtGetValue(), null, data);
        return data;
    }

    @Override
    public Object visit(ASTFuncion node, Object data) {
        // El tipo de retorno no se guardó aparte en el nodo (Task 7 no lo
        // necesitaba: lo consumía en línea). Para la Pasada 1 basta con
        // registrar la firma con retorno = TipoAda.DESCONOCIDO como marcador
        // de "función" (distinto de null = procedimiento); la Pasada 2 no
        // depende de este valor exacto, solo de que retorno != null para las
        // comprobaciones de aridad/tipo de argumentos en llamadas — que sí
        // exigen los tipos de PARÁMETRO correctos, no el de retorno.
        declararSubprogramaYAbrirAmbito(node, (NombreAst) node.jjtGetValue(), TipoAda.DESCONOCIDO, data);
        return data;
    }

    @Override
    public Object visit(ASTPaquete node, Object data) {
        NombreAst info = (NombreAst) node.jjtGetValue();
        verificador.declararPaquete(info.base(), info.linea(), info.columna());
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionVar node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        verificador.declararVariables(info.nombres(), info.tipo(), info.esConstante(),
                info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionTipo node, Object data) {
        DeclaracionTipoAst info = (DeclaracionTipoAst) node.jjtGetValue();
        verificador.declararTipo(info.nombre(), info.tipo(), info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionSubtipo node, Object data) {
        DeclaracionTipoAst info = (DeclaracionTipoAst) node.jjtGetValue();
        verificador.declararTipo(info.nombre(), info.tipo(), info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTFor node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        verificador.declararVariables(info.nombres(), info.tipo(), info.esConstante(),
                info.linea(), info.columna());
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }
}
```

- [ ] **Step 5: Escribir `VerificadorUsos.java` (Pasada 2)**

```java
package com.compiladorada.semantico.visitor;

import com.compiladorada.semantico.Ambito;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.Simbolo;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.*;

import java.util.List;
import java.util.Map;

/**
 * Implementación B, pasada 2: reentra los MISMOS ámbitos que
 * {@link RecolectorDeclaraciones} (pasada 1) creó — usando la asociación
 * nodo→Ambito que esa pasada dejó — y verifica cada uso, resolviendo tipos
 * de expresión de abajo hacia arriba mediante despacho recursivo del
 * visitor (cada {@code visit} de un nodo de expresión llama
 * {@code child.jjtAccept(this, data)} sobre sus hijos y castea el resultado
 * a TipoAda). Usa {@code resolverUsoConOrden} en vez de {@code resolverUso}
 * porque la Pasada 1 ya registró TODAS las declaraciones del ámbito antes de
 * que esta pasada revise ningún uso — sin ese chequeo de orden, B aceptaría
 * usos-antes-de-declarar que la Implementación A rechaza.
 */
public final class VerificadorUsos extends AdaParserDefaultVisitor {

    private final VerificadorSemantico verificador;
    private final Map<Node, Ambito> ambitosPorNodo;

    private VerificadorUsos(VerificadorSemantico verificador, Map<Node, Ambito> ambitosPorNodo) {
        this.verificador = verificador;
        this.ambitosPorNodo = ambitosPorNodo;
    }

    /** Corre la pasada 2 sobre la raíz, reutilizando el VerificadorSemantico
     * y la asociación nodo→ámbito que dejó {@link RecolectorDeclaraciones}. */
    public static void verificar(SimpleNode raiz, RecolectorDeclaraciones pasada1) {
        VerificadorUsos v = new VerificadorUsos(pasada1.verificador(), pasada1.ambitosPorNodo());
        raiz.jjtAccept(v, null);
    }

    private TipoAda tipo(Node hijo, Object data) {
        return (TipoAda) hijo.jjtAccept(this, data);
    }

    // ---------------------------------------------------- ámbitos (reentrar)

    @Override
    public Object visit(ASTProcedimiento node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTFuncion node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTPaquete node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTFor node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    // ------------------------------------------------------ sentencias/decl.

    @Override
    public Object visit(ASTDeclaracionVar node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        // El único hijo posible es el inicializador opcional.
        if (node.jjtGetNumChildren() > 0) {
            TipoAda origen = tipo(node.jjtGetChild(0), data);
            verificador.verificarInicializacion(info.tipo(), origen, info.linea(), info.columna());
        }
        return data;
    }

    @Override
    public Object visit(ASTAsignacion node, Object data) {
        // Hijo 0: el Nombre destino. Hijo 1: la expresión origen (si no se
        // colapsó a un Nombre/Literal directo, puede ser Expresion/Simple/...).
        NombreAst destinoInfo = (NombreAst) node.jjtGetChild(0).jjtGetValue();
        Simbolo destino = verificador.resolverUsoConOrden(destinoInfo.base(),
                destinoInfo.linea(), destinoInfo.columna());
        TipoAda tipoDestino = resolverCadena(destino != null ? destino.tipo() : TipoAda.DESCONOCIDO,
                destinoInfo, destino, data, node.jjtGetChild(0));
        verificador.verificarAsignacionMutabilidad(destino, destinoInfo.linea(), destinoInfo.columna());
        TipoAda origen = tipo(node.jjtGetChild(1), data);
        verificador.verificarInicializacion(tipoDestino, origen, destinoInfo.linea(), destinoInfo.columna());
        return data;
    }

    @Override
    public Object visit(ASTLlamadaProc node, Object data) {
        tipo(node.jjtGetChild(0), data);
        return data;
    }

    @Override
    public Object visit(ASTIf node, Object data) {
        // Cada condición es uno de los primeros hijos "sueltos" antes de que
        // empiecen las sentencias del cuerpo; como el árbol no distingue eso
        // estructuralmente, se recorre igual que childrenAccept: cada hijo
        // que sea un nodo de expresión (no una sentencia) se tipa aquí, y el
        // resto se delega. Dado que las condiciones son las ÚNICAS
        // expresiones sueltas bajo #If (las sentencias de sus ramas cuelgan
        // de sus propios nodos con nombre — Asignacion, If, For, While,
        // LlamadaProc — nunca de un nodo de expresión desnudo), basta con
        // detectar, entre los hijos directos de este nodo, cuáles NO son
        // ninguno de esos cinco tipos de sentencia: esos son condiciones.
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            Node hijo = node.jjtGetChild(i);
            if (esNodoDeExpresion(hijo)) {
                TipoAda t = tipo(hijo, data);
                verificador.verificarCondicion(t, node.jjtGetFirstToken().beginLine,
                        node.jjtGetFirstToken().beginColumn);
            } else {
                hijo.jjtAccept(this, data);
            }
        }
        return data;
    }

    @Override
    public Object visit(ASTWhile node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            Node hijo = node.jjtGetChild(i);
            if (esNodoDeExpresion(hijo)) {
                TipoAda t = tipo(hijo, data);
                verificador.verificarCondicion(t, node.jjtGetFirstToken().beginLine,
                        node.jjtGetFirstToken().beginColumn);
            } else {
                hijo.jjtAccept(this, data);
            }
        }
        return data;
    }

    private boolean esNodoDeExpresion(Node n) {
        return n instanceof ASTExpresion || n instanceof ASTRelacion || n instanceof ASTSimple
                || n instanceof ASTTermino || n instanceof ASTFactor || n instanceof ASTLiteral
                || n instanceof ASTNombre;
    }

    // --------------------------------------------------------- expresiones

    @Override
    public Object visit(ASTLiteral node, Object data) {
        String valor = String.valueOf(node.jjtGetValue());
        // El propio texto no distingue Integer/Float/Character/String/null
        // por sí solo de forma fiable (p. ej. "42" también podría ser texto
        // de un literal con base) — en vez de re-derivar el tipo desde el
        // texto, Ada.jjt#literal() (Task 7) debe guardar el TIPO ya
        // calculado, no solo el texto. Ver Step 5a más abajo: se ajusta
        // literal() para guardar un LiteralAst(texto, TipoAda) en vez de
        // solo el texto.
        return ((com.compiladorada.semantico.LiteralAst) node.jjtGetValue()).tipo();
    }

    @Override
    public Object visit(ASTNombre node, Object data) {
        NombreAst info = (NombreAst) node.jjtGetValue();
        Simbolo base = verificador.resolverUsoConOrden(info.base(), info.linea(), info.columna());
        TipoAda tipo = base != null ? base.tipo() : TipoAda.DESCONOCIDO;
        return resolverCadena(tipo, info, base, data, node);
    }

    /** Aplica, en orden, cada segmento (indexación/campo) de NombreAst sobre
     * el tipo actual, consumiendo del nodo los hijos que le correspondan a
     * cada indexación (sus argumentos son hijos reales del árbol, en el
     * mismo orden en que aparecen los segmentos de Indexacion). */
    private TipoAda resolverCadena(TipoAda tipoBase, NombreAst info, Simbolo simboloBase,
                                    Object data, SimpleNode node) {
        TipoAda tipo = tipoBase;
        int siguienteHijo = 0;
        boolean esPrimero = true;
        for (NombreAst.Segmento seg : info.segmentos()) {
            if (seg instanceof NombreAst.Segmento.Indexacion idx) {
                List<TipoAda> argumentos = new java.util.ArrayList<>();
                for (int i = 0; i < idx.cantidadArgumentos(); i++) {
                    argumentos.add(tipo(node.jjtGetChild(siguienteHijo++), data));
                }
                tipo = verificador.tipoDeLlamadaOIndexacion(
                        esPrimero ? simboloBase : null, tipo, argumentos, idx.linea(), idx.columna());
            } else if (seg instanceof NombreAst.Segmento.Campo campo) {
                tipo = verificador.tipoDeCampo(tipo, campo.nombre(), campo.linea(), campo.columna());
            }
            esPrimero = false;
        }
        return tipo;
    }

    @Override
    public Object visit(ASTExpresion node, Object data) {
        @SuppressWarnings("unchecked")
        List<String> ops = (List<String>) node.jjtGetValue();
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        for (int i = 0; i < ops.size(); i++) {
            TipoAda der = tipo(node.jjtGetChild(i + 1), data);
            izq = verificador.tipoOperadorLogico(izq, der, ops.get(i),
                    node.jjtGetFirstToken().beginLine, node.jjtGetFirstToken().beginColumn);
        }
        return izq;
    }

    @Override
    public Object visit(ASTRelacion node, Object data) {
        VerificadorSemantico.RelacionOp op = (VerificadorSemantico.RelacionOp) node.jjtGetValue();
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        TipoAda der = tipo(node.jjtGetChild(1), data);
        int linea = node.jjtGetFirstToken().beginLine;
        int columna = node.jjtGetFirstToken().beginColumn;
        if (op.pertenencia()) {
            return verificador.tipoDePertenencia(izq, der, linea, columna);
        }
        return verificador.tipoOperadorRelacional(izq, der, op.comparador(), linea, columna);
    }

    @Override
    public Object visit(ASTSimple node, Object data) {
        VerificadorSemantico.SimpleOp op = (VerificadorSemantico.SimpleOp) node.jjtGetValue();
        int linea = node.jjtGetFirstToken().beginLine;
        int columna = node.jjtGetFirstToken().beginColumn;
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        if (op.signo() != null) {
            izq = verificador.tipoOperadorUnario(op.signo(), izq, linea, columna);
        }
        for (int i = 0; i < op.operadores().size(); i++) {
            TipoAda der = tipo(node.jjtGetChild(i + 1), data);
            izq = verificador.tipoOperadorAditivo(izq, der, op.operadores().get(i), linea, columna);
        }
        return izq;
    }

    @Override
    public Object visit(ASTTermino node, Object data) {
        @SuppressWarnings("unchecked")
        List<String> ops = (List<String>) node.jjtGetValue();
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        for (int i = 0; i < ops.size(); i++) {
            TipoAda der = tipo(node.jjtGetChild(i + 1), data);
            izq = verificador.tipoOperadorMultiplicativo(izq, der, ops.get(i),
                    node.jjtGetFirstToken().beginLine, node.jjtGetFirstToken().beginColumn);
        }
        return izq;
    }

    @Override
    public Object visit(ASTFactor node, Object data) {
        VerificadorSemantico.FactorOp op = (VerificadorSemantico.FactorOp) node.jjtGetValue();
        int linea = node.jjtGetFirstToken().beginLine;
        int columna = node.jjtGetFirstToken().beginColumn;
        TipoAda base = tipo(node.jjtGetChild(0), data);
        if (op.unario() != null) {
            base = verificador.tipoOperadorUnario(op.unario(), base, linea, columna);
        }
        if (op.tienePotencia()) {
            TipoAda exp = tipo(node.jjtGetChild(1), data);
            base = verificador.tipoOperadorPotencia(base, exp, linea, columna);
        }
        return base;
    }

    /** Cualquier nodo sin manejo explícito (por ejemplo, uno bubbled sin
     * envoltorio cuando no hubo operador) simplemente no debería llegar aquí
     * directamente como top-level de una expresión — pero si un llamador
     * genérico como {@link #tipo(Node, Object)} lo invoca sobre un nodo
     * inesperado, devolver DESCONOCIDO en vez de fallar mantiene la
     * recuperación de errores consistente con el resto del motor. */
    @Override
    public Object visit(SimpleNode node, Object data) {
        return TipoAda.DESCONOCIDO;
    }
}
```

- [ ] **Step 5a: Ajustar `literal()` en `Ada.jjt` para guardar tipo + texto**

Crear `src/main/java/com/compiladorada/semantico/LiteralAst.java`:

```java
package com.compiladorada.semantico;

public record LiteralAst(String texto, TipoAda tipo) {
}
```

En `Ada.jjt`, reemplazar `literal()` (Task 7, Step 11) por:

```
TipoAda literal() #Literal :
{ Token t; }
{
    ( t=<ENTERO> { jjtThis.jjtSetValue(new LiteralAst(t.image, TipoAda.INTEGER)); return TipoAda.INTEGER; } )
  | ( t=<REAL> { jjtThis.jjtSetValue(new LiteralAst(t.image, TipoAda.FLOAT)); return TipoAda.FLOAT; } )
  | ( t=<BASADO> { jjtThis.jjtSetValue(new LiteralAst(t.image, TipoAda.INTEGER)); return TipoAda.INTEGER; } )
  | ( t=<CARACTER> { jjtThis.jjtSetValue(new LiteralAst(t.image, TipoAda.CHARACTER)); return TipoAda.CHARACTER; } )
  | ( t=<CADENA> { jjtThis.jjtSetValue(new LiteralAst(t.image, TipoAda.STRING)); return TipoAda.STRING; } )
  | ( <KW_NULL> { jjtThis.jjtSetValue(new LiteralAst("null", TipoAda.DESCONOCIDO)); return TipoAda.DESCONOCIDO; } )
}
```

Esto **cambia** la prueba `un_literal_entero_se_reconoce_como_nodo_Literal`
de la Task 7 (Step 16): ajustarla para leer `((LiteralAst) lit.jjtGetValue()).texto()`
en vez de `lit.jjtGetValue()` directamente:

```java
    @Test
    void un_literal_entero_se_reconoce_como_nodo_Literal() throws Exception {
        SimpleNode raiz = ast("procedure P is X : Integer; begin X := 42; end;");
        SimpleNode lit = buscar(raiz, "Literal");
        assertNotNull(lit);
        assertEquals("42", ((com.compiladorada.semantico.LiteralAst) lit.jjtGetValue()).texto());
    }
```

- [ ] **Step 6: Ejecutar y verificar que todo pasa**

Run: `mvn -q -o compile`
Expected: compila (recuerda regenerar el parser: `mvn -q -o generate-sources` si el IDE no lo hace solo).

Run: `mvn -q -o test -Dtest=ImplementacionBTest,AstExpresionesTest,ImplementacionAEmbebidaTest`
Expected: PASS

Run: `mvn -q -o test`
Expected: PASS — toda la suite completa, incluida la existente antes de este plan.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/compiladorada/semantico/ src/main/javacc/Ada.jjt src/test/java/com/compiladorada/semantico/visitor/ src/test/java/com/compiladorada/sintactico/AstExpresionesTest.java
git commit -m "feat(semantico): Implementación B — visitor de dos pasadas sobre el AST"
```

---

## Task 10: Casos de prueba duales (A vs B) + limpieza final

**Files:**
- Create: `src/test/resources/casos/semanticos/validos/01_variables_y_expresiones.ada`
- Create: `src/test/resources/casos/semanticos/validos/02_registros_arreglos_llamadas.ada`
- Create: `src/test/resources/casos/semanticos/invalidos/01_no_declarado.ada` + `.expected`
- Create: `src/test/resources/casos/semanticos/invalidos/02_redeclarado.ada` + `.expected`
- Create: `src/test/resources/casos/semanticos/invalidos/03_tipos_incompatibles.ada` + `.expected`
- Create: `src/test/resources/casos/semanticos/invalidos/04_asignacion_a_constante.ada` + `.expected`
- Create: `src/test/resources/casos/semanticos/invalidos/05_aridad_incorrecta.ada` + `.expected`
- Create: `src/test/java/com/compiladorada/semantico/CasosSemanticosTest.java`

**Interfaces:**
- Consumes: `Compilador.analizar` (Task 8, para A), `RecolectorDeclaraciones`/`VerificadorUsos` (Task 9, para B).
- Produces: la evidencia final de que ambas implementaciones reportan lo mismo, con el mismo mecanismo `.ada`+`.expected` que ya usa `CasosDePruebaTest`.

- [ ] **Step 1: Crear los casos válidos**

`src/test/resources/casos/semanticos/validos/01_variables_y_expresiones.ada`:

```ada
procedure Ejemplo is
   X : Integer := 10;
   Y : constant Float := 3.14;
   Activo : Boolean := True;
   Letra : Character := 'a';
   Nombre : String := "hola";
begin
   X := X + 1;
   Activo := X > 5 and then Y > 0.0;
   if Activo then
      X := X * 2;
   end if;
   while X < 100 loop
      X := X + 1;
   end loop;
   for I in 1 .. 10 loop
      X := X + I;
   end loop;
end Ejemplo;
```

`src/test/resources/casos/semanticos/validos/02_registros_arreglos_llamadas.ada`:

```ada
procedure Ejemplo2 is
   type Vector is array (1 .. 5) of Integer;
   type Punto is record
      X : Integer;
      Y : Integer;
   end record;

   V : Vector;
   P : Punto;

   function Suma (A, B : Integer) return Integer is
   begin
      return A + B;
   end Suma;
begin
   V(1) := 10;
   P.X := 1;
   P.Y := Suma(P.X, V(1));
end Ejemplo2;
```

- [ ] **Step 2: Crear los casos inválidos y sus `.expected`**

`src/test/resources/casos/semanticos/invalidos/01_no_declarado.ada`:

```ada
procedure Ejemplo is
begin
   X := 1;
end Ejemplo;
```

`01_no_declarado.expected`:

```
3:4:SEMANTICO
```

(Ajustar línea/columna exacta contra la salida real tras correr el
compilador una vez — ver Step 4; el formato es el mismo que usa
`CasosDePruebaTest`: `linea:columna:CATEGORIA`.)

`src/test/resources/casos/semanticos/invalidos/02_redeclarado.ada`:

```ada
procedure Ejemplo is
   X : Integer;
   X : Float;
begin
   null;
end Ejemplo;
```

`src/test/resources/casos/semanticos/invalidos/03_tipos_incompatibles.ada`:

```ada
procedure Ejemplo is
   X : Integer;
begin
   X := True;
end Ejemplo;
```

`src/test/resources/casos/semanticos/invalidos/04_asignacion_a_constante.ada`:

```ada
procedure Ejemplo is
   X : constant Integer := 1;
begin
   X := 2;
end Ejemplo;
```

`src/test/resources/casos/semanticos/invalidos/05_aridad_incorrecta.ada`:

```ada
procedure Ejemplo is
   function Suma (A, B : Integer) return Integer is
   begin
      return A + B;
   end Suma;
   X : Integer;
begin
   X := Suma(1);
end Ejemplo;
```

Para cada uno, crear el `.expected` correspondiente con una sola línea
`linea:columna:SEMANTICO` — las líneas/columnas exactas se confirman
ejecutando el Step 4 una primera vez y leyendo el mensaje de fallo (que
imprime los errores reales), luego ajustando los `.expected` para que
coincidan, siguiendo el mismo flujo que ya usa `CasosDePruebaTest`.

- [ ] **Step 3: Escribir `CasosSemanticosTest.java`**

```java
package com.compiladorada.semantico;

import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.Compilador;
import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.generado.AdaParser;
import com.compiladorada.semantico.visitor.RecolectorDeclaraciones;
import com.compiladorada.semantico.visitor.VerificadorUsos;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre cada caso .ada/.expected de casos/semanticos contra AMBAS
 * implementaciones (A: Compilador.analizar, embebida en el parseo; B:
 * RecolectorDeclaraciones + VerificadorUsos, visitor de dos pasadas) y
 * exige que las dos coincidan exactamente con lo esperado — esa doble
 * ejecución es la comparación que pide la materia (ver spec, sección 5).
 */
class CasosSemanticosTest {

    private static final Path VALIDOS = Path.of("src/test/resources/casos/semanticos/validos");
    private static final Path INVALIDOS = Path.of("src/test/resources/casos/semanticos/invalidos");

    private List<ErrorCompilacion> conB(String fuente) throws Exception {
        SimpleNode raiz = new AdaParser(new StringReader(fuente)).programa();
        RecolectorDeclaraciones pasada1 = RecolectorDeclaraciones.recolectar(raiz);
        VerificadorUsos.verificar(raiz, pasada1);
        return pasada1.verificador().errores();
    }

    @TestFactory
    Stream<DynamicTest> casos_validos_no_reportan_errores_en_ninguna_implementacion() throws IOException {
        List<Path> archivos = Files.list(VALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .sorted()
                .toList();
        assertTrue(archivos.size() >= 2, "esperados >= 2 casos válidos, encontrados " + archivos.size());
        return archivos.stream()
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    String fuente = Files.readString(p);
                    ResultadoCompilacion r = Compilador.analizar(fuente, p.getFileName().toString());
                    assertTrue(r.erroresLexicos().isEmpty(), "léxicos inesperados: " + r.erroresLexicos());
                    assertTrue(r.erroresSintacticos().isEmpty(), "sintácticos inesperados: " + r.erroresSintacticos());
                    assertTrue(r.erroresSemanticos().isEmpty(),
                            "semánticos inesperados (A): " + r.erroresSemanticos());
                    assertTrue(conB(fuente).isEmpty(), "semánticos inesperados (B): " + conB(fuente));
                }));
    }

    @TestFactory
    Stream<DynamicTest> casos_invalidos_reportan_lo_esperado_en_ambas_implementaciones() throws IOException {
        List<Path> archivos = Files.list(INVALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .sorted()
                .toList();
        assertTrue(archivos.size() >= 5, "esperados >= 5 casos inválidos, encontrados " + archivos.size());
        return archivos.stream()
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    String fuente = Files.readString(p);
                    Path esperado = p.resolveSibling(p.getFileName().toString().replace(".ada", ".expected"));
                    List<String> esperados = Files.readAllLines(esperado).stream()
                            .filter(l -> !l.isBlank()).toList();

                    ResultadoCompilacion r = Compilador.analizar(fuente, p.getFileName().toString());
                    assertTrue(r.erroresLexicos().isEmpty(), "léxicos inesperados: " + r.erroresLexicos());
                    assertTrue(r.erroresSintacticos().isEmpty(), "sintácticos inesperados: " + r.erroresSintacticos());
                    assertCoincideConEsperado(esperados, r.erroresSemanticos(), p.getFileName() + " (A)");

                    List<ErrorCompilacion> deB = conB(fuente);
                    assertCoincideConEsperado(esperados, deB, p.getFileName() + " (B)");
                }));
    }

    private void assertCoincideConEsperado(List<String> esperados, List<ErrorCompilacion> reales, String etiqueta) {
        for (String linea : esperados) {
            String[] pt = linea.trim().split(":");
            int ln = Integer.parseInt(pt[0]);
            int col = Integer.parseInt(pt[1]);
            ErrorCompilacion.Categoria cat = ErrorCompilacion.Categoria.valueOf(pt[2]);
            assertTrue(
                    reales.stream().anyMatch(e -> e.linea() == ln && e.columna() == col && e.categoria() == cat),
                    "falta el error " + linea + " en " + etiqueta + "; errores reales: " + reales);
        }
        assertEquals(esperados.size(), reales.size(), () -> "errores extra en " + etiqueta + ": " + reales);
    }
}
```

- [ ] **Step 4: Ejecutar, ajustar línea/columna de los `.expected` contra la salida real, y volver a ejecutar**

Run: `mvn -q -o test -Dtest=CasosSemanticosTest`
Expected (primera corrida): probablemente FAIL con mensajes del tipo
"falta el error 3:4:SEMANTICO ... errores reales: [...]" — leer la lista de
"errores reales" impresa en el mensaje de aserción, copiar la línea/columna
exacta de cada `ErrorCompilacion` a su archivo `.expected` correspondiente.

Repetir hasta: PASS

- [ ] **Step 5: Ejecutar la suite completa una última vez**

Run: `mvn -q -o test`
Expected: PASS — todo el proyecto, incluidas las Tasks 1-9 y la suite
léxica/sintáctica previa a este plan.

- [ ] **Step 6: Commit**

```bash
git add src/test/resources/casos/semanticos/ src/test/java/com/compiladorada/semantico/CasosSemanticosTest.java
git commit -m "test(semantico): casos duales A/B con el mecanismo .ada/.expected"
```

---

## Self-Review (completado al escribir este plan)

- **Cobertura del spec:** sección 1 (integración pipeline) → Task 8; sección 2
  (núcleo compartido) → Tasks 2-6; sección 3 (Implementación A) → Task 7;
  sección 4 (Implementación B) → Task 9; sección 5 (pruebas duales) → Task
  10; sección 6 (limpieza E1-E15) → ya completada en la sesión de
  brainstorming, antes de este plan (commits pendientes de
  `docs/superpowers/specs/2026-09-20-analisis-semantico-design.md` y
  `docs/unidad-0-diseno-ide.md`).
- **Placeholders:** ninguno — cada step trae el código completo a escribir;
  las únicas "TBD" explícitas son línea/columna de los `.expected` (Task 10),
  que dependen de ejecutar el compilador y por diseño no pueden fijarse de
  antemano (mismo patrón que ya usa `CasosDePruebaTest`).
- **Consistencia de tipos:** `VerificadorSemantico` es la única fuente de
  los records `ParametroInfo`/`NombreResuelto`/`FactorOp`/`SimpleOp`/
  `RelacionOp` (Task 6, ampliado en Task 7); `NombreAst`/`LiteralAst`/
  `DeclaracionVarAst`/`DeclaracionTipoAst` viven en `com.compiladorada.semantico`
  directamente (Tasks 7 y 9). Todas las firmas usadas en Task 9 (visitors)
  se verificaron contra las que Task 6/7 efectivamente declaran.
