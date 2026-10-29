# Optimización de código fuente Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Producir, tras compilar sin errores, una versión optimizada del fuente Ada (compactada, con plegado de constantes y reducción de potencia) y mostrarla junto al original en una pestaña nueva.

**Architecture:** Paquete nuevo `com.compiladorada.optimizacion`. Un recorrido del AST (con ámbitos propios) encuentra los subárboles aritméticos maximales con `ConstructorArbol`; cada uno se pliega y reduce como `ArbolExpresion`, se imprime como lista de lexemas con la estructura del árbol conservada, y se sustituye en la lista de tokens de `AnalizadorLexico` por rango de posiciones. El `Compactador` une todos los lexemas con espaciado y saltos de línea mínimos.

**Tech Stack:** Java 17, JavaCC/JJTree (AST existente), Swing + RSyntaxTextArea, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-10-28-optimizacion-design.md`

## Global Constraints

- Plegado: aritmética idéntica a `Evaluador` (Integer 32 bits, Float `double`); no se pliega si el `Evaluador` lanza `ErrorEvaluacion`.
- Reducción solo sobre identificadores de tipo exactamente `Integer` o `Float`: `X ** 2` → `X * X`; `X * 2` / `2 * X` → `X + X`; `X / 2.0` → `X * 0.5`.
- El texto regenerado debe volver a analizarse al mismo árbol (estructura conservada; paréntesis solo los necesarios).
- Espaciado mínimo; salto de línea tras `;` (fuera de paréntesis) y tras `is` (solo de subprograma/paquete), `begin`, `then`, `else`, `loop`, `declare`, `exception`, `record`, `private`, y `=>` de `when`; no tras `end loop`, `end record`, `or else`, `and then`.
- Sin resaltado de cambios ni notas de técnica. Solo corre si no hay errores. Salida `output/codigo_optimizado.ada`.
- Commit final único, sin línea `Co-Authored-By`.

## Review Focus

- Resultado plegado negativo en contexto aditivo/multiplicativo (`A - (0-3)`, `A * (0-3)`): debe quedar con paréntesis válidos.
- Raíz que baja de nivel (`B - A*2` → `B-(A+A)`; `A*2*B` → `(A+A)*B`).
- `abs` y `**` combinados (`abs A ** 2`), y `neg` como cabeza aditiva (`-A*2 + B`).
- Nombres sombreados con otro tipo en un subprograma interno: no se reducen.
- División entre cero y desbordamiento literales: se dejan intactos.
- Cadenas con espacios/`--` dentro y comentarios al final de línea: se conservan/eliminan correctamente.

---

### Task 1: Exponer la API de `intermedio`

**Files:**
- Modify: `src/main/java/com/compiladorada/intermedio/ConstructorArbol.java`

**Interfaces:**
- Produces: `public final class ConstructorArbol` con `public ConstructorArbol(Predicate<String>)` y `public ArbolExpresion construir(Node n)`.

- [ ] **Step 1:** Cambiar la clase, el constructor y `construir` a `public`.
- [ ] **Step 2:** `mvn -q compile` compila.

### Task 2: `Compactador`

**Files:**
- Create: `src/main/java/com/compiladorada/optimizacion/Compactador.java`
- Test: `src/test/java/com/compiladorada/optimizacion/CompactadorTest.java`

**Interfaces:**
- Produces: `public static String unir(List<String> lexemas)` (espaciado + saltos) y `public record Reemplazo(List<String> lexemas, long fin)`; `public static long clave(int linea, int columna)`; `public static String compactar(List<TokenLexico> tokens, Map<Long, Reemplazo> reemplazos)` (descarta `COMENTARIO`; en un token cuya clave está en `reemplazos` emite sus lexemas y salta hasta el token con clave `fin` inclusive).

- [ ] **Step 1:** Tests: `unir(["Total",":=","A","+","B","*","2",";"])` = `"Total:=A+B*2;\n"` (sin salto final sobrante: el texto termina sin `\n`); `procedure P is begin null; end P;` → líneas `procedure P is`, `begin`, `null;`, `end P;`; `x : Integer ;` → `x:Integer;`; `A - - 1` no se pega (`A- -1`); `end loop;`/`or else`/`and then` sin salto; `is` de `type T is (A,B);` sin salto; `;` dentro de paréntesis sin salto; `when others =>` salto tras `=>`; cadena `"a  --b"` intacta; `compactar` ignora comentarios y respeta `Reemplazo`.
- [ ] **Step 2:** Ejecutar y ver fallar.
- [ ] **Step 3:** Implementar. Espacio entre `a` y `b` si ambos terminan/empiezan con letra, dígito o `_`; o si el par `ultimoChar(a)+primerChar(b)` ∈ {`--`, `:=`, `=>`, `..`, `**`, `/=`, `>=`, `<=`, `<<`, `>>`, `<>`}; o `'`/`'` o `"`/`"`. Saltos según Global Constraints (comparando en minúsculas; profundidad de paréntesis; banderas `esperaIs` tras `procedure|function|package`, borrada en `is` o `;`, y `esperaFlecha` tras `when`).
- [ ] **Step 4:** Tests pasan.

### Task 3: `Impresor` (árbol → lexemas)

**Files:**
- Create: `src/main/java/com/compiladorada/optimizacion/Impresor.java`
- Test: `src/test/java/com/compiladorada/optimizacion/ImpresorTest.java`

**Interfaces:**
- Consumes: `ArbolExpresion` (`Literal(texto,valor)`, `Variable(nombre)`, `Unario(op: "neg"|"abs", operando)`, `Binario(op: + - * / mod rem **, izq, der)`).
- Produces: `public static List<String> imprimir(ArbolExpresion a, int requerido, boolean cabeza)`. Niveles: hoja 5; `abs` y `**` 4; `* / mod rem` 3; `+ -` y `neg` 2. Se parentiza si `nivel < requerido` o si es `neg` y `!cabeza`. Reglas: `+ -`: izq (2, cabeza heredada), der (3); `* / mod rem`: izq (3), der (4); `**`: ambos (5); `abs`: operando (5) (si no es hoja, entre paréntesis); `neg`: operando (3, sin cabeza).

- [ ] **Step 1:** Tests con árboles armados a mano y `Compactador.unir`: `A+(B+C)`, `(A+B)+C` → `A+B+C`, `(A+A)*B`, `A-(B+B)`, `-A*B+C`, `(-A)*B`, `A*(-3)`, `A+(-3)`, `X**(-1)`, `(abs A)**2`, `abs A`, `abs(A+B)`, `2**(3**2)`.
- [ ] **Step 2:** Ver fallar. **Step 3:** Implementar. **Step 4:** Pasar.
- [ ] **Step 5:** Test de propiedad (semilla fija, ~300 árboles aleatorios sobre `A`, `B` Integer y literales pequeños, ops `+ - * / mod rem **`, `abs`, `neg`): imprimir con `(0,true)`, insertar en `procedure P is A : Integer := 1; B : Integer := 2; X : Integer; begin X := <texto>; end P;`, compilar, y comprobar `Notaciones.prefijo` del árbol reconstruido (primera fila del intermedio con operadores) = `Notaciones.prefijo` del árbol original; árboles de una sola hoja se omiten.

### Task 4: `PlegadorConstantes` y `ReductorPotencia`

**Files:**
- Create: `src/main/java/com/compiladorada/optimizacion/PlegadorConstantes.java`, `src/main/java/com/compiladorada/optimizacion/ReductorPotencia.java`
- Test: `src/test/java/com/compiladorada/optimizacion/TransformacionesTest.java`

**Interfaces:**
- Produces: `static ArbolExpresion PlegadorConstantes.plegar(ArbolExpresion a)`; `static ArbolExpresion ReductorPotencia.reducir(ArbolExpresion a, Function<String, ClaseNumerica> clase)` con `enum ClaseNumerica { ENTERO, REAL, OTRA }` dentro de `ReductorPotencia` (paquete privado). Ambos devuelven la misma instancia si nada cambia.

- [ ] **Step 1:** Tests: plegar `2**5+3` → literal `35`; `7/2` → `3`; `7 mod 3`, `-7 rem 2`; `2.5*2.0` → `5.0`; `abs(-4)` → `4`; `0-3` → `neg(3)`; `A+2*3` → `A+6`; sin cambios (misma instancia): `1/0`, `2147483647+1`, `2**(-1)`, `A+B`, `neg(3)`. Reducir: `A**2`→`A*A`, `A*2`, `2*A`, `F*2.0`→`F+F`, `F/2.0`→`F*0.5`; no cambian: `R*2` (OTRA), `A*3`, `A**3`, `A/2` (Integer), `(A+B)*2`, `A*2.0` con A Integer (no es el caso válido).
- [ ] **Step 2:** Ver fallar. **Step 3:** Implementar (plegado bottom-up con `Evaluador(Map.of(), Map.of())` solo si todas las hojas son `Literal`; el resultado negativo (o `-0.0`) se escribe `Unario("neg", Literal(magnitud))`; real con `Double.toString`; un `Unario("neg", Literal)` no se vuelve a plegar). **Step 4:** Pasar.

### Task 5: `Optimizador` + integración en `Compilador`

**Files:**
- Create: `src/main/java/com/compiladorada/optimizacion/Optimizador.java`, `src/main/java/com/compiladorada/optimizacion/ResultadoOptimizacion.java`
- Modify: `src/main/java/com/compiladorada/ResultadoCompilacion.java`, `src/main/java/com/compiladorada/Compilador.java`
- Test: `src/test/java/com/compiladorada/optimizacion/OptimizadorTest.java`

**Interfaces:**
- Produces: `public record ResultadoOptimizacion(String original, String optimizado)`; `public static ResultadoOptimizacion Optimizador.optimizar(String fuente, List<TokenLexico> tokens, SimpleNode ast)`. `ResultadoCompilacion` pasa a 7 campos (`..., ResultadoIntermedio intermedio, ResultadoOptimizacion optimizacion`) conservando los constructores de 5 y 6 argumentos, y añade `optimizacionOmitido()` (= `tieneErrores()`).
- Consumes: `Compactador`, `Impresor`, `PlegadorConstantes`, `ReductorPotencia`, `ConstructorArbol`.

- [ ] **Step 1:** Tests con `Compilador.analizar(...).optimizacion().optimizado()`: ejemplo mínimo con comentarios/espacios → texto exacto esperado; `X := A*2*B;` → `X:=(A+A)*B;`; `Total := 2 ** 5 + A * 2;` → `Total:=32+(A+A);`; `if X > 2*3 then` → `if X>6 then`; `C : constant Integer := 3 * 4;` → `C:constant Integer:=12;`; índice `Salon(K*2)`; variable de tipo `range` (`G*2`) intacta; sombreado en subprograma interno; con error semántico `optimizacion()` es `null`.
- [ ] **Step 2:** Ver fallar. **Step 3:** Implementar: ámbitos `Deque<Map<String, ClaseNumerica>>` (programa, procedimiento, función, paquete, `for`; `ASTParametro` → `ParametroInfo`; `ASTDeclaracionVar` declara tras visitar su inicializador; nombres de subprograma como `OTRA`); recorrido que en `ASTSimple/ASTTermino/ASTFactor` intenta `construir`; si es `Binario`/`Unario`, `reducir(plegar(a))`, y si `!equals` guarda un `Reemplazo` con `imprimir` usando el contexto del padre (Simple idx0 sin signo → (2,true), otro Simple → (3,false); Termino idx0 → (3,false), resto → (4,false); Factor → (5,false); entre `(` y `)` o cualquier otro padre → (0,true)); si no hay árbol aritmético baja a los hijos. Claves de posición con `Compactador.clave`; ubicar `(`/`)` vecinos por la lista de tokens sin comentarios.
- [ ] **Step 4:** Pasar.
- [ ] **Step 5:** Propiedades sobre todos los `ejemplos/*.ada` sin errores: el optimizado compila sin errores; optimizarlo de nuevo da el mismo texto; los `valoresFinales` del intermedio coinciden antes y después.

### Task 6: Salida y pestaña del IDE

**Files:**
- Create: `src/main/java/com/compiladorada/optimizacion/EscritorOptimizacion.java`, `src/main/java/com/compiladorada/ide/PanelOptimizado.java`
- Modify: `src/main/java/com/compiladorada/ide/VentanaPrincipal.java`
- Test: `src/test/java/com/compiladorada/ide/PanelesTest.java` (añadir), `src/test/java/com/compiladorada/ide/VentanaPrincipalTest.java` (añadir)

**Interfaces:**
- Produces: `EscritorOptimizacion.ARCHIVO = "codigo_optimizado.ada"`, `volcar(ResultadoCompilacion, Path)` (si omitido escribe `Optimización OMITIDA: ...`); `PanelOptimizado.setResultado(ResultadoOptimizacion)` (`null` = omitido), `getOriginal()`, `getOptimizado()`; `VentanaPrincipal.getPanelOptimizado()`.

- [ ] **Step 1:** Tests: tras `compilar()` con `A * 2` el panel muestra original y `A+A` en optimizado y existe `codigo_optimizado.ada`; con error, panel con aviso y archivo que empieza con `Optimización OMITIDA`.
- [ ] **Step 2:** Ver fallar. **Step 3:** Implementar `PanelOptimizado` con dos `RSyntaxTextArea` de solo lectura en `JSplitPane` horizontal (mismo `text/ada`, números de línea) y añadir la pestaña "Optimizado" y el volcado en `compilar()`. **Step 4:** Pasar.

### Task 7: Ejemplo, README y commit final

**Files:**
- Create: `ejemplos/optimizacion.ada`
- Modify: `README.md`

- [ ] **Step 1:** Escribir el ejemplo (comentarios, espacios, `**`, `* 2`, `/ 2.0`, división entre cero literal sin plegar) y verificar que sus propiedades pasan (añadir a la lista de ejemplos de Task 5).
- [ ] **Step 2:** README: estado y estructura (`optimizacion/`).
- [ ] **Step 3:** `mvn -q test` completo en verde.
- [ ] **Step 4:** Un único commit con plan, código, pruebas, ejemplo y README; **sin** línea `Co-Authored-By`.
