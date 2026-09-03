# Front-end léxico-sintáctico e IDE — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir el analizador léxico-sintáctico de un subconjunto de Ada (con recuperación de errores que reporta todos los errores de una pasada y produce un AST JJTree) y un IDE Swing que lo opera.

**Architecture:** Un motor de compilación (`com.compiladorada.*`) desacoplado del IDE mediante la fachada `Compilador.analizar(fuente, nombre) -> ResultadoCompilacion`. El motor hace un recorrido léxico dedicado (para la tabla de tokens del IDE) y luego una pasada de parseo JavaCC/JJTree con recuperación en modo pánico. El IDE (`com.compiladorada.ide.*`) solo consume la fachada y vuelca los resultados a `output/`.

**Tech Stack:** Java 17, Maven, JavaCC + JJTree (`org.javacc.plugin:javacc-maven-plugin:3.1.1`, goal `jjtree-javacc`), JUnit 5 (`org.junit.jupiter:junit-jupiter:5.11.4`), Swing, RSyntaxTextArea 3.5.4.

**Spec:** `docs/superpowers/specs/2026-09-02-front-end-compilador-ada-design.md`

## Global Constraints

- Java: `maven.compiler.release` = `17`. No usar APIs posteriores a Java 17.
- Encoding de todo el proyecto: `UTF-8` (ya fijado en `pom.xml`).
- El subconjunto de Ada soportado es el de `docs/CLAUDE.md`, sección "Subconjunto de Ada soportado". No añadir construcciones fuera de esa lista (genéricos, concurrencia, OO/`tagged`, `access` real, punto fijo, `Wide_*`, cadenas dinámicas, `separate`, `goto`, `renames`, `aliased`).
- Ada es case-insensitive: la gramática usa `IGNORE_CASE = true` y el lookup de palabras reservadas es case-insensitive.
- `Compilador.analizar(...)` **nunca** propaga una excepción al llamador.
- Mensajes de error dirigidos al usuario: en español.
- Formato de línea de error (archivos y paneles): `<archivo>:<linea>:<columna>: <categoria>: <mensaje>` (estilo GCC/GNAT).
- Carpeta de salida: `output/` en la raíz del proyecto. Archivos sobrescritos completos en cada compilación.
- Paquete del parser generado: `com.compiladorada.generado`. Paquete de los nodos JJTree: `com.compiladorada.sintactico.nodos`.
- Commits: mensajes en español, estilo Conventional Commits (`feat:`, `test:`, `docs:`, `build:`, `refactor:`). **No** añadir líneas de coautoría.
- TDD estricto: test que falla -> verlo fallar -> implementación mínima -> test que pasa -> commit.

---

## Estructura de archivos

| Archivo | Responsabilidad |
|---|---|
| `pom.xml` | Build. Cambia el goal `javacc` -> `jjtree-javacc`, source `src/main/javacc`. |
| `src/main/javacc/Ada.jjt` | Gramática única: opciones, tokens, producciones, anotaciones JJTree. |
| `src/main/java/com/compiladorada/lexico/TipoToken.java` | Enum de categorías de token para la tabla del IDE. |
| `src/main/java/com/compiladorada/lexico/TokenLexico.java` | POJO `{ lexema, tipo, linea, columna }`. |
| `src/main/java/com/compiladorada/lexico/PalabrasReservadas.java` | Set de las 73 palabras reservadas; lookup case-insensitive. |
| `src/main/java/com/compiladorada/lexico/AnalizadorLexico.java` | Recorrido léxico dedicado: produce `List<TokenLexico>` (incluye comentarios y tokens de error) y `List<ErrorCompilacion>` léxicos. |
| `src/main/java/com/compiladorada/errores/ErrorCompilacion.java` | POJO `{ categoria, linea, columna, mensaje }` + `Categoria` enum. |
| `src/main/java/com/compiladorada/errores/EscritorErrores.java` | Vuelca tokens y errores a `output/`. |
| `src/main/java/com/compiladorada/ResultadoCompilacion.java` | POJO agregado con las cuatro salidas. |
| `src/main/java/com/compiladorada/Compilador.java` | Fachada `analizar(fuente, nombre)`. |
| `src/main/java/com/compiladorada/ide/Main.java` | Punto de entrada (ya existe como stub). |
| `src/main/java/com/compiladorada/ide/VentanaPrincipal.java` | `JFrame`, `JMenuBar`, layout, flujo "Compilar". |
| `src/main/java/com/compiladorada/ide/EditorPanel.java` | `RSyntaxTextArea` + abrir/guardar. |
| `src/main/java/com/compiladorada/ide/BarraEstado.java` | `Ln x, Col y` en vivo + resultado de compilación. |
| `src/main/java/com/compiladorada/ide/TablaTokensPanel.java` | `JTable` de tokens. |
| `src/main/java/com/compiladorada/ide/PanelErrores.java` | `JTabbedPane` con dos `JTable` de errores. |
| `src/main/java/com/compiladorada/ide/AdaTokenMaker.java` | Resaltado de sintaxis Ada para RSyntaxTextArea (recortable). |
| `src/test/java/com/compiladorada/...` | Tests JUnit 5 espejando los paquetes de `main`. |
| `src/test/resources/casos/validos/*.ada` | Programas que deben dar 0 errores. |
| `src/test/resources/casos/invalidos/*.ada` + `*.expected` | Programas con errores conocidos. |
| `docs/unidad-0-diseno-ide.md` | Documento de diseño académico de la unidad (30% de la nota). |

---

## Task 1: Configurar la cadena de build JJTree y el bloque de tokens

**Files:**
- Modify: `pom.xml` (bloque `<plugin>` de `javacc-maven-plugin`)
- Create: `src/main/javacc/Ada.jjt`
- Delete: `src/main/javacc/README.txt`
- Test: `src/test/java/com/compiladorada/lexico/LexerHumoTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: clases generadas `com.compiladorada.generado.AdaParser`, `AdaParserTokenManager`, `AdaParserConstants`, `Token`, `SimpleCharStream`, `ParseException`, `TokenMgrError`. Nombres de token públicos vía `AdaParserConstants` (p. ej. `IDENTIFICADOR`, `ENTERO`, `REAL`, `CARACTER`, `CADENA`, `KW_PROCEDURE`, ..., `ASIGNA`, `FLECHA`, `ERROR_LEXICO`, `EOF`).

- [ ] **Step 1: Cambiar el plugin a `jjtree-javacc` en `pom.xml`**

Reemplaza la ejecución actual del plugin `javacc-maven-plugin` por:

```xml
<plugin>
    <groupId>org.javacc.plugin</groupId>
    <artifactId>javacc-maven-plugin</artifactId>
    <version>3.1.1</version>
    <executions>
        <execution>
            <id>jjtree-javacc</id>
            <goals>
                <goal>jjtree-javacc</goal>
            </goals>
            <configuration>
                <sourceDirectory>${project.basedir}/src/main/javacc</sourceDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Crear `src/main/javacc/Ada.jjt` con opciones + tokens + una producción raíz mínima**

```java
options {
    JDK_VERSION = "17";
    STATIC = false;
    IGNORE_CASE = true;
    MULTI = true;
    VISITOR = true;
    NODE_DEFAULT_VOID = true;
    TRACK_TOKENS = true;
    NODE_PACKAGE = "com.compiladorada.sintactico.nodos";
}

PARSER_BEGIN(AdaParser)
package com.compiladorada.generado;

public class AdaParser {
}
PARSER_END(AdaParser)

/* ===== Descartables ===== */
SKIP : { " " | "\t" | "\r" | "\n" }

/* Comentario de línea: disponible para el recorrido léxico, ignorado por el parser */
SPECIAL_TOKEN : { <COMENTARIO: "--" (~["\n","\r"])*> }

/* ===== Palabras reservadas (subconjunto usado por la gramática) ===== */
TOKEN :
{
    <KW_PROCEDURE: "procedure">
  | <KW_FUNCTION:  "function">
  | <KW_PACKAGE:   "package">
  | <KW_BODY:      "body">
  | <KW_IS:        "is">
  | <KW_BEGIN:     "begin">
  | <KW_END:       "end">
  | <KW_RETURN:    "return">
  | <KW_CONSTANT:  "constant">
  | <KW_TYPE:      "type">
  | <KW_SUBTYPE:   "subtype">
  | <KW_RANGE:     "range">
  | <KW_RECORD:    "record">
  | <KW_ARRAY:     "array">
  | <KW_OF:        "of">
  | <KW_IN:        "in">
  | <KW_OUT:       "out">
  | <KW_IF:        "if">
  | <KW_THEN:      "then">
  | <KW_ELSIF:     "elsif">
  | <KW_ELSE:      "else">
  | <KW_LOOP:      "loop">
  | <KW_FOR:       "for">
  | <KW_WHILE:     "while">
  | <KW_REVERSE:   "reverse">
  | <KW_DECLARE:   "declare">
  | <KW_EXCEPTION: "exception">
  | <KW_WHEN:      "when">
  | <KW_RAISE:     "raise">
  | <KW_OTHERS:    "others">
  | <KW_PRIVATE:   "private">
  | <KW_NULL:      "null">
  | <KW_AND:       "and">
  | <KW_OR:        "or">
  | <KW_XOR:       "xor">
  | <KW_NOT:       "not">
  | <KW_ABS:       "abs">
  | <KW_MOD:       "mod">
  | <KW_REM:       "rem">
}

/* ===== Delimitadores compuestos (antes que los simples) ===== */
TOKEN :
{
    <ASIGNA:   ":=">
  | <FLECHA:   "=>">
  | <PUNTOPUNTO: "..">
  | <POT:      "**">
  | <NEQ:      "/=">
  | <GEQ:      ">=">
  | <LEQ:      "<=">
  | <ETIQIZQ:  "<<">
  | <ETIQDER:  ">>">
  | <CAJA:     "<>">
  | <BARRA:    "|">
}

/* ===== Delimitadores simples ===== */
TOKEN :
{
    <LPAREN: "(">
  | <RPAREN: ")">
  | <COMA:   ",">
  | <PUNTO:  ".">
  | <DOSP:   ":">
  | <PYC:    ";">
  | <IGUAL:  "=">
  | <MENOR:  "<">
  | <MAYOR:  ">">
  | <MAS:    "+">
  | <MENOS:  "-">
  | <POR:    "*">
  | <DIV:    "/">
  | <AMP:    "&">
  | <APOS:   "'">
}

/* ===== Literales ===== */
TOKEN :
{
    <#DIGITO:    ["0"-"9"]>
  | <#HEX:       ["0"-"9","a"-"f","A"-"F"]>
  | <#EXP:       ["e","E"] (["+","-"])? (<DIGITO>)+>
  | <ENTERO:     <DIGITO> ("_")? (<DIGITO>)* >
  | <REAL:       <DIGITO> ("_")? (<DIGITO>)* "." <DIGITO> ("_")? (<DIGITO>)* (<EXP>)? >
  | <BASADO:     (<DIGITO>)+ "#" <HEX> (("_")? <HEX>)* ("." <HEX> (("_")? <HEX>)*)? "#" (<EXP>)? >
  | <CARACTER:   "'" (~["'","\n","\r"]) "'">
  | <CADENA:     "\"" ( (~["\"","\n","\r"]) | "\"\"" )* "\"">
}

/* ===== Identificador ===== */
TOKEN :
{
    <IDENTIFICADOR: ["a"-"z","A"-"Z"] (["a"-"z","A"-"Z","0"-"9"])*
                    ("_" (["a"-"z","A"-"Z","0"-"9"])+)* >
}

/* ===== Catch-all léxico: cualquier carácter que nada aceptó ===== */
TOKEN :
{
    <ERROR_LEXICO: ~[]>
}

/* Producción raíz mínima; se amplía en Tasks 5-8 */
void programa() #Programa :
{}
{
    ( <IDENTIFICADOR> | keyword() | literal() | delimitador() )* <EOF>
}

void keyword() : {}
{
    <KW_PROCEDURE> | <KW_FUNCTION> | <KW_PACKAGE> | <KW_BODY> | <KW_IS> | <KW_BEGIN>
  | <KW_END> | <KW_RETURN> | <KW_CONSTANT> | <KW_TYPE> | <KW_SUBTYPE> | <KW_RANGE>
  | <KW_RECORD> | <KW_ARRAY> | <KW_OF> | <KW_IN> | <KW_OUT> | <KW_IF> | <KW_THEN>
  | <KW_ELSIF> | <KW_ELSE> | <KW_LOOP> | <KW_FOR> | <KW_WHILE> | <KW_REVERSE>
  | <KW_DECLARE> | <KW_EXCEPTION> | <KW_WHEN> | <KW_RAISE> | <KW_OTHERS> | <KW_PRIVATE>
  | <KW_NULL> | <KW_AND> | <KW_OR> | <KW_XOR> | <KW_NOT> | <KW_ABS> | <KW_MOD> | <KW_REM>
}

void literal() : {}
{ <ENTERO> | <REAL> | <BASADO> | <CARACTER> | <CADENA> }

void delimitador() : {}
{
    <ASIGNA> | <FLECHA> | <PUNTOPUNTO> | <POT> | <NEQ> | <GEQ> | <LEQ> | <ETIQIZQ>
  | <ETIQDER> | <CAJA> | <BARRA> | <LPAREN> | <RPAREN> | <COMA> | <PUNTO> | <DOSP>
  | <PYC> | <IGUAL> | <MENOR> | <MAYOR> | <MAS> | <MENOS> | <POR> | <DIV> | <AMP> | <APOS>
}
```

- [ ] **Step 3: Borrar `src/main/javacc/README.txt`**

Run: `rm src/main/javacc/README.txt`

- [ ] **Step 4: Escribir el test de humo del lexer**

```java
package com.compiladorada.lexico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.AdaParserConstants;
import com.compiladorada.generado.AdaParserTokenManager;
import com.compiladorada.generado.SimpleCharStream;
import com.compiladorada.generado.Token;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerHumoTest {

    private List<Token> tokenizar(String fuente) {
        AdaParserTokenManager tm =
                new AdaParserTokenManager(new SimpleCharStream(new StringReader(fuente)));
        List<Token> tokens = new ArrayList<>();
        Token t;
        while ((t = tm.getNextToken()).kind != AdaParserConstants.EOF) {
            tokens.add(t);
        }
        return tokens;
    }

    @Test
    void reconoce_palabras_reservadas_identificadores_y_delimitadores() {
        List<Token> t = tokenizar("procedure Hola is begin end;");
        assertEquals(AdaParserConstants.KW_PROCEDURE, t.get(0).kind);
        assertEquals(AdaParserConstants.IDENTIFICADOR, t.get(1).kind);
        assertEquals("Hola", t.get(1).image);
        assertEquals(AdaParserConstants.KW_IS, t.get(2).kind);
        assertEquals(AdaParserConstants.PYC, t.get(t.size() - 1).kind);
    }

    @Test
    void ada_es_case_insensitive_en_palabras_reservadas() {
        List<Token> t = tokenizar("PROCEDURE x IS BEGIN END;");
        assertEquals(AdaParserConstants.KW_PROCEDURE, t.get(0).kind);
        assertEquals(AdaParserConstants.KW_IS, t.get(2).kind);
    }

    @Test
    void reconoce_literales() {
        List<Token> t = tokenizar("1_000 3.14 16#FF# 'a' \"hola\"");
        assertEquals(AdaParserConstants.ENTERO, t.get(0).kind);
        assertEquals(AdaParserConstants.REAL, t.get(1).kind);
        assertEquals(AdaParserConstants.BASADO, t.get(2).kind);
        assertEquals(AdaParserConstants.CARACTER, t.get(3).kind);
        assertEquals(AdaParserConstants.CADENA, t.get(4).kind);
    }

    @Test
    void caracter_ilegal_produce_token_ERROR_LEXICO() {
        List<Token> t = tokenizar("x $ y");
        assertTrue(t.stream().anyMatch(tok -> tok.kind == AdaParserConstants.ERROR_LEXICO));
    }
}
```

