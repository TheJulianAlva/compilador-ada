# Generación de código intermedio aritmético — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mostrar, para cada expresión aritmética del programa, su notación prefija, posfija, cuádruplos y valor evaluado, en una pestaña nueva del IDE y en `output/codigo_intermedio.txt`.

**Architecture:** Un paso nuevo (`com.compiladorada.intermedio`) recorre el AST ya construido por JavaCC/JJTree (no se toca `Ada.jjt`). Convierte cada expresión a un `ArbolExpresion` propio; de ese árbol salen prefijo (preorden), posfijo (postorden) y cuádruplos (postorden); un `Evaluador` con memoria calcula el valor. Corre solo si no hay errores léxicos, sintácticos ni semánticos.

**Tech Stack:** Java 17, JavaCC/JJTree (AST existente), Swing, JUnit 5, Maven (`mvn -o test`).

**Spec:** `docs/superpowers/specs/2026-10-13-codigo-intermedio-design.md`

## Global Constraints

- Java 17 (`maven.compiler.release=17`): sin `switch` con patrones; usar `instanceof` con patrón.
- Solo operaciones aritméticas: `+ - * / mod rem ** abs`, signo unario, paréntesis.
- No entrar a `if`, `while`, `for`; no evaluar llamadas a funciones.
- Comentarios y mensajes en español, estilo del código existente.
- Un único commit al final, sin línea de coautoría.

## Decisiones de implementación (derivadas del AST real)

- "Cuerpo principal" = el **último** `ASTProcedimiento` de nivel superior del `ASTPrograma`; funciones, paquetes y subprogramas anidados se ignoran.
- Los nodos de expresión que conserva el AST: `ASTSimple` (valor `VerificadorSemantico.SimpleOp`), `ASTTermino` (valor `List<String>` de operadores), `ASTFactor` (valor `VerificadorSemantico.FactorOp`), `ASTLiteral` (`LiteralAst`), `ASTNombre` (`NombreAst`). Un nodo solo existe si hubo operador; los paréntesis no generan nodo.
- Variables asignadas dentro de `if/while/for` quedan "sin valor (indeterminado)" después de la construcción, para no mostrar valores falsos.
- Tipo de una variable = `DeclaracionVarAst.tipo()` (Integer/rango → entero; Float → real; subtipo → su base). Cualquier otro identificador hace que la expresión se omita.

## Review Focus

- División entre cero, `mod`/`rem` con negativos, exponente negativo en Integer → error de fila, no excepción.
- Desbordamiento de Integer (32 bits) → error de fila.
- Variable sin inicializar usada en una expresión → fila con error, no se asume 0.
- Variable modificada dentro de un `if` y usada después → "indeterminado".
- Mezcla Integer/Float → error de tipos en la fila.

---

### Task 1: Modelo, árbol y notaciones

**Files:**
- Create: `src/main/java/com/compiladorada/intermedio/ArbolExpresion.java`, `Valor.java`, `Notaciones.java`
- Test: `src/test/java/com/compiladorada/intermedio/NotacionesTest.java`

**Interfaces:**
- Produces: `ArbolExpresion` (sealed: `Literal(String texto, Valor valor)`, `Variable(String nombre)`, `Unario(String op, ArbolExpresion operando)`, `Binario(String op, ArbolExpresion izq, ArbolExpresion der)`); `Valor.entero(long)`, `Valor.real(double)`, `esReal()`, `entero()`, `real()`, `toString()`; `Notaciones.prefijo(ArbolExpresion)`, `Notaciones.posfijo(ArbolExpresion)` → `String` con tokens separados por un espacio.

- [ ] Test: `A + B * 2` → prefijo `+ A * B 2`, posfijo `A B 2 * +`; `(A+B)*(C-D)` → `* + A B - C D` / `A B + C D - *`; unario `neg`/`abs` → `neg A` / `A neg`.
- [ ] Implementar los tres archivos; `mvn -o test -Dtest=NotacionesTest`.

### Task 2: Cuádruplos

**Files:**
- Create: `intermedio/Cuadruplo.java`, `intermedio/GeneradorCuadruplos.java`
- Test: `intermedio/GeneradorCuadruplosTest.java`