- [ ] **Step 5: Ejecutar el test y verlo fallar**

Run: `mvn -q test -Dtest=LexerHumoTest`
Expected: FALLA en compilación o resolución de plugin la primera vez (descarga de `javacc-maven-plugin`). Tras la descarga, debe compilar la gramática y **pasar**. Si el goal `jjtree-javacc` no genera en `com.compiladorada.generado`, revisar que el `package` en `PARSER_BEGIN` es correcto.

- [ ] **Step 6: Ajustar hasta que los 4 tests pasen**

Run: `mvn -q test -Dtest=LexerHumoTest`
Expected: PASA (4 tests).

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/javacc/Ada.jjt src/test/java/com/compiladorada/lexico/LexerHumoTest.java
git rm src/main/javacc/README.txt
git commit -m "build: cadena JJTree->JavaCC y bloque de tokens de Ada"
```

---

## Task 2: Modelo de datos del resultado de compilación

**Files:**
- Create: `src/main/java/com/compiladorada/lexico/TipoToken.java`
- Create: `src/main/java/com/compiladorada/lexico/TokenLexico.java`
- Create: `src/main/java/com/compiladorada/errores/ErrorCompilacion.java`
- Create: `src/main/java/com/compiladorada/ResultadoCompilacion.java`
- Test: `src/test/java/com/compiladorada/ModeloDatosTest.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `enum TipoToken { IDENTIFICADOR, PALABRA_RESERVADA, ENTERO, REAL, BASADO, CARACTER, CADENA, DELIMITADOR_SIMPLE, DELIMITADOR_COMPUESTO, COMENTARIO, ERROR }`
  - `TokenLexico(String lexema, TipoToken tipo, int linea, int columna)` — record; getters `lexema()`, `tipo()`, `linea()`, `columna()`.
  - `ErrorCompilacion.Categoria { LEXICO, SINTACTICO }`
  - `ErrorCompilacion(Categoria categoria, int linea, int columna, String mensaje)` — record; método `String formatear(String nombreArchivo)` que devuelve `nombreArchivo + ":" + linea + ":" + columna + ": " + etiqueta + ": " + mensaje` donde `etiqueta` es `"error léxico"` o `"error sintáctico"`.
  - `ResultadoCompilacion(List<TokenLexico> tokens, List<ErrorCompilacion> erroresLexicos, List<ErrorCompilacion> erroresSintacticos, Object ast)` — record; `ast` es `Object` por ahora (Task 9 lo tipa a `Node`). Método `boolean tieneErrores()`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModeloDatosTest {

    @Test
    void token_lexico_guarda_sus_campos() {
        TokenLexico t = new TokenLexico("Hola", TipoToken.IDENTIFICADOR, 3, 11);
        assertEquals("Hola", t.lexema());
        assertEquals(TipoToken.IDENTIFICADOR, t.tipo());
        assertEquals(3, t.linea());
        assertEquals(11, t.columna());
    }

    @Test
    void error_lexico_se_formatea_estilo_gcc() {
        ErrorCompilacion e = new ErrorCompilacion(Categoria.LEXICO, 12, 8, "carácter no válido '$'");
        assertEquals("p.ada:12:8: error léxico: carácter no válido '$'", e.formatear("p.ada"));
    }

    @Test
    void error_sintactico_usa_su_etiqueta() {
        ErrorCompilacion e = new ErrorCompilacion(Categoria.SINTACTICO, 20, 1, "se esperaba ';'");
        assertEquals("p.ada:20:1: error sintáctico: se esperaba ';'", e.formatear("p.ada"));
    }

    @Test
    void resultado_sin_errores_no_tiene_errores() {
        ResultadoCompilacion r = new ResultadoCompilacion(List.of(), List.of(), List.of(), null);
        assertFalse(r.tieneErrores());
    }

    @Test
    void resultado_con_error_sintactico_tiene_errores() {
        ErrorCompilacion e = new ErrorCompilacion(Categoria.SINTACTICO, 1, 1, "x");
        ResultadoCompilacion r = new ResultadoCompilacion(List.of(), List.of(), List.of(e), null);
        assertTrue(r.tieneErrores());
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=ModeloDatosTest`
Expected: FALLA — clases inexistentes.

- [ ] **Step 3: Crear las clases**

`TipoToken.java`:

```java
package com.compiladorada.lexico;

public enum TipoToken {
    IDENTIFICADOR,
    PALABRA_RESERVADA,
    ENTERO,
    REAL,
    BASADO,
    CARACTER,
    CADENA,
    DELIMITADOR_SIMPLE,
    DELIMITADOR_COMPUESTO,
    COMENTARIO,
    ERROR
}
```

`TokenLexico.java`:

```java
package com.compiladorada.lexico;

public record TokenLexico(String lexema, TipoToken tipo, int linea, int columna) {
}
```

`ErrorCompilacion.java`:

```java
package com.compiladorada.errores;

public record ErrorCompilacion(Categoria categoria, int linea, int columna, String mensaje) {

    public enum Categoria {
        LEXICO("error léxico"),
        SINTACTICO("error sintáctico");

        private final String etiqueta;

        Categoria(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public String formatear(String nombreArchivo) {
        return nombreArchivo + ":" + linea + ":" + columna + ": "
                + categoria.etiqueta() + ": " + mensaje;
    }
}
```

`ResultadoCompilacion.java`:

```java
package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.lexico.TokenLexico;

import java.util.List;

public record ResultadoCompilacion(
        List<TokenLexico> tokens,
        List<ErrorCompilacion> erroresLexicos,
        List<ErrorCompilacion> erroresSintacticos,
        Object ast) {

    public boolean tieneErrores() {
        return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty();
    }
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=ModeloDatosTest`
Expected: PASA (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada src/test/java/com/compiladorada/ModeloDatosTest.java
git commit -m "feat: modelo de datos del resultado de compilación"
```

---

## Task 3: Tabla de palabras reservadas

**Files:**
- Create: `src/main/java/com/compiladorada/lexico/PalabrasReservadas.java`
- Test: `src/test/java/com/compiladorada/lexico/PalabrasReservadasTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `PalabrasReservadas.esReservada(String)` -> `boolean`, case-insensitive. `PalabrasReservadas.TODAS` -> `Set<String>` inmutable en minúsculas (73 entradas).

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.lexico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PalabrasReservadasTest {

    @Test
    void hay_73_palabras_reservadas() {
        assertEquals(73, PalabrasReservadas.TODAS.size());
    }

    @Test
    void reconoce_reservadas_sin_importar_mayusculas() {
        assertTrue(PalabrasReservadas.esReservada("procedure"));
        assertTrue(PalabrasReservadas.esReservada("PROCEDURE"));
        assertTrue(PalabrasReservadas.esReservada("Procedure"));
        assertTrue(PalabrasReservadas.esReservada("end"));
    }

    @Test
    void tipos_predefinidos_no_son_reservados() {
        assertFalse(PalabrasReservadas.esReservada("Integer"));
        assertFalse(PalabrasReservadas.esReservada("Float"));
        assertFalse(PalabrasReservadas.esReservada("Boolean"));
        assertFalse(PalabrasReservadas.esReservada("Character"));
        assertFalse(PalabrasReservadas.esReservada("String"));
    }

    @Test
    void identificador_comun_no_es_reservado() {
        assertFalse(PalabrasReservadas.esReservada("Contador"));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=PalabrasReservadasTest`
Expected: FALLA — clase inexistente.

- [ ] **Step 3: Crear `PalabrasReservadas.java`** (las 73 palabras reservadas de Ada 2012)

```java
package com.compiladorada.lexico;

import java.util.Locale;
import java.util.Set;

public final class PalabrasReservadas {

    public static final Set<String> TODAS = Set.of(
            "abort", "abs", "abstract", "accept", "access", "aliased", "all", "and",
            "array", "at", "begin", "body", "case", "constant", "declare", "delay",
            "delta", "digits", "do", "else", "elsif", "end", "entry", "exception",
            "exit", "for", "function", "generic", "goto", "if", "in", "interface",
            "is", "limited", "loop", "mod", "new", "not", "null", "of", "or", "others",
            "out", "overriding", "package", "pragma", "private", "procedure",
            "protected", "raise", "range", "record", "rem", "renames", "requeue",
            "return", "reverse", "select", "separate", "some", "subtype",
            "synchronized", "tagged", "task", "terminate", "then", "type", "until",
            "use", "when", "while", "with", "xor");

    private PalabrasReservadas() {
    }

    public static boolean esReservada(String lexema) {
        return TODAS.contains(lexema.toLowerCase(Locale.ROOT));
    }
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=PalabrasReservadasTest`
Expected: PASA (4 tests). Si el conteo no da 73, revisar la lista contra el Ada Reference Manual 2012 §2.9.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/lexico/PalabrasReservadas.java src/test/java/com/compiladorada/lexico/PalabrasReservadasTest.java
git commit -m "feat: tabla de las 73 palabras reservadas de Ada 2012"
```

---

## Task 4: Recorrido léxico dedicado (`AnalizadorLexico`)

**Files:**
- Create: `src/main/java/com/compiladorada/lexico/AnalizadorLexico.java`
- Test: `src/test/java/com/compiladorada/lexico/AnalizadorLexicoTest.java`

**Interfaces:**
- Consumes: `AdaParserTokenManager`, `AdaParserConstants`, `SimpleCharStream`, `Token` (Task 1); `TokenLexico`, `TipoToken`, `PalabrasReservadas` (Tasks 2-3); `ErrorCompilacion` (Task 2).
- Produces:
  - `AnalizadorLexico(String fuente)` — constructor.
  - `List<TokenLexico> tokens()` — todos los tokens en orden, incluidos comentarios (`SPECIAL_TOKEN`) y tokens `ERROR_LEXICO` (como `TipoToken.ERROR`). No incluye `EOF`.
  - `List<ErrorCompilacion> errores()` — un `ErrorCompilacion(LEXICO, ...)` por cada token `ERROR_LEXICO`, mensaje `"carácter no válido '<c>'"`.
  - Nunca lanza: si `getNextToken()` lanza `TokenMgrError`, se registra como error léxico en la posición conocida y se detiene el recorrido.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.lexico;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalizadorLexicoTest {

    @Test
    void clasifica_identificador_reservada_y_delimitadores() {
        AnalizadorLexico a = new AnalizadorLexico("procedure P is begin end;");
        List<TokenLexico> t = a.tokens();
        assertEquals(TipoToken.PALABRA_RESERVADA, t.get(0).tipo());
        assertEquals("procedure", t.get(0).lexema());
        assertEquals(TipoToken.IDENTIFICADOR, t.get(1).tipo());
        assertEquals(TipoToken.DELIMITADOR_SIMPLE, t.get(t.size() - 1).tipo());
        assertTrue(a.errores().isEmpty());
    }

    @Test
    void clasifica_literales_por_subtipo() {
        List<TokenLexico> t = new AnalizadorLexico("1_000 3.14 16#FF# 'a' \"hi\"").tokens();
        assertEquals(TipoToken.ENTERO, t.get(0).tipo());
        assertEquals(TipoToken.REAL, t.get(1).tipo());
        assertEquals(TipoToken.BASADO, t.get(2).tipo());
        assertEquals(TipoToken.CARACTER, t.get(3).tipo());
        assertEquals(TipoToken.CADENA, t.get(4).tipo());
    }

    @Test
    void los_comentarios_aparecen_en_la_lista_de_tokens() {
        List<TokenLexico> t = new AnalizadorLexico("x -- nota\ny").tokens();
        assertTrue(t.stream().anyMatch(tok -> tok.tipo() == TipoToken.COMENTARIO
                && tok.lexema().equals("-- nota")));
    }

    @Test
    void caracter_ilegal_es_token_ERROR_y_genera_error_lexico() {
        AnalizadorLexico a = new AnalizadorLexico("x $ y");
        assertTrue(a.tokens().stream().anyMatch(tok -> tok.tipo() == TipoToken.ERROR));
        List<ErrorCompilacion> e = a.errores();
        assertEquals(1, e.size());
        assertEquals(ErrorCompilacion.Categoria.LEXICO, e.get(0).categoria());
        assertEquals(1, e.get(0).linea());
        assertEquals(3, e.get(0).columna());
        assertTrue(e.get(0).mensaje().contains("$"));
    }

    @Test
    void reporta_linea_y_columna_correctas() {
        List<TokenLexico> t = new AnalizadorLexico("procedure\n  P").tokens();
        assertEquals(2, t.get(1).linea());
        assertEquals(3, t.get(1).columna());
    }

    @Test
    void entrada_basura_no_lanza() {
        assertDoesNotThrow(() -> new AnalizadorLexico("###\u0000@@@```").tokens());
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=AnalizadorLexicoTest`
Expected: FALLA — clase inexistente.

- [ ] **Step 3: Crear `AnalizadorLexico.java`**

```java
package com.compiladorada.lexico;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.generado.AdaParserConstants;
import com.compiladorada.generado.AdaParserTokenManager;
import com.compiladorada.generado.SimpleCharStream;
import com.compiladorada.generado.Token;
import com.compiladorada.generado.TokenMgrError;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class AnalizadorLexico {

    private final List<TokenLexico> tokens = new ArrayList<>();
    private final List<ErrorCompilacion> errores = new ArrayList<>();

    public AnalizadorLexico(String fuente) {
        AdaParserTokenManager tm =
                new AdaParserTokenManager(new SimpleCharStream(new StringReader(fuente)));
        try {
            Token t = tm.getNextToken();
            while (t.kind != AdaParserConstants.EOF) {
                agregarEspeciales(t);
                agregar(t);
                t = tm.getNextToken();
            }
            agregarEspeciales(t); // comentarios pegados justo antes de EOF
        } catch (TokenMgrError e) {
            errores.add(new ErrorCompilacion(Categoria.LEXICO, 0, 0,
                    "no se pudo continuar el análisis léxico: " + e.getMessage()));
        }
    }

    public List<TokenLexico> tokens() {
        return List.copyOf(tokens);
    }

    public List<ErrorCompilacion> errores() {
        return List.copyOf(errores);
    }

    /** Los SPECIAL_TOKEN (comentarios) cuelgan de t.specialToken en orden inverso. */
    private void agregarEspeciales(Token t) {
        if (t.specialToken == null) {
            return;
        }
        List<Token> especiales = new ArrayList<>();
        Token s = t.specialToken;
        while (s != null) {
            especiales.add(0, s);
            s = s.specialToken;
        }
        for (Token esp : especiales) {
            tokens.add(new TokenLexico(esp.image, TipoToken.COMENTARIO,
                    esp.beginLine, esp.beginColumn));
        }
    }

    private void agregar(Token t) {
        TipoToken tipo = clasificar(t);
        tokens.add(new TokenLexico(t.image, tipo, t.beginLine, t.beginColumn));
        if (tipo == TipoToken.ERROR) {
            errores.add(new ErrorCompilacion(Categoria.LEXICO, t.beginLine, t.beginColumn,
                    "carácter no válido '" + t.image + "'"));
        }
    }

    private TipoToken clasificar(Token t) {
        switch (t.kind) {
            case AdaParserConstants.IDENTIFICADOR:
                return PalabrasReservadas.esReservada(t.image)
                        ? TipoToken.PALABRA_RESERVADA
                        : TipoToken.IDENTIFICADOR;
            case AdaParserConstants.ENTERO:
                return TipoToken.ENTERO;
            case AdaParserConstants.REAL:
                return TipoToken.REAL;
            case AdaParserConstants.BASADO:
                return TipoToken.BASADO;
            case AdaParserConstants.CARACTER:
                return TipoToken.CARACTER;
            case AdaParserConstants.CADENA:
                return TipoToken.CADENA;
            case AdaParserConstants.ERROR_LEXICO:
                return TipoToken.ERROR;
            default:
                return esCompuesto(t.kind)
                        ? TipoToken.DELIMITADOR_COMPUESTO
                        : (esReservadaKind(t.kind)
                            ? TipoToken.PALABRA_RESERVADA
                            : TipoToken.DELIMITADOR_SIMPLE);
        }
    }

    private boolean esCompuesto(int kind) {
        return kind == AdaParserConstants.ASIGNA || kind == AdaParserConstants.FLECHA
                || kind == AdaParserConstants.PUNTOPUNTO || kind == AdaParserConstants.POT
                || kind == AdaParserConstants.NEQ || kind == AdaParserConstants.GEQ
                || kind == AdaParserConstants.LEQ || kind == AdaParserConstants.ETIQIZQ
                || kind == AdaParserConstants.ETIQDER || kind == AdaParserConstants.CAJA
                || kind == AdaParserConstants.BARRA;
    }

    private boolean esReservadaKind(int kind) {
        // Las palabras reservadas declaradas como token propio quedan entre KW_PROCEDURE y KW_REM.
        return kind >= AdaParserConstants.KW_PROCEDURE && kind <= AdaParserConstants.KW_REM;
    }
}
```

Nota para el implementador: si el rango `KW_PROCEDURE..KW_REM` no es contiguo en el `AdaParserConstants` generado (depende del orden exacto en `Ada.jjt`), sustituir `esReservadaKind` por un `switch` explícito sobre cada `KW_*`. Verificar abriendo `target/generated-sources/javacc/com/compiladorada/generado/AdaParserConstants.java`.

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=AnalizadorLexicoTest`
Expected: PASA (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/lexico/AnalizadorLexico.java src/test/java/com/compiladorada/lexico/AnalizadorLexicoTest.java
git commit -m "feat: recorrido léxico dedicado con clasificación de tokens"
```

---

## Task 5: Gramática — subprogramas, parámetros y declaraciones de variable

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (reemplaza la producción `programa()` mínima de Task 1; borra `keyword()`/`literal()`/`delimitador()` de relleno)
- Test: `src/test/java/com/compiladorada/sintactico/GramaticaSubprogramasTest.java`

**Interfaces:**
- Consumes: tokens de Task 1.
- Produces: `AdaParser.programa()` reconoce uno o más subprogramas/paquetes; `AdaParser` expone constructor `AdaParser(java.io.Reader)`. Producciones internas: `unidadCompilacion()`, `procedimiento()`, `funcion()`, `parametros()`, `parametro()`, `tipoRef()`, `declaracion()`, `declaracionVar()`. Estas se amplían en Tasks 6-8.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaSubprogramasTest {

    private void parsear(String fuente) throws ParseException {
        new AdaParser(new StringReader(fuente)).programa();
    }

    @Test
    void procedimiento_minimo() {
        assertDoesNotThrow(() -> parsear("procedure Vacio is begin null; end;"));
    }

    @Test
    void procedimiento_con_end_nombrado() {
        assertDoesNotThrow(() -> parsear("procedure Saludo is begin null; end Saludo;"));
    }

    @Test
    void procedimiento_con_parametros_y_modos() {
        assertDoesNotThrow(() -> parsear(
                "procedure P (A : in Integer; B : out Integer; C : in out Float) "
              + "is begin null; end P;"));
    }

    @Test
    void funcion_con_retorno_y_declaraciones() {
        assertDoesNotThrow(() -> parsear(
                "function Doble (X : Integer) return Integer is "
              + "  R : Integer; K : constant Integer := 2; "
              + "begin R := X; end Doble;"));
    }

    @Test
    void declaracion_de_varias_variables_en_una_linea() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is A, B, C : Integer; begin null; end;"));
    }

    @Test
    void falta_punto_y_coma_lanza_ParseException() {
        assertThrows(ParseException.class, () -> parsear(
                "procedure P is begin null end;"));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=GramaticaSubprogramasTest`
Expected: FALLA — `programa()` de Task 1 no acepta esta estructura / las clases no cambian.

- [ ] **Step 3: Reemplazar la sección de producciones en `Ada.jjt`**

Borra `void programa()...`, `void keyword()`, `void literal()`, `void delimitador()` de Task 1 y pon:

```java
void programa() #Programa : {}
{
    ( unidadCompilacion() )+ <EOF>
}

void unidadCompilacion() : {}
{
    LOOKAHEAD(2) paquete()
  | procedimiento()
  | funcion()
}

void procedimiento() #Procedimiento : {}
{
    <KW_PROCEDURE> <IDENTIFICADOR> ( "(" parametros() ")" )? <KW_IS>
        ( declaracion() )*
    <KW_BEGIN>
        ( sentencia() )*
    ( bloqueExcepcion() )?
    <KW_END> ( <IDENTIFICADOR> )? ";"
}

void funcion() #Funcion : {}
{
    <KW_FUNCTION> <IDENTIFICADOR> ( "(" parametros() ")" )? <KW_RETURN> tipoRef() <KW_IS>
        ( declaracion() )*
    <KW_BEGIN>
        ( sentencia() )*
    ( bloqueExcepcion() )?
    <KW_END> ( <IDENTIFICADOR> )? ";"
}

void parametros() : {}
{
    parametro() ( ";" parametro() )*
}

void parametro() #Parametro : {}
{
    <IDENTIFICADOR> ( "," <IDENTIFICADOR> )* ":"
    ( LOOKAHEAD(2) <KW_IN> <KW_OUT> | <KW_IN> | <KW_OUT> )?
    tipoRef()
}

/* Referencia a un tipo por nombre; la definición de tipos nuevos va en Task 8. */
void tipoRef() : {}
{
    <IDENTIFICADOR>
}

void declaracion() : {}
{
    declaracionVar()
}

void declaracionVar() #DeclaracionVar : {}
{
    <IDENTIFICADOR> ( "," <IDENTIFICADOR> )* ":" ( <KW_CONSTANT> )? tipoRef()
    ( ":=" expresion() )? ";"
}
```

Añade **stubs temporales** para las producciones que definen Tasks 6-8, para que compile:

```java
void sentencia() : {}
{
    <KW_NULL> ";"
  | <IDENTIFICADOR> ":=" expresion() ";"
}

void expresion() : {}
{
    <IDENTIFICADOR> | <ENTERO> | <REAL> | <BASADO> | <CARACTER> | <CADENA>
}

void bloqueExcepcion() : {}
{
    <KW_EXCEPTION> <KW_WHEN> <KW_OTHERS> "=>" ( sentencia() )*
}

void paquete() : {}
{
    <KW_PACKAGE> ( <KW_BODY> )? <IDENTIFICADOR> <KW_IS> ( declaracion() )*
    <KW_END> ( <IDENTIFICADOR> )? ";"
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=GramaticaSubprogramasTest`
Expected: PASA (6 tests). Si JavaCC reporta warnings de LOOKAHEAD en `unidadCompilacion` o `parametro`, son esperados (los resuelven los `LOOKAHEAD` explícitos); no deben ser errores.

- [ ] **Step 5: Commit**

```bash
git add src/main/javacc/Ada.jjt src/test/java/com/compiladorada/sintactico/GramaticaSubprogramasTest.java
git commit -m "feat: gramática de subprogramas, parámetros y declaración de variables"
```

---

## Task 6: Gramática — sentencias de control

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (reemplaza el stub `sentencia()`)
- Test: `src/test/java/com/compiladorada/sintactico/GramaticaSentenciasTest.java`

**Interfaces:**
- Consumes: `programa()`, `expresion()` (stub aún), tokens de Task 1.
- Produces: `sentencia()` completa: `null;`, asignación, llamada a procedimiento, `if/elsif/else`, `for`, `while`, `raise`. Producciones nuevas: `asignacion()`, `llamadaProc()`, `sentenciaIf()`, `sentenciaFor()`, `sentenciaWhile()`, `sentenciaRaise()`, `rango()`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaSentenciasTest {

    private void parsear(String cuerpo) throws ParseException {
        String fuente = "procedure P is begin " + cuerpo + " end;";
        new AdaParser(new StringReader(fuente)).programa();
    }

    @Test
    void asignacion_y_llamada() {
        assertDoesNotThrow(() -> parsear("X := 1; Poner(X); Iniciar;"));
    }

    @Test
    void if_elsif_else() {
        assertDoesNotThrow(() -> parsear(
                "if X = 1 then Y := 1; elsif X = 2 then Y := 2; else Y := 0; end if;"));
    }

    @Test
    void for_con_reverse() {
        assertDoesNotThrow(() -> parsear(
                "for I in reverse 1 .. 10 loop Sumar(I); end loop;"));
    }

    @Test
    void while_loop() {
        assertDoesNotThrow(() -> parsear("while X < 10 loop X := X + 1; end loop;"));
    }

    @Test
    void raise_con_y_sin_nombre() {
        assertDoesNotThrow(() -> parsear("raise; raise Constraint_Error;"));
    }

    @Test
    void if_sin_then_lanza() {
        assertThrows(ParseException.class, () -> parsear("if X = 1 Y := 1; end if;"));
    }

    @Test
    void end_loop_sin_loop_lanza() {
        assertThrows(ParseException.class, () -> parsear(
                "for I in 1 .. 3 Sumar(I); end loop;"));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=GramaticaSentenciasTest`
Expected: FALLA — el stub `sentencia()` solo acepta `null;` y asignación simple.

- [ ] **Step 3: Reemplazar el stub `sentencia()` en `Ada.jjt`**

```java
void sentencia() : {}
{
    <KW_NULL> ";"
  | sentenciaIf()
  | sentenciaFor()
  | sentenciaWhile()
  | sentenciaRaise()
  | LOOKAHEAD(<IDENTIFICADOR> ":=") asignacion()
  | llamadaProc()
}

void asignacion() #Asignacion : {}
{
    <IDENTIFICADOR> ":=" expresion() ";"
}

void llamadaProc() #LlamadaProc : {}
{
    <IDENTIFICADOR> ( "(" expresion() ( "," expresion() )* ")" )? ";"
}

void sentenciaIf() #If : {}
{
    <KW_IF> expresion() <KW_THEN> ( sentencia() )*
    ( <KW_ELSIF> expresion() <KW_THEN> ( sentencia() )* )*
    ( <KW_ELSE> ( sentencia() )* )?
    <KW_END> <KW_IF> ";"
}

void sentenciaFor() #For : {}
{
    <KW_FOR> <IDENTIFICADOR> <KW_IN> ( <KW_REVERSE> )? rango() <KW_LOOP>
        ( sentencia() )*
    <KW_END> <KW_LOOP> ";"
}

void sentenciaWhile() #While : {}
{
    <KW_WHILE> expresion() <KW_LOOP>
        ( sentencia() )*
    <KW_END> <KW_LOOP> ";"
}

void sentenciaRaise() : {}
{
    <KW_RAISE> ( <IDENTIFICADOR> )? ";"
}

void rango() : {}
{
    expresion() ".." expresion()
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=GramaticaSentenciasTest`
Expected: PASA (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/javacc/Ada.jjt src/test/java/com/compiladorada/sintactico/GramaticaSentenciasTest.java
git commit -m "feat: gramática de sentencias de control (if, for, while, raise)"
```

---

## Task 7: Gramática — expresiones con precedencia completa

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (reemplaza el stub `expresion()`)
- Test: `src/test/java/com/compiladorada/sintactico/GramaticaExpresionesTest.java`

**Interfaces:**
- Consumes: tokens de Task 1; `rango()` de Task 6.
- Produces: cadena `expresion()` -> `relacion()` -> `simple()` -> `termino()` -> `factor()` -> `primario()`, con `and/or/xor/and then/or else`, relacionales, pertenencia `in`/`not in`, `+ - &`, `* / mod rem`, `abs not`, `**`, paréntesis, llamada a función `id(args)`, acceso a campo `id.id`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaExpresionesTest {

    private void expr(String e) throws ParseException {
        new AdaParser(new StringReader(
                "procedure P is begin X := " + e + "; end;")).programa();
    }

    @Test
    void aritmetica_con_precedencia() {
        assertDoesNotThrow(() -> expr("1 + 2 * 3 - 4 / 2"));
        assertDoesNotThrow(() -> expr("2 ** 3 ** 2"));
        assertDoesNotThrow(() -> expr("A mod B rem C"));
    }

    @Test
    void logica_y_cortocircuito() {
        assertDoesNotThrow(() -> expr("A and then B or else C"));
        assertDoesNotThrow(() -> expr("not A xor (B and C)"));
    }

    @Test
    void relacionales_y_pertenencia() {
        assertDoesNotThrow(() -> expr("X >= 1 and X <= 10"));
        assertDoesNotThrow(() -> expr("X in 1 .. 100"));
        assertDoesNotThrow(() -> expr("X not in 1 .. 100"));
    }

    @Test
    void concatenacion_llamada_y_campo() {
        assertDoesNotThrow(() -> expr("Nombre & \" \" & Apellido"));
        assertDoesNotThrow(() -> expr("Max(A, B) + Registro.Campo"));
        assertDoesNotThrow(() -> expr("abs (-X)"));
    }

    @Test
    void parentesis_desbalanceado_lanza() {
        assertThrows(ParseException.class, () -> expr("(1 + 2"));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=GramaticaExpresionesTest`
Expected: FALLA — el stub `expresion()` solo acepta un literal/identificador suelto.

- [ ] **Step 3: Reemplazar el stub `expresion()` en `Ada.jjt`**

```java
void expresion() : {}
{
    relacion()
    ( ( <KW_AND> ( LOOKAHEAD(2) <KW_THEN> )? | <KW_OR> ( LOOKAHEAD(2) <KW_ELSE> )? | <KW_XOR> )
      relacion() )*
}

void relacion() : {}
{
    simple()
    (
        ( "=" | <NEQ> | "<" | <LEQ> | ">" | <GEQ> ) simple()
      | ( <KW_NOT> )? <KW_IN> rango()
    )?
}

void simple() : {}
{
    ( "+" | "-" )? termino() ( ( "+" | "-" | "&" ) termino() )*
}

void termino() : {}
{
    factor() ( ( "*" | "/" | <KW_MOD> | <KW_REM> ) factor() )*
}

void factor() : {}
{
    ( <KW_ABS> | <KW_NOT> )? primario() ( "**" primario() )?
}

void primario() : {}
{
    "(" expresion() ")"
  | <ENTERO> | <REAL> | <BASADO> | <CARACTER> | <CADENA> | <KW_NULL>
  | <IDENTIFICADOR>
      ( "(" expresion() ( "," expresion() )* ")" )?
      ( "." <IDENTIFICADOR> )*
}
```

Nota: la producción `rango()` de Task 6 ya existe; `relacion()` la reutiliza para `in`.

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=GramaticaExpresionesTest`
Expected: PASA (5 tests). Si hay warning de LOOKAHEAD en `and then` / `or else`, es esperado y está cubierto por el `LOOKAHEAD(2)`.

- [ ] **Step 5: Commit**

```bash
git add src/main/javacc/Ada.jjt src/test/java/com/compiladorada/sintactico/GramaticaExpresionesTest.java
git commit -m "feat: gramática de expresiones con precedencia completa de Ada"
```

---

## Task 8: Gramática — tipos, paquetes y bloque de excepciones

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (amplía `declaracion()`, reemplaza stubs `bloqueExcepcion()` y `paquete()`)
- Test: `src/test/java/com/compiladorada/sintactico/GramaticaTiposTest.java`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: `declaracion()` acepta también `declaracionTipo()` y `declaracionSubtipo()`; `definicionTipo()` (range / enum / record / array); `paquete()` con spec (`is ... [private ...] end`) y body (`package body ... is ... [begin ...] end`); `bloqueExcepcion()` con múltiples `manejador()` (`when id | id => ...`, `when others => ...`).

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.generado.ParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GramaticaTiposTest {

    private void parsear(String fuente) throws ParseException {
        new AdaParser(new StringReader(fuente)).programa();
    }

    @Test
    void tipo_rango_y_subtipo() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is type Grado is range 0 .. 100; "
              + "subtype Positivo is Integer range 1 .. 100; begin null; end;"));
    }

    @Test
    void tipo_enumerado() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is type Color is (Rojo, Verde, Azul); begin null; end;"));
    }

    @Test
    void tipo_registro_y_arreglo() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is "
              + "  type Punto is record X : Integer; Y : Integer; end record; "
              + "  type Vec is array (1 .. 10) of Integer; "
              + "begin null; end;"));
    }

    @Test
    void paquete_spec_con_private() {
        assertDoesNotThrow(() -> parsear(
                "package Pila is X : Integer; private Y : Integer; end Pila;"));
    }

    @Test
    void paquete_body_con_begin() {
        assertDoesNotThrow(() -> parsear(
                "package body Pila is Z : Integer; begin Z := 0; end Pila;"));
    }

    @Test
    void bloque_de_excepciones_con_varios_manejadores() {
        assertDoesNotThrow(() -> parsear(
                "procedure P is begin null; "
              + "exception "
              + "  when Constraint_Error | Program_Error => null; "
              + "  when others => raise; "
              + "end;"));
    }

    @Test
    void record_sin_end_record_lanza() {
        assertThrows(ParseException.class, () -> parsear(
                "procedure P is type R is record X : Integer; begin null; end;"));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=GramaticaTiposTest`
Expected: FALLA — `declaracion()` solo acepta `declaracionVar()`; stubs de paquete/excepción son demasiado pobres.

- [ ] **Step 3: Ampliar `Ada.jjt`**

Reemplaza `declaracion()`:

```java
void declaracion() : {}
{
    <KW_TYPE> declaracionTipo()
  | <KW_SUBTYPE> declaracionSubtipo()
  | declaracionVar()
}

void declaracionTipo() #DeclaracionTipo : {}
{
    <IDENTIFICADOR> <KW_IS> definicionTipo() ";"
}

void declaracionSubtipo() #DeclaracionTipo : {}
{
    <IDENTIFICADOR> <KW_IS> <IDENTIFICADOR> ( <KW_RANGE> rango() )? ";"
}

void definicionTipo() : {}
{
    <KW_RANGE> rango()
  | "(" <IDENTIFICADOR> ( "," <IDENTIFICADOR> )* ")"
  | <KW_RECORD> ( componenteRegistro() )* <KW_END> <KW_RECORD>
  | <KW_ARRAY> "(" rango() ")" <KW_OF> tipoRef()
}

void componenteRegistro() : {}
{
    <IDENTIFICADOR> ( "," <IDENTIFICADOR> )* ":" tipoRef() ( ":=" expresion() )? ";"
}
```

Reemplaza el stub `bloqueExcepcion()`:

```java
void bloqueExcepcion() : {}
{
    <KW_EXCEPTION> ( manejador() )+
}

void manejador() : {}
{
    <KW_WHEN> ( <KW_OTHERS> | <IDENTIFICADOR> ( "|" <IDENTIFICADOR> )* ) "=>"
        ( sentencia() )*
}
```

Reemplaza el stub `paquete()`:

```java
void paquete() #Paquete : {}
{
    <KW_PACKAGE>
    (
        <KW_BODY> <IDENTIFICADOR> <KW_IS>
            ( declaracion() )*
        ( <KW_BEGIN> ( sentencia() )* )?
        ( bloqueExcepcion() )?
        <KW_END> ( <IDENTIFICADOR> )? ";"
      |
        <IDENTIFICADOR> <KW_IS>
            ( declaracion() )*
        ( <KW_PRIVATE> ( declaracion() )* )?
        <KW_END> ( <IDENTIFICADOR> )? ";"
    )
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=GramaticaTiposTest`
Expected: PASA (7 tests).

- [ ] **Step 5: Ejecutar toda la suite de gramática para detectar regresiones**

Run: `mvn -q test -Dtest=Gramatica*`
Expected: PASA (todas).

- [ ] **Step 6: Commit**

```bash
git add src/main/javacc/Ada.jjt src/test/java/com/compiladorada/sintactico/GramaticaTiposTest.java
git commit -m "feat: gramática de tipos, paquetes y bloque de excepciones"
```

---

## Task 9: Anotaciones JJTree y acceso al AST

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (revisar que las anotaciones `#Nombre` estén en las producciones de la spec §4.4; añadir método de retorno del AST)
- Test: `src/test/java/com/compiladorada/sintactico/AstTest.java`

**Interfaces:**
- Consumes: gramática de Tasks 5-8.
- Produces: `AdaParser.programa()` cambia su firma a `SimpleNode programa()` y devuelve la raíz del AST (`com.compiladorada.sintactico.nodos.SimpleNode`). Los nodos exponen `jjtGetNumChildren()`, `jjtGetChild(int)`, `toString()` (nombre del nodo), y por `TRACK_TOKENS` los métodos `jjtGetFirstToken()` / `jjtGetLastToken()`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.AdaParser;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AstTest {

    private SimpleNode ast(String fuente) throws Exception {
        return new AdaParser(new StringReader(fuente)).programa();
    }

    private void recolectar(SimpleNode n, List<String> acc) {
        acc.add(n.toString());
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            recolectar((SimpleNode) n.jjtGetChild(i), acc);
        }
    }

    @Test
    void la_raiz_es_Programa() throws Exception {
        SimpleNode raiz = ast("procedure P is begin null; end;");
        assertEquals("Programa", raiz.toString());
    }

    @Test
    void el_arbol_contiene_los_nodos_esperados() throws Exception {
        SimpleNode raiz = ast(
                "procedure P (X : Integer) is C : constant Integer := 1; "
              + "begin if X = 1 then P2(C); end if; end P;");
        List<String> nombres = new ArrayList<>();
        recolectar(raiz, nombres);
        assertTrue(nombres.contains("Procedimiento"));
        assertTrue(nombres.contains("Parametro"));
        assertTrue(nombres.contains("DeclaracionVar"));
        assertTrue(nombres.contains("If"));
        assertTrue(nombres.contains("LlamadaProc"));
    }

    @Test
    void los_nodos_conservan_su_posicion() throws Exception {
        SimpleNode raiz = ast("procedure P is\n begin null; end;");
        SimpleNode proc = (SimpleNode) raiz.jjtGetChild(0);
        assertEquals("Procedimiento", proc.toString());
        assertEquals(1, proc.jjtGetFirstToken().beginLine);
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=AstTest`
Expected: FALLA — `programa()` devuelve `void`; falta el `return`.

- [ ] **Step 3: Ajustar `programa()` en `Ada.jjt` para devolver el nodo**

```java
SimpleNode programa() #Programa :
{}
{
    ( unidadCompilacion() )+ <EOF>
    { return jjtThis; }
}
```

Verifica que estas producciones llevan su anotación `#Nombre` (deben haberse puesto en Tasks 5-8; si falta alguna, añádela): `#Programa`, `#Procedimiento`, `#Funcion`, `#Paquete`, `#Parametro`, `#DeclaracionVar`, `#DeclaracionTipo`, `#Asignacion`, `#If`, `#For`, `#While`, `#LlamadaProc`.

Añade nodos a las expresiones (opcional para esta unidad, útil para la siguiente): deja `expresion()`, `relacion()`, etc. **sin** anotar por ahora — `NODE_DEFAULT_VOID` hace que no generen nodo, lo cual mantiene el árbol plano y legible. Las clases de nodo concretas (con tipado fuerte) se difieren a la Unidad 1 según la spec §4.4 y el plan de recorte.

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=AstTest`
Expected: PASA (3 tests).

- [ ] **Step 5: Verificar que `GramaticaSubprogramasTest` sigue compilando** (cambió la firma de `programa()`)

Run: `mvn -q test -Dtest=Gramatica*,AstTest`
Expected: PASA. Si algún test de gramática no compila por la firma `SimpleNode`, no requiere cambio: llamar a `programa()` e ignorar el retorno sigue siendo válido.

- [ ] **Step 6: Commit**

```bash
git add src/main/javacc/Ada.jjt src/test/java/com/compiladorada/sintactico/AstTest.java
git commit -m "feat: AST JJTree navegable con posiciones de token"
```

---

## Task 10: Recuperación de errores léxicos (tokens trampa)

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (añade tokens trampa antes del catch-all)
- Modify: `src/main/java/com/compiladorada/lexico/AnalizadorLexico.java` (mensajes específicos por trampa)
- Test: `src/test/java/com/compiladorada/lexico/RecuperacionLexicaTest.java`

**Interfaces:**
- Consumes: Task 4.
- Produces: nuevos tokens `CADENA_SIN_CERRAR`, `CARACTER_MALFORMADO`, `IDENT_MALFORMADO`; `AnalizadorLexico` los reporta como `ErrorCompilacion(LEXICO, ...)` con mensaje descriptivo y `TipoToken.ERROR` en la tabla, sin abortar.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.lexico;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecuperacionLexicaTest {

    private List<ErrorCompilacion> errores(String fuente) {
        return new AnalizadorLexico(fuente).errores();
    }

    @Test
    void cadena_sin_cerrar() {
        List<ErrorCompilacion> e = errores("X := \"hola;\nY := 1;");
        assertEquals(1, e.size());
        assertTrue(e.get(0).mensaje().toLowerCase().contains("cadena"));
    }

    @Test
    void identificador_con_doble_guion_bajo() {
        List<ErrorCompilacion> e = errores("mi__variable : Integer;");
        assertEquals(1, e.size());
        assertTrue(e.get(0).mensaje().contains("_"));
    }

    @Test
    void identificador_que_termina_en_guion_bajo() {
        List<ErrorCompilacion> e = errores("contador_ : Integer;");
        assertEquals(1, e.size());
    }

    @Test
    void varios_errores_lexicos_en_la_misma_fuente() {
        List<ErrorCompilacion> e = errores("a__b : Integer; c := $; d_ : Float;");
        assertEquals(3, e.size());
    }

    @Test
    void el_analisis_continua_tras_cada_error() {
        // Tras los errores debe seguir habiendo tokens válidos reconocidos.
        AnalizadorLexico a = new AnalizadorLexico("a__b : Integer;");
        assertTrue(a.tokens().stream()
                .anyMatch(t -> t.tipo() == TipoToken.IDENTIFICADOR && t.lexema().equals("Integer")));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=RecuperacionLexicaTest`
Expected: FALLA — `mi__variable` hoy se lexa como dos tokens válidos; cadena sin cerrar lanza `TokenMgrError`.

- [ ] **Step 3: Añadir tokens trampa en `Ada.jjt`** justo **antes** del `<IDENTIFICADOR>` y del catch-all `<ERROR_LEXICO>`

```java
/* ===== Tokens trampa: patrones inválidos que se reconocen para reportarlos ===== */
TOKEN :
{
    <CADENA_SIN_CERRAR: "\"" (~["\"","\n","\r"])* ("\n" | "\r" | "\r\n")>
  | <CARACTER_MALFORMADO: "'" (~["'","\n","\r"]) (~["'","\n","\r"])+ "'">
  | <IDENT_MALFORMADO:
        ["a"-"z","A"-"Z"] (["a"-"z","A"-"Z","0"-"9","_"])* "_"
      | ["a"-"z","A"-"Z"] (["a"-"z","A"-"Z","0"-"9"])* ("_" ("_")+ (["a"-"z","A"-"Z","0"-"9"])*)+ >
}
```

Coloca este bloque **antes** del bloque `TOKEN : { <IDENTIFICADOR: ...> }` para que el matcher de mayor longitud / primera regla capture los patrones malformados. Verifica el orden final: literales válidos -> trampas -> identificador válido -> `ERROR_LEXICO`.

- [ ] **Step 4: Manejar los nuevos kinds en `AnalizadorLexico.clasificar` y en `agregar`**

En `clasificar(...)`, añade casos que devuelven `TipoToken.ERROR`:

```java
            case AdaParserConstants.CADENA_SIN_CERRAR:
            case AdaParserConstants.CARACTER_MALFORMADO:
            case AdaParserConstants.IDENT_MALFORMADO:
                return TipoToken.ERROR;
```

En `agregar(Token t)`, sustituye el bloque `if (tipo == TipoToken.ERROR)` por un mensaje según el kind:

```java
        if (tipo == TipoToken.ERROR) {
            errores.add(new ErrorCompilacion(Categoria.LEXICO, t.beginLine, t.beginColumn,
                    mensajeLexico(t)));
        }
```

y añade:

```java
    private String mensajeLexico(Token t) {
        switch (t.kind) {
            case AdaParserConstants.CADENA_SIN_CERRAR:
                return "cadena sin cerrar antes de fin de línea";
            case AdaParserConstants.CARACTER_MALFORMADO:
                return "literal de carácter mal formado: " + t.image;
            case AdaParserConstants.IDENT_MALFORMADO:
                return "identificador no válido '" + t.image
                        + "': no puede terminar en '_' ni contener '__'";
            default:
                return "carácter no válido '" + t.image + "'";
        }
    }
```

- [ ] **Step 5: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=RecuperacionLexicaTest,AnalizadorLexicoTest`
Expected: PASA (11 tests). Si `AnalizadorLexicoTest` regresiona porque un identificador válido ahora cae en `IDENT_MALFORMADO`, afinar la ER de `IDENT_MALFORMADO` (el problema típico es que `[..._]*_` también matchea nombres válidos con un solo `_` interno seguido de letras — la ER de arriba solo matchea si **termina** en `_` o tiene `__`).

- [ ] **Step 6: Commit**

```bash
git add src/main/javacc/Ada.jjt src/main/java/com/compiladorada/lexico/AnalizadorLexico.java src/test/java/com/compiladorada/lexico/RecuperacionLexicaTest.java
git commit -m "feat: recuperación de errores léxicos con tokens trampa"
```

---

## Task 11: Recuperación de errores sintácticos (modo pánico) y traducción de mensajes

**Files:**
- Modify: `src/main/javacc/Ada.jjt` (envolver producciones de nivel en try/catch + `error_skipto`; helpers en un bloque de código Java dentro del parser)
- Create: `src/main/java/com/compiladorada/sintactico/TraductorMensajes.java`
- Test: `src/test/java/com/compiladorada/sintactico/RecuperacionSintacticaTest.java`

**Interfaces:**
- Consumes: gramática completa (Tasks 5-9), `ErrorCompilacion` (Task 2).
- Produces:
  - `AdaParser` acumula errores en un campo `private final java.util.List<ErrorCompilacion> erroresSintacticos = new java.util.ArrayList<>();` con getter `public java.util.List<ErrorCompilacion> getErroresSintacticos()`.
  - `programa()` ya no lanza `ParseException` en la mayoría de los casos: recupera y sigue. Solo lanza si la recuperación no puede avanzar.
  - `TraductorMensajes.traducir(ParseException e)` -> `String` en español legible.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.sintactico;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecuperacionSintacticaTest {

    private List<ErrorCompilacion> errores(String fuente) throws Exception {
        AdaParser p = new AdaParser(new StringReader(fuente));
        try {
            p.programa();
        } catch (Exception ignorada) {
            // la recuperación puede rendirse; los errores acumulados siguen valiendo
        }
        return p.getErroresSintacticos();
    }

    @Test
    void reporta_un_error_por_punto_como_minimo() throws Exception {
        List<ErrorCompilacion> e = errores("procedure P is begin null end;");
        assertFalse(e.isEmpty());
        assertEquals(ErrorCompilacion.Categoria.SINTACTICO, e.get(0).categoria());
    }

    @Test
    void recupera_y_encuentra_multiples_errores_en_distintas_lineas() throws Exception {
        String fuente = ""
                + "procedure P is\n"
                + "  X : Integer\n"      // falta ';'
                + "begin\n"
                + "  Y := ;\n"           // expresión inválida
                + "  Z := 1\n"           // falta ';'
                + "end;\n";
        List<ErrorCompilacion> e = errores(fuente);
        assertTrue(e.size() >= 2, "se esperaban >=2 errores, hubo " + e.size());
        long lineasDistintas = e.stream().map(ErrorCompilacion::linea).distinct().count();
        assertTrue(lineasDistintas >= 2);
    }

    @Test
    void los_mensajes_estan_en_espaniol() throws Exception {
        List<ErrorCompilacion> e = errores("procedure P is begin null end;");
        assertTrue(e.get(0).mensaje().toLowerCase().contains("se esperaba")
                || e.get(0).mensaje().toLowerCase().contains("encontr"));
    }

    @Test
    void programa_valido_no_produce_errores() throws Exception {
        assertTrue(errores("procedure P is begin null; end;").isEmpty());
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=RecuperacionSintacticaTest`
Expected: FALLA — no existe `getErroresSintacticos()`; `programa()` aborta en el primer error.

- [ ] **Step 3: Crear `TraductorMensajes.java`**

```java
package com.compiladorada.sintactico;

import com.compiladorada.generado.ParseException;
import com.compiladorada.generado.Token;

import java.util.Map;

public final class TraductorMensajes {

    private static final Map<String, String> NOMBRES = Map.ofEntries(
            Map.entry("\";\"", "';'"),
            Map.entry("\":\"", "':'"),
            Map.entry("\"(\"", "'('"),
            Map.entry("\")\"", "')'"),
            Map.entry("\":=\"", "':='"),
            Map.entry("\"=>\"", "'=>'"),
            Map.entry("\"..\"", "'..'"),
            Map.entry("<IDENTIFICADOR>", "un identificador"),
            Map.entry("<ENTERO>", "un número entero"),
            Map.entry("<REAL>", "un número real"),
            Map.entry("<CADENA>", "una cadena"),
            Map.entry("<EOF>", "el fin del archivo"),
            Map.entry("\"then\"", "'then'"),
            Map.entry("\"loop\"", "'loop'"),
            Map.entry("\"is\"", "'is'"),
            Map.entry("\"begin\"", "'begin'"),
            Map.entry("\"end\"", "'end'"),
            Map.entry("\"record\"", "'record'"));

    private TraductorMensajes() {
    }

    public static String traducir(ParseException e) {
        Token ofensor = e.currentToken != null && e.currentToken.next != null
                ? e.currentToken.next
                : e.currentToken;
        String encontrado = ofensor != null ? ofensor.image : "?";

        StringBuilder esperados = new StringBuilder();
        if (e.expectedTokenSequences != null) {
            java.util.LinkedHashSet<String> vistos = new java.util.LinkedHashSet<>();
            for (int[] seq : e.expectedTokenSequences) {
                if (seq.length > 0) {
                    vistos.add(amigable(e.tokenImage[seq[0]]));
                }
            }
            esperados.append(String.join(" o ", vistos));
        }

        if (esperados.length() > 0) {
            return "se esperaba " + esperados + " pero se encontró '" + encontrado + "'";
        }
        return "construcción no válida cerca de '" + encontrado + "'";
    }

    private static String amigable(String imagenToken) {
        return NOMBRES.getOrDefault(imagenToken, imagenToken);
    }
}
```

- [ ] **Step 4: Añadir el bloque de utilidades y los try/catch en `Ada.jjt`**

Dentro de `PARSER_BEGIN(AdaParser) ... PARSER_END`, amplía la clase:

```java
public class AdaParser {
    public final java.util.List<com.compiladorada.errores.ErrorCompilacion> erroresSintacticos =
            new java.util.ArrayList<>();

    public java.util.List<com.compiladorada.errores.ErrorCompilacion> getErroresSintacticos() {
        return erroresSintacticos;
    }

    private void registrar(ParseException e) {
        Token t = (e.currentToken != null && e.currentToken.next != null)
                ? e.currentToken.next : e.currentToken;
        int linea = t != null ? t.beginLine : 0;
        int col = t != null ? t.beginColumn : 0;
        String msg = com.compiladorada.sintactico.TraductorMensajes.traducir(e);
        if (!esFantasma(linea, col)) {
            erroresSintacticos.add(new com.compiladorada.errores.ErrorCompilacion(
                com.compiladorada.errores.ErrorCompilacion.Categoria.SINTACTICO, linea, col, msg));
        }
        if (erroresSintacticos.size() > 200) {
            throw new RuntimeException("demasiados errores, análisis interrumpido");
        }
    }

    private int ultLinea = -1, ultCol = -1;

    private boolean esFantasma(int linea, int col) {
        boolean cerca = (linea == ultLinea && Math.abs(col - ultCol) <= 2);
        ultLinea = linea; ultCol = col;
        return cerca;
    }

    /** Consume tokens hasta uno de 'sync' (sin consumirlo) o EOF, respetando paréntesis. */
    private void sincronizar(int... sync) {
        int prof = 0;
        while (true) {
            Token sig = getToken(1);
            if (sig.kind == EOF) return;
            if (sig.kind == LPAREN) prof++;
            else if (sig.kind == RPAREN && prof > 0) prof--;
            else if (prof == 0) {
                for (int k : sync) if (sig.kind == k) return;
            }
            getNextToken();
        }
    }
}
```

Envuelve las producciones de nivel. `declaracion()`:

```java
void declaracion() : {}
{
    try {
        <KW_TYPE> declaracionTipo()
      | <KW_SUBTYPE> declaracionSubtipo()
      | declaracionVar()
    } catch (ParseException e) {
        registrar(e);
        sincronizar(PYC, KW_BEGIN, KW_END);
        if (getToken(1).kind == PYC) getNextToken();
    }
}
```

`sentencia()` — envuelve el cuerpo en `try { ... } catch (ParseException e) { registrar(e); sincronizar(PYC, KW_END, KW_ELSIF, KW_ELSE, KW_EXCEPTION, KW_WHEN); if (getToken(1).kind == PYC) getNextToken(); }`.

`unidadCompilacion()` — `catch (ParseException e) { registrar(e); sincronizar(KW_PROCEDURE, KW_FUNCTION, KW_PACKAGE); }`.

En `programa()`, envuelve el bucle para no morir si `unidadCompilacion` no avanzó:

```java
SimpleNode programa() #Programa :
{ int guarda; }
{
    (
        { guarda = jj_input_stream.bufpos; }
        unidadCompilacion()
    )+
    <EOF>
    { return jjtThis; }
}
```

Nota: si `jj_input_stream` no es accesible, usar `getToken(1)` antes/después y si es el mismo token forzar `getNextToken()` para garantizar avance.

- [ ] **Step 5: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=RecuperacionSintacticaTest`
Expected: PASA (4 tests). Ajustar los conjuntos de sincronización si un caso traga demasiados tokens (síntoma: menos errores de los esperados) o produce fantasmas (síntoma: más errores que puntos de fallo reales).

- [ ] **Step 6: Ejecutar toda la suite**

Run: `mvn -q test`
Expected: PASA. Los tests de gramática que usaban `assertThrows(ParseException.class, ...)` ahora podrían **no** lanzar (la recuperación los traga). Actualizarlos: cambiar a `assertFalse(parsearYObtenerErrores(fuente).isEmpty())` con un helper local, o mantener `assertThrows` solo donde la recuperación genuinamente se rinde. Documentar el cambio en el mensaje de commit.

- [ ] **Step 7: Commit**

```bash
git add src/main/javacc/Ada.jjt src/main/java/com/compiladorada/sintactico/TraductorMensajes.java src/test/java/com/compiladorada/sintactico
git commit -m "feat: recuperación sintáctica en modo pánico con mensajes en español"
```

---

## Task 12: Fachada `Compilador`

**Files:**
- Modify: `src/main/java/com/compiladorada/ResultadoCompilacion.java` (tipar `ast` a `SimpleNode`)
- Create: `src/main/java/com/compiladorada/Compilador.java`
- Test: `src/test/java/com/compiladorada/CompiladorTest.java`

**Interfaces:**
- Consumes: `AnalizadorLexico` (Tasks 4, 10), `AdaParser` (Tasks 5-11), `ResultadoCompilacion`, `ErrorCompilacion`.
- Produces: `Compilador.analizar(String fuente, String nombreArchivo)` -> `ResultadoCompilacion`. **Nunca lanza.** Si el parser se rinde o algo inesperado ocurre, se captura y se añade un `ErrorCompilacion(SINTACTICO, ...)` final.

- [ ] **Step 1: Tipar `ast` en `ResultadoCompilacion`**

Cambia `Object ast` -> `com.compiladorada.sintactico.nodos.SimpleNode ast` (y el import). `tieneErrores()` no cambia.

- [ ] **Step 2: Escribir el test**

```java
package com.compiladorada;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CompiladorTest {

    @Test
    void programa_valido_sin_errores_y_con_ast() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is begin null; end;", "p.ada");
        assertFalse(r.tieneErrores());
        assertNotNull(r.ast());
        assertFalse(r.tokens().isEmpty());
    }

    @Test
    void separa_errores_lexicos_y_sintacticos() {
        ResultadoCompilacion r = Compilador.analizar(
                "procedure P is X : Integer $ begin null end;", "p.ada");
        assertFalse(r.erroresLexicos().isEmpty());
        assertFalse(r.erroresSintacticos().isEmpty());
    }

    @Test
    void nunca_lanza_con_entrada_valida_vacia_o_basura() {
        assertDoesNotThrow(() -> Compilador.analizar("", "x.ada"));
        assertDoesNotThrow(() -> Compilador.analizar("   \n\n  ", "x.ada"));
        Random rnd = new Random(42);
        for (int i = 0; i < 200; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 80; j++) sb.append((char) (rnd.nextInt(94) + 32));
            String basura = sb.toString();
            assertDoesNotThrow(() -> Compilador.analizar(basura, "fuzz.ada"));
        }
    }

    @Test
    void entrada_vacia_reporta_al_menos_un_error_sintactico() {
        ResultadoCompilacion r = Compilador.analizar("", "x.ada");
        assertTrue(r.tieneErrores());
    }
}
```

- [ ] **Step 3: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=CompiladorTest`
Expected: FALLA — `Compilador` no existe.

- [ ] **Step 4: Crear `Compilador.java`**

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
        AnalizadorLexico lexico = new AnalizadorLexico(fuente);

        List<ErrorCompilacion> sintacticos = new ArrayList<>();
        SimpleNode ast = null;
        AdaParser parser = new AdaParser(new StringReader(fuente));
        try {
            ast = parser.programa();
        } catch (Throwable t) {
            // la recuperación se rindió, o error inesperado: se preserva lo acumulado
        }
        sintacticos.addAll(parser.getErroresSintacticos());
        if (ast == null && sintacticos.isEmpty()) {
            sintacticos.add(new ErrorCompilacion(Categoria.SINTACTICO, 1, 1,
                    "no se pudo construir el árbol sintáctico"));
        }

        return new ResultadoCompilacion(
                lexico.tokens(),
                lexico.errores(),
                List.copyOf(sintacticos),
                ast);
    }
}
```

- [ ] **Step 5: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=CompiladorTest`
Expected: PASA (4 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/compiladorada/Compilador.java src/main/java/com/compiladorada/ResultadoCompilacion.java src/test/java/com/compiladorada/CompiladorTest.java
git commit -m "feat: fachada Compilador.analizar que nunca propaga excepciones"
```

---

## Task 13: `EscritorErrores` — persistencia a `output/`

**Files:**
- Create: `src/main/java/com/compiladorada/errores/EscritorErrores.java`
- Test: `src/test/java/com/compiladorada/errores/EscritorErroresTest.java`

**Interfaces:**
- Consumes: `ResultadoCompilacion`, `ErrorCompilacion`, `TokenLexico`.
- Produces: `EscritorErrores.volcar(ResultadoCompilacion r, java.nio.file.Path dirSalida, String nombreArchivoFuente)` — crea `dirSalida` si no existe; escribe (sobrescribiendo) `tokens.txt`, `errores_lexicos.txt`, `errores_sintacticos.txt`. Cada archivo de errores: cabecera con fecha/hora ISO y conteo, luego una línea `formatear(nombre)` por error. `tokens.txt`: cabecera + tabla alineada `LEXEMA | TIPO | LINEA | COLUMNA`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.errores;

import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EscritorErroresTest {

    @Test
    void escribe_los_tres_archivos_con_formato_esperado(@TempDir Path dir) throws Exception {
        ResultadoCompilacion r = new ResultadoCompilacion(
                List.of(new TokenLexico("Hola", TipoToken.IDENTIFICADOR, 1, 1)),
                List.of(new ErrorCompilacion(ErrorCompilacion.Categoria.LEXICO, 2, 8, "carácter no válido '$'")),
                List.of(new ErrorCompilacion(ErrorCompilacion.Categoria.SINTACTICO, 3, 1, "se esperaba ';'")),
                null);

        EscritorErrores.volcar(r, dir, "prog.ada");

        String lex = Files.readString(dir.resolve("errores_lexicos.txt"));
        String sin = Files.readString(dir.resolve("errores_sintacticos.txt"));
        String tok = Files.readString(dir.resolve("tokens.txt"));

        assertTrue(lex.contains("prog.ada:2:8: error léxico: carácter no válido '$'"));
        assertTrue(lex.contains("1 error"));
        assertTrue(sin.contains("prog.ada:3:1: error sintáctico: se esperaba ';'"));
        assertTrue(tok.contains("Hola"));
        assertTrue(tok.contains("IDENTIFICADOR"));
    }

    @Test
    void sobrescribe_en_cada_llamada(@TempDir Path dir) throws Exception {
        ResultadoCompilacion conError = new ResultadoCompilacion(List.of(),
                List.of(new ErrorCompilacion(ErrorCompilacion.Categoria.LEXICO, 1, 1, "x")),
                List.of(), null);
        ResultadoCompilacion limpio = new ResultadoCompilacion(List.of(), List.of(), List.of(), null);

        EscritorErrores.volcar(conError, dir, "p.ada");
        EscritorErrores.volcar(limpio, dir, "p.ada");

        String lex = Files.readString(dir.resolve("errores_lexicos.txt"));
        assertFalse(lex.contains("1:1"));
        assertTrue(lex.contains("0 errores") || lex.contains("Sin errores"));
    }

    @Test
    void crea_la_carpeta_si_no_existe(@TempDir Path base) throws Exception {
        Path dir = base.resolve("output");
        EscritorErrores.volcar(new ResultadoCompilacion(List.of(), List.of(), List.of(), null),
                dir, "p.ada");
        assertTrue(Files.isDirectory(dir));
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=EscritorErroresTest`
Expected: FALLA — clase inexistente.

- [ ] **Step 3: Crear `EscritorErrores.java`**

```java
package com.compiladorada.errores;

import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.lexico.TokenLexico;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class EscritorErrores {

    private EscritorErrores() {
    }

    public static void volcar(ResultadoCompilacion r, Path dirSalida, String nombreArchivoFuente) {
        try {
            Files.createDirectories(dirSalida);
            escribirErrores(dirSalida.resolve("errores_lexicos.txt"),
                    "ERRORES LÉXICOS", r.erroresLexicos(), nombreArchivoFuente);
            escribirErrores(dirSalida.resolve("errores_sintacticos.txt"),
                    "ERRORES SINTÁCTICOS", r.erroresSintacticos(), nombreArchivoFuente);
            escribirTokens(dirSalida.resolve("tokens.txt"), r.tokens());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escribirErrores(Path destino, String titulo,
                                        List<ErrorCompilacion> errores, String nombre) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("== ").append(titulo).append(" ==\n");
        sb.append("Compilado: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("Total: ").append(errores.size())
          .append(errores.size() == 1 ? " error" : " errores").append("\n\n");
        if (errores.isEmpty()) {
            sb.append("Sin errores.\n");
        } else {
            for (ErrorCompilacion e : errores) {
                sb.append(e.formatear(nombre)).append('\n');
            }
        }
        Files.writeString(destino, sb.toString());
    }

    private static void escribirTokens(Path destino, List<TokenLexico> tokens) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("== TABLA DE TOKENS ==\n");
        sb.append("Compilado: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("Total: ").append(tokens.size()).append("\n\n");
        sb.append(String.format("%-24s %-22s %6s %8s%n", "LEXEMA", "TIPO", "LINEA", "COLUMNA"));
        sb.append("-".repeat(62)).append('\n');
        for (TokenLexico t : tokens) {
            sb.append(String.format("%-24s %-22s %6d %8d%n",
                    recortar(t.lexema()), t.tipo(), t.linea(), t.columna()));
        }
        Files.writeString(destino, sb.toString());
    }

    private static String recortar(String s) {
        String limpio = s.replace("\n", "\\n").replace("\t", "\\t");
        return limpio.length() <= 24 ? limpio : limpio.substring(0, 21) + "...";
    }
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=EscritorErroresTest`
Expected: PASA (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/errores/EscritorErrores.java src/test/java/com/compiladorada/errores/EscritorErroresTest.java
git commit -m "feat: persistencia de tokens y errores a output/"
```

---

## Task 14: Batería de casos válidos e inválidos

**Files:**
- Create: `src/test/resources/casos/validos/*.ada` (8 archivos)
- Create: `src/test/resources/casos/invalidos/*.ada` + `*.expected` (10 pares)
- Create: `src/test/java/com/compiladorada/CasosDePruebaTest.java`
- Delete: los `.gitkeep` de esas carpetas

**Interfaces:**
- Consumes: `Compilador.analizar`.
- Produces: nada de código productivo; fija el comportamiento del compilador sobre programas realistas.

- [ ] **Step 1: Crear los 8 casos válidos** (deben dar 0 errores)

`src/test/resources/casos/validos/01_procedimiento_minimo.ada`:
```ada
procedure Vacio is
begin
   null;
end Vacio;
```

`02_funcion_con_parametros.ada`:
```ada
function Suma (A : Integer; B : Integer) return Integer is
   R : Integer;
begin
   R := A + B;
end Suma;
```

`03_paquete_spec_y_body.ada`:
```ada
package Contador is
   Valor : Integer;
   Limite : constant Integer := 100;
end Contador;
```

`04_tipos_del_usuario.ada`:
```ada
procedure Tipos is
   type Grado is range 0 .. 100;
   type Color is (Rojo, Verde, Azul);
   type Punto is record
      X : Integer;
      Y : Integer;
   end record;
   type Fila is array (1 .. 8) of Integer;
   subtype Positivo is Integer range 1 .. 100;
begin
   null;
end Tipos;
```

`05_control_anidado.ada`:
```ada
procedure Control is
   X : Integer;
begin
   if X = 1 then
      X := 10;
   elsif X = 2 then
      X := 20;
   else
      X := 0;
   end if;
end Control;
```

`06_ciclos.ada`:
```ada
procedure Ciclos is
   Total : Integer;
begin
   Total := 0;
   for I in reverse 1 .. 10 loop
      Total := Total + I;
   end loop;
   while Total > 0 loop
      Total := Total - 1;
   end loop;
end Ciclos;
```

`07_expresiones.ada`:
```ada
procedure Expr is
   A, B, C : Integer;
   R : Boolean;
begin
   A := 1 + 2 * 3 - 4 / 2;
   B := 2 ** 3;
   C := A mod B rem 2;
   R := (A > 0 and then B < 100) or else not (C = 0);
end Expr;
```

`08_excepciones.ada`:
```ada
procedure Exc is
   X : Integer;
begin
   X := 1;
exception
   when Constraint_Error | Program_Error =>
      X := 0;
   when others =>
      raise;
end Exc;
```

- [ ] **Step 2: Crear los 10 casos inválidos con su `.expected`**

Formato de `.expected`: una línea `linea:columna:categoria` por error esperado (categoria = `LEXICO` o `SINTACTICO`). El test comprueba que **cada** línea esperada aparece entre los errores reales (no exige igualdad exacta de conteo, por el ruido del modo pánico).

`invalidos/01_caracter_ilegal.ada`:
```ada
procedure P is
   X : Integer := 5 $ 3;
begin
   null;
end P;
```
`invalidos/01_caracter_ilegal.expected`:
```
2:21:LEXICO
```

`invalidos/02_cadena_sin_cerrar.ada`:
```ada
procedure P is
   S : String := "hola;
begin
   null;
end P;
```
`02_cadena_sin_cerrar.expected`:
```
2:18:LEXICO
```

`invalidos/03_identificador_doble_guion.ada`:
```ada
procedure P is
   mi__var : Integer;
begin
   null;
end P;
```
`03_identificador_doble_guion.expected`:
```
2:4:LEXICO
```

`invalidos/04_falta_punto_y_coma.ada`:
```ada
procedure P is
   X : Integer
begin
   null;
end P;
```
`04_falta_punto_y_coma.expected`:
```
3:1:SINTACTICO
```

`invalidos/05_end_if_faltante.ada`:
```ada
procedure P is
   X : Integer;
begin
   if X = 1 then
      X := 2;
end P;
```
`05_end_if_faltante.expected`:
```
6:1:SINTACTICO
```

`invalidos/06_parentesis_desbalanceado.ada`:
```ada
procedure P is
   X : Integer;
begin
   X := (1 + 2 * 3;
end P;
```
`06_parentesis_desbalanceado.expected`:
```
4:18:SINTACTICO
```

`invalidos/07_then_faltante.ada`:
```ada
procedure P is
   X : Integer;
begin
   if X = 1
      X := 2;
   end if;
end P;
```
`07_then_faltante.expected`:
```
5:7:SINTACTICO
```

`invalidos/08_multiples_errores.ada`:
```ada
procedure P is
   X : Integer
   Y : Integer;
begin
   X := 1
   Y := 2;
   Z := ;
end P;
```
`08_multiples_errores.expected`:
```
3:4:SINTACTICO
6:4:SINTACTICO
7:9:SINTACTICO
```

`invalidos/09_loop_sin_end.ada`:
```ada
procedure P is
begin
   for I in 1 .. 10 loop
      null;
end P;
```
`09_loop_sin_end.expected`:
```
5:1:SINTACTICO
```

`invalidos/10_mezcla_lexico_sintactico.ada`:
```ada
procedure P is
   contador_ : Integer := 1 @ 2
begin
   null
end P;
```
`10_mezcla_lexico_sintactico.expected`:
```
2:4:LEXICO
2:29:LEXICO
3:1:SINTACTICO
```

Nota para el implementador: las columnas exactas de `.expected` pueden requerir ajuste tras la primera ejecución (dependen de cómo JavaCC cuenta columnas y de dónde cae el token ofensor tras la recuperación). Ejecuta el test, mira los errores reales que imprime, y corrige los `.expected` a los valores reales **solo si** el error correcto está presente en la línea correcta. No relajes el test para que pase con errores en líneas equivocadas.

- [ ] **Step 3: Escribir `CasosDePruebaTest.java`**

```java
package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CasosDePruebaTest {

    private static final Path VALIDOS = Path.of("src/test/resources/casos/validos");
    private static final Path INVALIDOS = Path.of("src/test/resources/casos/invalidos");

    @TestFactory
    Stream<DynamicTest> casos_validos_sin_errores() throws IOException {
        return Files.list(VALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    ResultadoCompilacion r = Compilador.analizar(Files.readString(p),
                            p.getFileName().toString());
                    assertTrue(r.erroresLexicos().isEmpty(),
                            "léxicos inesperados: " + r.erroresLexicos());
                    assertTrue(r.erroresSintacticos().isEmpty(),
                            "sintácticos inesperados: " + r.erroresSintacticos());
                }));
    }

    @TestFactory
    Stream<DynamicTest> casos_invalidos_contienen_los_errores_esperados() throws IOException {
        return Files.list(INVALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    ResultadoCompilacion r = Compilador.analizar(Files.readString(p),
                            p.getFileName().toString());
                    List<ErrorCompilacion> todos = new java.util.ArrayList<>();
                    todos.addAll(r.erroresLexicos());
                    todos.addAll(r.erroresSintacticos());

                    Path esperado = p.resolveSibling(
                            p.getFileName().toString().replace(".ada", ".expected"));
                    for (String linea : Files.readAllLines(esperado)) {
                        if (linea.isBlank()) continue;
                        String[] pt = linea.trim().split(":");
                        int ln = Integer.parseInt(pt[0]);
                        int col = Integer.parseInt(pt[1]);
                        ErrorCompilacion.Categoria cat = ErrorCompilacion.Categoria.valueOf(pt[2]);
                        assertTrue(
                                todos.stream().anyMatch(e -> e.linea() == ln
                                        && e.columna() == col && e.categoria() == cat),
                                "falta el error " + linea + " en " + p.getFileName()
                                        + "; errores reales: " + todos);
                    }
                }));
    }
}
```

- [ ] **Step 4: Ejecutar, ajustar los `.expected`, verlo pasar**

Run: `mvn -q test -Dtest=CasosDePruebaTest`
Expected: primero probablemente FALLA en algunos `.expected` por columnas; ajústalos a los valores reales (respetando línea y categoría). Meta: PASA (18 tests dinámicos). Si un caso válido produce error, es un hueco de la gramática -> volver a la Task de gramática correspondiente, añadir un test de regresión ahí, y arreglar.

- [ ] **Step 5: Quitar los `.gitkeep` y commit**

```bash
git rm src/test/resources/casos/validos/.gitkeep src/test/resources/casos/invalidos/.gitkeep
git add src/test/resources/casos src/test/java/com/compiladorada/CasosDePruebaTest.java
git commit -m "test: batería de casos válidos e inválidos del subconjunto de Ada"
```

---

## Task 15: IDE — `EditorPanel` (editor, abrir, guardar)

**Files:**
- Create: `src/main/java/com/compiladorada/ide/EditorPanel.java`
- Test: `src/test/java/com/compiladorada/ide/EditorPanelTest.java`

**Interfaces:**
- Consumes: RSyntaxTextArea (`org.fife.ui.rsyntaxtextarea.RSyntaxTextArea`, `org.fife.ui.rtextarea.RTextScrollPane`).
- Produces: `EditorPanel extends JPanel`:
  - `String getTexto()`
  - `void setTexto(String)`
  - `String getNombreArchivo()` — nombre simple del archivo actual, o `"fuente_sin_guardar.ada"` si nunca se guardó
  - `java.util.Optional<Path> getRutaArchivo()`
  - `boolean isModificado()`
  - `void abrir(Path)` / `void guardar()` / `void guardarComo(Path)` / `void nuevo()`
  - `void addCaretListener(javax.swing.event.CaretListener)` (delegado al textarea)
  - `RSyntaxTextArea getTextArea()` (para acciones de edición y para que `VentanaPrincipal` mueva el cursor)

- [ ] **Step 1: Escribir el test** (headless; RSyntaxTextArea funciona sin display para operaciones de modelo)

```java
package com.compiladorada.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EditorPanelTest {

    @Test
    void nuevo_editor_no_esta_modificado_y_tiene_nombre_por_defecto() {
        EditorPanel e = new EditorPanel();
        assertFalse(e.isModificado());
        assertEquals("fuente_sin_guardar.ada", e.getNombreArchivo());
    }

    @Test
    void set_texto_y_get_texto() {
        EditorPanel e = new EditorPanel();
        e.setTexto("procedure P is begin null; end;");
        assertEquals("procedure P is begin null; end;", e.getTexto());
    }

    @Test
    void abrir_carga_contenido_y_nombre(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("demo.ada");
        Files.writeString(f, "procedure Demo is begin null; end;");
        EditorPanel e = new EditorPanel();
        e.abrir(f);
        assertEquals("procedure Demo is begin null; end;", e.getTexto());
        assertEquals("demo.ada", e.getNombreArchivo());
        assertFalse(e.isModificado());
    }

    @Test
    void guardar_persiste_y_limpia_el_flag_modificado(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("out.ada");
        EditorPanel e = new EditorPanel();
        e.setTexto("procedure X is begin null; end;");
        assertTrue(e.isModificado());
        e.guardarComo(f);
        assertEquals("procedure X is begin null; end;", Files.readString(f));
        assertFalse(e.isModificado());
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=EditorPanelTest`
Expected: FALLA — clase inexistente.

- [ ] **Step 3: Crear `EditorPanel.java`**

```java
package com.compiladorada.ide;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class EditorPanel extends JPanel {

    private static final String NOMBRE_POR_DEFECTO = "fuente_sin_guardar.ada";

    private final RSyntaxTextArea area = new RSyntaxTextArea(30, 80);
    private Path rutaActual;
    private boolean modificado;

    public EditorPanel() {
        super(new BorderLayout());
        area.setCodeFoldingEnabled(false);
        area.setTabSize(3);
        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { modificado = true; }
            public void removeUpdate(DocumentEvent e) { modificado = true; }
            public void changedUpdate(DocumentEvent e) { modificado = true; }
        });
    }

    public RSyntaxTextArea getTextArea() {
        return area;
    }

    public String getTexto() {
        return area.getText();
    }

    public void setTexto(String texto) {
        area.setText(texto);
        area.setCaretPosition(0);
        modificado = true;
    }

    public boolean isModificado() {
        return modificado;
    }

    public String getNombreArchivo() {
        return rutaActual != null ? rutaActual.getFileName().toString() : NOMBRE_POR_DEFECTO;
    }

    public Optional<Path> getRutaArchivo() {
        return Optional.ofNullable(rutaActual);
    }

    public void nuevo() {
        area.setText("");
        rutaActual = null;
        modificado = false;
    }

    public void abrir(Path ruta) {
        try {
            area.setText(Files.readString(ruta));
            area.setCaretPosition(0);
            rutaActual = ruta;
            modificado = false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void guardar() {
        if (rutaActual == null) {
            throw new IllegalStateException("no hay ruta asignada; usar guardarComo");
        }
        guardarComo(rutaActual);
    }

    public void guardarComo(Path ruta) {
        try {
            Files.writeString(ruta, area.getText());
            rutaActual = ruta;
            modificado = false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void addCaretListener(CaretListener l) {
        area.addCaretListener(l);
    }
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=EditorPanelTest`
Expected: PASA (4 tests). Si falla por entorno headless al construir `RSyntaxTextArea`, añadir `@BeforeAll` con `System.setProperty("java.awt.headless", "false")` no ayuda; en su lugar anotar la clase con `@EnabledIfSystemProperty` o ejecutar con `-Djava.awt.headless=true` (RSyntaxTextArea tolera headless para el modelo de texto). Documentar en el commit si algún test se marca `@Disabled` por headless.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/ide/EditorPanel.java src/test/java/com/compiladorada/ide/EditorPanelTest.java
git commit -m "feat: EditorPanel con abrir/guardar sobre RSyntaxTextArea"
```

---

## Task 16: IDE — `BarraEstado`, `TablaTokensPanel`, `PanelErrores`

**Files:**
- Create: `src/main/java/com/compiladorada/ide/BarraEstado.java`
- Create: `src/main/java/com/compiladorada/ide/TablaTokensPanel.java`
- Create: `src/main/java/com/compiladorada/ide/PanelErrores.java`
- Test: `src/test/java/com/compiladorada/ide/PanelesTest.java`

**Interfaces:**
- Produces:
  - `BarraEstado extends JPanel`: `void setPosicionCursor(int linea, int columna)`, `void setResultado(String)`, `String getTextoPosicion()`.
  - `TablaTokensPanel extends JPanel`: `void setTokens(List<TokenLexico>)`, `int getFilas()`.
  - `PanelErrores extends JPanel`: `void setErrores(List<ErrorCompilacion> lexicos, List<ErrorCompilacion> sintacticos)`, `int getFilasLexicas()`, `int getFilasSintacticas()`, `void setOnSeleccion(java.util.function.BiConsumer<Integer,Integer> irALineaColumna)` (doble clic -> llama con línea/columna).

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.ide;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelesTest {

    @Test
    void barra_estado_formatea_la_posicion() {
        BarraEstado b = new BarraEstado();
        b.setPosicionCursor(12, 8);
        assertEquals("Ln 12, Col 8", b.getTextoPosicion());
    }

    @Test
    void tabla_tokens_refleja_la_lista() {
        TablaTokensPanel p = new TablaTokensPanel();
        p.setTokens(List.of(
                new TokenLexico("P", TipoToken.IDENTIFICADOR, 1, 1),
                new TokenLexico(";", TipoToken.DELIMITADOR_SIMPLE, 1, 2)));
        assertEquals(2, p.getFilas());
    }

    @Test
    void panel_errores_separa_lexicos_de_sintacticos() {
        PanelErrores p = new PanelErrores();
        p.setErrores(
                List.of(new ErrorCompilacion(Categoria.LEXICO, 1, 1, "a")),
                List.of(new ErrorCompilacion(Categoria.SINTACTICO, 2, 1, "b"),
                        new ErrorCompilacion(Categoria.SINTACTICO, 3, 1, "c")));
        assertEquals(1, p.getFilasLexicas());
        assertEquals(2, p.getFilasSintacticas());
    }

    @Test
    void set_errores_reemplaza_el_contenido_anterior() {
        PanelErrores p = new PanelErrores();
        p.setErrores(List.of(new ErrorCompilacion(Categoria.LEXICO, 1, 1, "a")), List.of());
        p.setErrores(List.of(), List.of());
        assertEquals(0, p.getFilasLexicas());
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=PanelesTest`
Expected: FALLA — clases inexistentes.

- [ ] **Step 3: Crear las tres clases**

`BarraEstado.java`:

```java
package com.compiladorada.ide;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class BarraEstado extends JPanel {

    private final JLabel posicion = new JLabel("Ln 1, Col 1");
    private final JLabel resultado = new JLabel(" ");

    public BarraEstado() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(posicion, BorderLayout.WEST);
        add(resultado, BorderLayout.EAST);
    }

    public void setPosicionCursor(int linea, int columna) {
        posicion.setText("Ln " + linea + ", Col " + columna);
    }

    public String getTextoPosicion() {
        return posicion.getText();
    }

    public void setResultado(String texto) {
        resultado.setText(texto);
    }
}
```

`TablaTokensPanel.java`:

```java
package com.compiladorada.ide;

import com.compiladorada.lexico.TokenLexico;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public class TablaTokensPanel extends JPanel {

    private final Modelo modelo = new Modelo();

    public TablaTokensPanel() {
        super(new BorderLayout());
        JTable tabla = new JTable(modelo);
        tabla.setAutoCreateRowSorter(true);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    public void setTokens(List<TokenLexico> tokens) {
        modelo.datos = new ArrayList<>(tokens);
        modelo.fireTableDataChanged();
    }

    public int getFilas() {
        return modelo.datos.size();
    }

    private static class Modelo extends AbstractTableModel {
        private final String[] cols = {"Token", "Tipo", "Línea", "Columna"};
        private List<TokenLexico> datos = new ArrayList<>();

        public int getRowCount() { return datos.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            TokenLexico t = datos.get(r);
            return switch (c) {
                case 0 -> t.lexema();
                case 1 -> t.tipo().toString();
                case 2 -> t.linea();
                case 3 -> t.columna();
                default -> "";
            };
        }
    }
}
```

`PanelErrores.java`:

```java
package com.compiladorada.ide;

import com.compiladorada.errores.ErrorCompilacion;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class PanelErrores extends JPanel {

    private final Modelo lexicos = new Modelo();
    private final Modelo sintacticos = new Modelo();
    private final JTabbedPane pestanias = new JTabbedPane();
    private final JTable tablaLex = new JTable(lexicos);
    private final JTable tablaSin = new JTable(sintacticos);
    private BiConsumer<Integer, Integer> onSeleccion = (l, c) -> { };

    public PanelErrores() {
        super(new BorderLayout());
        pestanias.addTab("Léxicos (0)", new JScrollPane(tablaLex));
        pestanias.addTab("Sintácticos (0)", new JScrollPane(tablaSin));
        add(pestanias, BorderLayout.CENTER);
        instalarDobleClic(tablaLex, lexicos);
        instalarDobleClic(tablaSin, sintacticos);
    }

    public void setErrores(List<ErrorCompilacion> lex, List<ErrorCompilacion> sin) {
        lexicos.datos = new ArrayList<>(lex);
        sintacticos.datos = new ArrayList<>(sin);
        lexicos.fireTableDataChanged();
        sintacticos.fireTableDataChanged();
        pestanias.setTitleAt(0, "Léxicos (" + lex.size() + ")");
        pestanias.setTitleAt(1, "Sintácticos (" + sin.size() + ")");
    }

    public int getFilasLexicas() { return lexicos.datos.size(); }
    public int getFilasSintacticas() { return sintacticos.datos.size(); }

    public void setOnSeleccion(BiConsumer<Integer, Integer> cb) {
        this.onSeleccion = cb;
    }

    private void instalarDobleClic(JTable tabla, Modelo modelo) {
        tabla.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int fila = tabla.getSelectedRow();
                    if (fila >= 0) {
                        ErrorCompilacion err = modelo.datos.get(tabla.convertRowIndexToModel(fila));
                        onSeleccion.accept(err.linea(), err.columna());
                    }
                }
            }
        });
    }

    private static class Modelo extends AbstractTableModel {
        private final String[] cols = {"Ln:Col", "Mensaje"};
        private List<ErrorCompilacion> datos = new ArrayList<>();

        public int getRowCount() { return datos.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            ErrorCompilacion e = datos.get(r);
            return c == 0 ? e.linea() + ":" + e.columna() : e.mensaje();
        }
    }
}
```

- [ ] **Step 4: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=PanelesTest`
Expected: PASA (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/compiladorada/ide/BarraEstado.java src/main/java/com/compiladorada/ide/TablaTokensPanel.java src/main/java/com/compiladorada/ide/PanelErrores.java src/test/java/com/compiladorada/ide/PanelesTest.java
git commit -m "feat: barra de estado, tabla de tokens y panel de errores del IDE"
```

---

## Task 17: IDE — `VentanaPrincipal` (menú, layout, flujo Compilar)

**Files:**
- Modify: `src/main/java/com/compiladorada/ide/Main.java`
- Create: `src/main/java/com/compiladorada/ide/VentanaPrincipal.java`
- Test: `src/test/java/com/compiladorada/ide/VentanaPrincipalTest.java`

**Interfaces:**
- Consumes: `EditorPanel`, `BarraEstado`, `TablaTokensPanel`, `PanelErrores`, `Compilador`, `EscritorErrores`.
- Produces: `VentanaPrincipal extends JFrame`:
  - constructor construye el layout (JMenuBar, split panes, barra de estado).
  - `void compilar()` — toma el texto del editor, llama `Compilador.analizar`, actualiza los tres paneles, escribe a `output/`, actualiza la barra de estado. Público para poder testearlo sin simular el menú.
  - `Path getDirectorioSalida()` — `Path.of("output")` por defecto; setter `setDirectorioSalida(Path)` para el test.
  - getters de solo lectura para el test: `TablaTokensPanel getTablaTokens()`, `PanelErrores getPanelErrores()`, `BarraEstado getBarraEstado()`, `EditorPanel getEditor()`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VentanaPrincipalTest {

    @Test
    void compilar_programa_valido_llena_tokens_y_no_muestra_errores(@TempDir Path dir) {
        VentanaPrincipal v = new VentanaPrincipal();
        v.setDirectorioSalida(dir);
        v.getEditor().setTexto("procedure P is begin null; end;");

        v.compilar();

        assertTrue(v.getTablaTokens().getFilas() > 0);
        assertEquals(0, v.getPanelErrores().getFilasLexicas());
        assertEquals(0, v.getPanelErrores().getFilasSintacticas());
        assertTrue(Files.exists(dir.resolve("tokens.txt")));
        assertTrue(Files.exists(dir.resolve("errores_lexicos.txt")));
        assertTrue(Files.exists(dir.resolve("errores_sintacticos.txt")));
    }

    @Test
    void compilar_programa_con_errores_los_separa(@TempDir Path dir) {
        VentanaPrincipal v = new VentanaPrincipal();
        v.setDirectorioSalida(dir);
        v.getEditor().setTexto("procedure P is X : Integer $ begin null end;");

        v.compilar();

        assertTrue(v.getPanelErrores().getFilasLexicas() > 0);
        assertTrue(v.getPanelErrores().getFilasSintacticas() > 0);
    }

    @Test
    void el_menu_tiene_las_secciones_requeridas() {
        VentanaPrincipal v = new VentanaPrincipal();
        java.util.List<String> menus = new java.util.ArrayList<>();
        for (int i = 0; i < v.getJMenuBar().getMenuCount(); i++) {
            menus.add(v.getJMenuBar().getMenu(i).getText());
        }
        assertEquals(java.util.List.of("Archivo", "Editar", "Compilar", "Ver", "Ayuda"), menus);
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=VentanaPrincipalTest`
Expected: FALLA — clase inexistente.

- [ ] **Step 3: Crear `VentanaPrincipal.java`**

```java
package com.compiladorada.ide;

import com.compiladorada.Compilador;
import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.errores.EscritorErrores;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VentanaPrincipal extends JFrame {

    private final EditorPanel editor = new EditorPanel();
    private final TablaTokensPanel tablaTokens = new TablaTokensPanel();
    private final PanelErrores panelErrores = new PanelErrores();
    private final BarraEstado barraEstado = new BarraEstado();
    private Path directorioSalida = Path.of("output");

    public VentanaPrincipal() {
        super("Compilador Ada — IDE");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setJMenuBar(construirMenu());

        JSplitPane arriba = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, tablaTokens);
        arriba.setResizeWeight(0.62);
        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, arriba, panelErrores);
        principal.setResizeWeight(0.72);

        add(principal, BorderLayout.CENTER);
        add(barraEstado, BorderLayout.SOUTH);

        editor.addCaretListener(actualizarPosicion());
        panelErrores.setOnSeleccion(this::irA);
    }

    private CaretListener actualizarPosicion() {
        return e -> {
            var area = editor.getTextArea();
            try {
                int offset = area.getCaretPosition();
                int linea = area.getLineOfOffset(offset);
                int col = offset - area.getLineStartOffset(linea);
                barraEstado.setPosicionCursor(linea + 1, col + 1);
            } catch (BadLocationException ignore) {
                barraEstado.setPosicionCursor(1, 1);
            }
        };
    }

    private JMenuBar construirMenu() {
        JMenuBar barra = new JMenuBar();

        JMenu archivo = new JMenu("Archivo");
        archivo.add(item("Nuevo", e -> editor.nuevo()));
        archivo.add(item("Abrir…", e -> accionAbrir()));
        archivo.add(item("Guardar", e -> accionGuardar()));
        archivo.add(item("Guardar como…", e -> accionGuardarComo()));
        archivo.addSeparator();
        archivo.add(item("Salir", e -> dispose()));
        barra.add(archivo);

        JMenu editar = new JMenu("Editar");
        var area = editor.getTextArea();
        editar.add(item("Deshacer", e -> area.undoLastAction()));
        editar.add(item("Rehacer", e -> area.redoLastAction()));
        editar.addSeparator();
        editar.add(item("Cortar", e -> area.cut()));
        editar.add(item("Copiar", e -> area.copy()));
        editar.add(item("Pegar", e -> area.paste()));
        barra.add(editar);

        JMenu compilar = new JMenu("Compilar");
        JMenuItem compItem = item("Compilar", e -> compilar());
        compItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        compilar.add(compItem);
        compilar.addSeparator();
        compilar.add(item("Abrir carpeta output/", e -> abrirCarpetaSalida()));
        barra.add(compilar);

        JMenu ver = new JMenu("Ver");
        ver.add(item("Mostrar/ocultar tabla de tokens", e -> tablaTokens.setVisible(!tablaTokens.isVisible())));
        ver.add(item("Mostrar/ocultar panel de errores", e -> panelErrores.setVisible(!panelErrores.isVisible())));
        barra.add(ver);

        JMenu ayuda = new JMenu("Ayuda");
        ayuda.add(item("Acerca de…", e -> JOptionPane.showMessageDialog(this,
                "Compilador Ada — IDE\nLenguajes y Autómatas II")));
        barra.add(ayuda);

        return barra;
    }

    private JMenuItem item(String texto, java.awt.event.ActionListener a) {
        JMenuItem it = new JMenuItem(texto);
        it.addActionListener(a);
        return it;
    }

    public void compilar() {
        String fuente = editor.getTexto();
        String nombre = editor.getNombreArchivo();
        ResultadoCompilacion r = Compilador.analizar(fuente, nombre);

        tablaTokens.setTokens(r.tokens());
        panelErrores.setErrores(r.erroresLexicos(), r.erroresSintacticos());

        try {
            Files.createDirectories(directorioSalida);
            if (editor.getRutaArchivo().isEmpty()) {
                Path tmp = directorioSalida.resolve("fuente_sin_guardar.ada");
                Files.writeString(tmp, fuente);
                barraEstado.setResultado("Fuente sin guardar volcada a " + tmp.toAbsolutePath());
            }
            EscritorErrores.volcar(r, directorioSalida, nombre);
        } catch (IOException e) {
            barraEstado.setResultado("No se pudo escribir en " + directorioSalida.toAbsolutePath());
            return;
        }

        barraEstado.setResultado(String.format(
                "Compilado: %d léxicos, %d sintácticos — %s actualizado",
                r.erroresLexicos().size(), r.erroresSintacticos().size(),
                directorioSalida.toAbsolutePath()));
    }

    private void irA(int linea, int columna) {
        var area = editor.getTextArea();
        try {
            int offset = area.getLineStartOffset(Math.max(0, linea - 1)) + Math.max(0, columna - 1);
            area.setCaretPosition(Math.min(offset, area.getText().length()));
            area.requestFocusInWindow();
        } catch (BadLocationException ignore) {
        }
    }

    private void accionAbrir() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            editor.abrir(fc.getSelectedFile().toPath());
        }
    }

    private void accionGuardar() {
        if (editor.getRutaArchivo().isPresent()) {
            editor.guardar();
        } else {
            accionGuardarComo();
        }
    }

    private void accionGuardarComo() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            editor.guardarComo(fc.getSelectedFile().toPath());
        }
    }

    private void abrirCarpetaSalida() {
        try {
            Files.createDirectories(directorioSalida);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directorioSalida.toFile());
            }
        } catch (IOException ignore) {
        }
    }

    public void setDirectorioSalida(Path dir) {
        this.directorioSalida = dir;
    }

    public Path getDirectorioSalida() {
        return directorioSalida;
    }

    public EditorPanel getEditor() {
        return editor;
    }

    public TablaTokensPanel getTablaTokens() {
        return tablaTokens;
    }

    public PanelErrores getPanelErrores() {
        return panelErrores;
    }

    public BarraEstado getBarraEstado() {
        return barraEstado;
    }
}
```

- [ ] **Step 4: Reemplazar `Main.java`**

```java
package com.compiladorada.ide;

import javax.swing.SwingUtilities;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VentanaPrincipal().setVisible(true));
    }
}
```

- [ ] **Step 5: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=VentanaPrincipalTest`
Expected: PASA (3 tests). Si el entorno CI es headless y `new JFrame()` lanza `HeadlessException`, anotar la clase de test con `@org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable` o ejecutar el módulo con `-Djava.awt.headless=false` cuando haya display; en la máquina de desarrollo (con escritorio) pasan.

- [ ] **Step 6: Prueba manual**

Run: `mvn -q -Dexec.mainClass=com.compiladorada.ide.Main exec:java`
Expected: abre la ventana. Verificar a mano: escribir un programa, pulsar F5, ver tabla de tokens y errores; mover el cursor y ver `Ln/Col` cambiar; abrir y guardar un `.ada`; comprobar que `output/` tiene los tres archivos.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/compiladorada/ide/VentanaPrincipal.java src/main/java/com/compiladorada/ide/Main.java src/test/java/com/compiladorada/ide/VentanaPrincipalTest.java
git commit -m "feat: ventana principal del IDE con menú único y flujo de compilación"
```

---

## Task 18: IDE — Resaltado de sintaxis Ada (`AdaTokenMaker`)

**Files:**
- Create: `src/main/java/com/compiladorada/ide/AdaTokenMaker.java`
- Modify: `src/main/java/com/compiladorada/ide/EditorPanel.java` (registrar el TokenMaker y activarlo)
- Test: `src/test/java/com/compiladorada/ide/AdaTokenMakerTest.java`

**Interfaces:**
- Consumes: RSyntaxTextArea (`org.fife.ui.rsyntaxtextarea.AbstractTokenMaker`, `TokenMap`, `Token`, `TokenTypes`).
- Produces: `AdaTokenMaker extends AbstractTokenMaker` con `getWordsToHighlight()` (palabras reservadas -> `RESERVED_WORD`) y `getTokenList(...)` que marca comentarios `--`, cadenas, caracteres y números. `EditorPanel` registra `"text/ada"` y llama `area.setSyntaxEditingStyle("text/ada")`.

- [ ] **Step 1: Escribir el test**

```java
package com.compiladorada.ide;

import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;

import javax.swing.text.Segment;

import static org.junit.jupiter.api.Assertions.*;

class AdaTokenMakerTest {

    private Token primerToken(String linea) {
        Segment s = new Segment(linea.toCharArray(), 0, linea.length());
        return new AdaTokenMaker().getTokenList(s, TokenTypes.NULL, 0);
    }

    @Test
    void marca_palabra_reservada() {
        Token t = primerToken("procedure");
        assertEquals(TokenTypes.RESERVED_WORD, t.getType());
    }

    @Test
    void marca_comentario_de_linea() {
        Token t = primerToken("-- esto es comentario");
        assertEquals(TokenTypes.COMMENT_EOL, t.getType());
    }

    @Test
    void identificador_no_reservado_no_es_palabra_reservada() {
        Token t = primerToken("Contador");
        assertNotEquals(TokenTypes.RESERVED_WORD, t.getType());
    }
}
```

- [ ] **Step 2: Ejecutar y verlo fallar**

Run: `mvn -q test -Dtest=AdaTokenMakerTest`
Expected: FALLA — clase inexistente.

- [ ] **Step 3: Crear `AdaTokenMaker.java`**

```java
package com.compiladorada.ide;

import com.compiladorada.lexico.PalabrasReservadas;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.text.Segment;

public class AdaTokenMaker extends AbstractTokenMaker {

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tm = new TokenMap(true); // ignora mayúsculas: Ada es case-insensitive
        for (String kw : PalabrasReservadas.TODAS) {
            tm.put(kw, TokenTypes.RESERVED_WORD);
        }
        return tm;
    }

    @Override
    public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
        if (tokenType == TokenTypes.IDENTIFIER) {
            int value = wordsToHighlight.get(segment, start, end);
            if (value != -1) {
                tokenType = value;
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset);
    }

    @Override
    public Token getTokenList(Segment text, int startTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int end = offset + text.count;
        int newStartOffset = startOffset - offset;
        int currentTokenStart = offset;
        int currentTokenType = startTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];
            switch (currentTokenType) {
                case TokenTypes.NULL:
                    currentTokenStart = i;
                    if (c == '-' && i + 1 < end && array[i + 1] == '-') {
                        currentTokenType = TokenTypes.COMMENT_EOL;
                    } else if (c == '"') {
                        currentTokenType = TokenTypes.LITERAL_STRING_DOUBLE_QUOTE;
                    } else if (Character.isWhitespace(c)) {
                        currentTokenType = TokenTypes.WHITESPACE;
                    } else if (Character.isDigit(c)) {
                        currentTokenType = TokenTypes.LITERAL_NUMBER_DECIMAL_INT;
                    } else if (Character.isLetter(c)) {
                        currentTokenType = TokenTypes.IDENTIFIER;
                    } else {
                        addToken(text, i, i, TokenTypes.IDENTIFIER, newStartOffset + i);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.COMMENT_EOL:
                    // hasta fin de línea: se consume todo
                    break;

                case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE:
                    if (c == '"') {
                        addToken(text, currentTokenStart, i,
                                TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                    }
                    break;

                case TokenTypes.WHITESPACE:
                    if (!Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1,
                                TokenTypes.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                        i--; // reprocesa c
                    }
                    break;

                case TokenTypes.LITERAL_NUMBER_DECIMAL_INT:
                    if (!(Character.isDigit(c) || c == '.' || c == '_' || c == '#'
                            || c == 'e' || c == 'E')) {
                        addToken(text, currentTokenStart, i - 1,
                                TokenTypes.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                        i--;
                    }
                    break;

                case TokenTypes.IDENTIFIER:
                    if (!(Character.isLetterOrDigit(c) || c == '_')) {
                        addToken(text, currentTokenStart, i - 1,
                                TokenTypes.IDENTIFIER, newStartOffset + currentTokenStart);
                        currentTokenType = TokenTypes.NULL;
                        i--;
                    }
                    break;
            }
        }

        switch (currentTokenType) {
            case TokenTypes.NULL:
                addNullToken();
                break;
            case TokenTypes.COMMENT_EOL:
                addToken(text, currentTokenStart, end - 1, TokenTypes.COMMENT_EOL,
                        newStartOffset + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType,
                        newStartOffset + currentTokenStart);
                addNullToken();
                break;
        }

        return firstToken;
    }
}
```

- [ ] **Step 4: Registrar el TokenMaker en `EditorPanel`**

En el constructor de `EditorPanel`, antes de crear el `RTextScrollPane`:

```java
        org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory atmf =
                (org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory)
                        org.fife.ui.rsyntaxtextarea.TokenMakerFactory.getDefaultInstance();
        atmf.putMapping("text/ada", "com.compiladorada.ide.AdaTokenMaker");
        area.setSyntaxEditingStyle("text/ada");
```

- [ ] **Step 5: Ejecutar y verlo pasar**

Run: `mvn -q test -Dtest=AdaTokenMakerTest,EditorPanelTest`
Expected: PASA. Si `getTokenList` produce un `NullPointerException` en `firstToken`, revisar que `resetTokenList()` se llamó al inicio (lo hace).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/compiladorada/ide/AdaTokenMaker.java src/main/java/com/compiladorada/ide/EditorPanel.java src/test/java/com/compiladorada/ide/AdaTokenMakerTest.java
git commit -m "feat: resaltado de sintaxis Ada en el editor"
```

---

## Task 19: Cierre — suite completa, empaquetado y `.gitignore`

**Files:**
- Modify: `.gitignore` (añadir `output/`)
- Test: toda la suite

**Interfaces:** ninguna nueva.

- [ ] **Step 1: Añadir `output/` a `.gitignore`**

Añade una línea `output/` bajo la sección "Salidas del compilador".

- [ ] **Step 2: Ejecutar toda la suite**

Run: `mvn -q clean test`
Expected: PASA (todas las clases de test). Anotar el conteo total.

- [ ] **Step 3: Verificar el empaquetado**

Run: `mvn -q clean package -DskipTests`
Expected: genera `target/compilador-ada-0.1.0-SNAPSHOT.jar` (shade). Probar: `java -jar target/compilador-ada-0.1.0-SNAPSHOT.jar` abre el IDE.

- [ ] **Step 4: Commit**

```bash
git add .gitignore
git commit -m "build: ignorar output/ y verificar empaquetado ejecutable"
```

---

## Task 20: Documento de diseño de la unidad (entregable académico)

**Files:**
- Create: `docs/unidad-0-diseno-ide.md`

**Interfaces:** ninguna.

- [ ] **Step 1: Redactar `docs/unidad-0-diseno-ide.md`** con estas secciones (contenido real, no placeholders):

1. **Introducción y alcance** — qué resuelve esta unidad; referencia al subconjunto de Ada (`docs/CLAUDE.md`).
2. **Modelo de compilación** — resumen del pipeline de GNAT y qué replica/simplifica el proyecto; por qué el léxico-sintáctico es de una pasada y el resto multipasada (copiar y adaptar la sección 2 de la spec).
3. **Análisis léxico** — tabla completa de tokens con su expresión regular (tomar de `docs/CLAUDE.md` y de `Ada.jjt`); tratamiento de palabras reservadas (case-insensitive, tabla aparte); tokens trampa para errores léxicos.
4. **Análisis sintáctico** — gramática BNF final (extraer de `Ada.jjt`, en notación BNF legible); ambigüedades y cómo se resolvieron con `LOOKAHEAD`.
5. **Recuperación de errores** — estrategia de modo pánico; conjuntos de sincronización por producción; heurística de errores fantasma; ejemplos con entrada/salida reales tomados de `src/test/resources/casos/invalidos/`.
6. **Arquitectura del software** — diagrama de módulos (motor vs IDE); responsabilidad de cada clase; el papel de la fachada `Compilador`.
7. **El IDE** — descripción del layout, el menú, el flujo "Compilar", el formato de los archivos de `output/`; capturas de pantalla.
8. **Pruebas** — tabla de casos válidos e inválidos con el resultado esperado y el obtenido; conteo de tests.
9. **Limitaciones conocidas y trabajo futuro** — qué del subconjunto quedó fuera o parcial; cómo se conecta con la Unidad 1 (semántico) a través del AST y la futura tabla de símbolos.

- [ ] **Step 2: Añadir capturas** — ejecutar el IDE, capturar: (a) editor con un programa y resaltado, (b) tabla de tokens poblada, (c) panel de errores con múltiples errores. Guardar en `docs/img/` y enlazarlas.

- [ ] **Step 3: Commit**

```bash
git add docs/unidad-0-diseno-ide.md docs/img
git commit -m "docs: documento de diseño de la Unidad 0 (IDE léxico-sintáctico)"
```

---

## Auto-revisión del plan

**Cobertura de la spec:**

- Editor + abrir/guardar -> Task 15. ✔
- Indicador de cursor en tiempo real -> Task 16 (`BarraEstado`) + Task 17 (`CaretListener`). ✔
- Tabla de tokens (token | tipo) -> Task 4 (datos) + Task 16 (`TablaTokensPanel`). ✔
- Paneles de errores separados con ubicación y explicación, todos los errores -> Tasks 10-11 (motor) + Task 16 (`PanelErrores`). ✔
- Errores a archivo, sobrescritos en cada compilación -> Task 13 (`EscritorErrores`) + Task 17 (flujo). ✔
- Cobertura del subconjunto de Ada -> Tasks 5-8 + batería Task 14. ✔
- AST JJTree para unidades siguientes -> Task 9. ✔
- Fachada desacoplada motor/IDE -> Task 12. ✔
- Formato de línea estilo GCC -> Task 2 (`ErrorCompilacion.formatear`) + Task 13. ✔
- `output/` fija, `fuente_sin_guardar.ada` cuando no hay archivo -> Task 17. ✔
- Menú único sin toolbar -> Task 17. ✔
- Modelo de compilación de una/varias pasadas (documento) -> Task 20 §2. ✔
- Recorte de emergencia (resaltado recortable, nodos SimpleNode) -> Task 18 es la última funcional antes del cierre; Task 9 deja nodos genéricos. ✔

**Escaneo de placeholders:** sin "TBD"/"TODO" pendientes. Las notas "ajustar tras la primera ejecución" (columnas de `.expected`, rango de kinds de palabras reservadas, headless) son instrucciones concretas de verificación, no huecos de contenido.

**Consistencia de tipos:**

- `Compilador.analizar(String, String) -> ResultadoCompilacion` — usado igual en Tasks 12, 14, 17. ✔
- `ResultadoCompilacion` accessors `tokens() / erroresLexicos() / erroresSintacticos() / ast()` — consistentes en Tasks 2, 12, 13, 14, 17. ✔
- `ErrorCompilacion` record: `categoria() / linea() / columna() / mensaje() / formatear(String)` — consistentes en Tasks 2, 11, 13, 14, 16. ✔
- `AnalizadorLexico(String)` con `tokens()` y `errores()` — Tasks 4, 10, 12. ✔
- `EditorPanel`: `getTexto / setTexto / getNombreArchivo / getRutaArchivo / isModificado / abrir / guardar / guardarComo / nuevo / addCaretListener / getTextArea` — consistentes en Tasks 15, 17, 18. ✔
- `PanelErrores.setErrores(List, List)` + `getFilasLexicas/getFilasSintacticas` + `setOnSeleccion(BiConsumer)` — Tasks 16, 17. ✔
- Nombre del jar del shade: `compilador-ada-0.1.0-SNAPSHOT.jar` coincide con `artifactId` + `version` del `pom.xml`. ✔
- Paquete `com.compiladorada.generado` para las clases JavaCC — usado consistentemente (Tasks 1, 4, 12). Riesgo señalado en Task 1 Step 5 si el plugin deriva el paquete de otra forma.