**Interfaces:**
- Produces: `record Cuadruplo(String op, String arg1, String arg2, String resultado)` con `toString()` = `(op, a1, a2, r)` (`_` si vacío); `GeneradorCuadruplos.generar(ArbolExpresion, List<String> destinos)` → `List<Cuadruplo>`; temporales `t1, t2…` reiniciados por expresión; por cada destino un cuádruplo final `(:=, lugar, _, destino)`; destinos vacío → sin cuádruplo final.

- [ ] Test: `X := A + B * 2` → `(*, B, 2, t1)`, `(+, A, t1, t2)`, `(:=, t2, _, X)`; `Y := 5` → `(:=, 5, _, Y)`; `-A` → `(neg, A, _, t1)`.
- [ ] Implementar; ejecutar test.

### Task 3: Evaluador

**Files:**
- Create: `intermedio/Evaluador.java`
- Test: `intermedio/EvaluadorTest.java`

**Interfaces:**
- Consumes: `ArbolExpresion`, `Valor`.
- Produces: `new Evaluador(Map<String,Valor> memoria, Map<String,String> sinValor)` (claves en minúsculas); `Valor evaluar(ArbolExpresion) throws Evaluador.ErrorEvaluacion` (mensaje en español).

- [ ] Tests: `7/2=3`, `-7/2=-3`, `-7 mod 3=2`, `-7 rem 3=-1`, `2**10=1024`, `2.0**3=8.0`, `1/0` error, `5 mod 0` error, `2**-1` Integer error, `2147483647+1` desbordamiento, `1+2.0` error de tipos, variable ausente → mensaje desde `sinValor`, variable sin entrada → "no inicializada".
- [ ] Implementar (long con `Math.*Exact`, `check32`, `Math.floorMod`, `%`); ejecutar test.

### Task 4: Constructor de árbol + generador sobre el AST

**Files:**
- Create: `intermedio/ConstructorArbol.java`, `FilaIntermedia.java`, `ValorFinal.java`, `ResultadoIntermedio.java`, `GeneradorIntermedio.java`
- Test: `intermedio/GeneradorIntermedioTest.java`

**Interfaces:**
- Consumes: tasks 1–3; `Compilador.analizar(fuente, nombre).ast()`.
- Produces: `GeneradorIntermedio.generar(SimpleNode raiz)` → `ResultadoIntermedio(List<FilaIntermedia> filas, List<ValorFinal> valoresFinales, List<String> metodos)` con `aTexto()`; `FilaIntermedia(int linea, String fuente, String tipo, String prefijo, String posfijo, List<Cuadruplo> cuadruplos, String valor, String error)`; `ValorFinal(String nombre, String tipo, String valor)` (`valor` null → sin valor, con `motivo`).

- [ ] Tests de integración (fuente Ada completa): memoria acumulativa (`A:=5; B:=A*2+1;` → B=11), constantes, Float, `Put_Line(A+B)` genera fila sin escribir memoria, expresión dentro de `if` ignorada, variable asignada en `if` → indeterminada, variable sin inicializar → error, programa con función solo cuenta el procedimiento principal.
- [ ] Implementar y ejecutar.

### Task 5: Integración con `Compilador` y archivo de salida

**Files:**
- Modify: `ResultadoCompilacion.java` (6.º componente `intermedio`, constructor de 5 args conservado, `intermedioOmitido()`), `Compilador.java`
- Create: `intermedio/EscritorIntermedio.java`
- Test: `CompiladorTest.java` (agregar casos), `intermedio/EscritorIntermedioTest.java`

- [ ] Tests: programa válido → `intermedio()` no nulo; con error semántico/sintáctico/léxico → `null` y `intermedioOmitido()`; el volcado crea `codigo_intermedio.txt` (con aviso si se omitió).
- [ ] Implementar; ejecutar suite completa.

### Task 6: Pestaña del IDE, ejemplo y documentación

**Files:**
- Create: `ide/PanelIntermedio.java`, `ejemplos/codigo_intermedio.ada`
- Modify: `ide/VentanaPrincipal.java` (pestañas inferiores "Errores" | "Código intermedio"; `compilar()` alimenta el panel y escribe el archivo; `getPanelIntermedio()`), `README.md`
- Test: `ide/VentanaPrincipalTest.java`, `ide/PanelesTest.java`

- [ ] Tests: tras `compilar()` de un programa válido el panel muestra N filas y existe `codigo_intermedio.txt`.
- [ ] Implementar; `mvn -o test` completo; compilar el ejemplo y revisar la salida; un único commit sin coautoría.
