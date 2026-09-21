# Análisis semántico (Unidad 1) — diseño

## Contexto

El proyecto concluyó las etapas léxica y sintáctica (JavaCC/JJTree, `Ada.jjt`).
La materia exige "al menos dos implementaciones" del análisis semántico. Se
confirmó con el usuario que esto significa **dos estrategias de disparo de los
mismos chequeos semánticos** sobre el mismo subconjunto de Ada, no dos
catálogos de errores distintos ni dos estructuras de tabla de símbolos
independientes:

- **Implementación A** — acciones semánticas embebidas en las producciones de
  `Ada.jjt`, evaluadas durante el parseo (una sola pasada, síntesis de
  atributos al estilo gramática de atributos). Es la dirección que ya
  anticipa `docs/CLAUDE.md`.
- **Implementación B** — uno o varios `AdaParserVisitor` que recorren el AST
  ya construido, en **dos pasadas**: una recolecta declaraciones, otra
  verifica usos. Es la dirección que ya anticipaba `docs/unidad-0-diseno-ide.md`
  §9.2.

Ambas comparten el mismo núcleo (tabla de símbolos, modelo de tipos, reglas de
verificación) y solo difieren en cómo y cuándo se disparan esas reglas — así
la comparación entre ambas es justa (deben reportar exactamente los mismos
errores para el mismo programa) y no se duplica la lógica de tipos de Ada.

**Fuera de alcance de este documento**: el catálogo de errores E1–E15
mencionado antes en `docs/CLAUDE.md` y `docs/unidad-0-diseno-ide.md`
pertenece a otro entregable de la materia (evaluar el comportamiento
semántico de Ada real frente a una lista de errores dada) y no a este
proyecto. Las referencias a ese catálogo se eliminan de ambos documentos como
parte de este trabajo.

## Regla base

Todo identificador debe estar declarado al menos una vez, con un solo tipo de
dato, antes de cualquier uso en el mismo ámbito o uno anidado. Ada es
fuertemente tipado: no hay conversión implícita entre tipos con nombre
distinto (los literales universales sí se adaptan al tipo de contexto).

## 1. Integración con el pipeline existente

- Nuevo paquete `com.compiladorada.semantico`, paralelo a `lexico` y
  `sintactico`.
- `ErrorCompilacion.Categoria` gana el valor `SEMANTICO`.
- `ResultadoCompilacion` gana `erroresSemanticos()` (y, para el driver que se
  use en el IDE, acceso a la tabla de símbolos resultante).
- `Compilador.analizar()` sigue secuencial: el análisis semántico solo corre
  si no hay errores léxicos **ni** sintácticos, siguiendo el mismo patrón que
  ya rige la transición léxico → sintáctico.
- `Compilador` expone una forma de elegir qué driver corre (A o B) — necesario
  para la comparación pedida y para que las pruebas corran ambos contra el
  mismo caso.

## 2. Núcleo compartido

### `TipoAda`
Jerarquía cerrada que representa las categorías de tipo del subconjunto:

- Escalares predefinidos: `INTEGER`, `FLOAT`, `BOOLEAN`, `CHARACTER`
  (instancias únicas/singleton).
- `TipoString` — arreglo de longitud fija de `Character` (predefinido).
- `TipoRango` — entero con límites (`type X is range A..B`).
- `TipoEnumerado` — lista ordenada de literales (`type X is (A, B, C)`).
- `TipoArreglo` — rango de índice + tipo de componente.
- `TipoRegistro` — mapa ordenado de nombre de campo → `TipoAda`.
- `TipoSubtipo` — tipo base + rango opcional más estrecho
  (`subtype X is T range A..B`).

La compatibilidad de tipos se decide por identidad/regla explícita de
compatibilidad, nunca por coerción numérica implícita.

### `Simbolo`
Nombre (comparado sin distinguir mayúsculas/minúsculas, igual que
`IGNORE_CASE` en la gramática y las reglas de Ada), categoría (variable,
constante, tipo, parámetro, subprograma, paquete), `TipoAda`, mutabilidad,
posición de declaración (línea/columna — reutilizada en mensajes de error y
para la regla de declarado-antes-de-usar de B).

### `TablaSimbolos`
Pila de `Ambito` (cada uno un mapa nombre→`Simbolo`), con un ámbito por cada
`procedure`/`function`/`package` (coincide con dónde la gramática ya abre y
cierra esas construcciones). El ámbito más externo se preseeda con los
nombres predefinidos de `Standard`: `Integer`, `Float`, `Boolean`,
`Character`, `String`, `True`, `False`, `Constraint_Error`.

- `entrarAmbito()` / `salirAmbito()`.
- `declarar(Simbolo)` — error si el nombre ya existe en el ámbito **actual**
  (el shadowing entre ámbitos anidados es válido en Ada).
- `resolver(nombre)` — busca hacia afuera por la pila de ámbitos.

### `VerificadorSemantico`
Motor de reglas, con una `TablaSimbolos` y la lista de errores semánticos
acumulados. Expone métodos de intención que ninguno de los dos drivers
reimplementa:

- `declararVariables(nombres, tipo, esConstante, posicion)`
- `tipoDeUso(nombre, posicion)` → `TipoAda` (o reporta "no declarado" y
  devuelve un tipo centinela `DESCONOCIDO` para no propagar errores en
  cascada)
- `verificarAsignacion(tipoDestino, tipoOrigen, posicion)`
- `verificarLlamada(nombreSubprograma, tiposArgs, posicion)`
- `entrarAmbito()` / `salirAmbito()` (delegando a `TablaSimbolos`)

Ambos drivers llaman **solo** a estos métodos; ninguno reimplementa una regla
de compatibilidad de tipos por su cuenta.

## 3. Implementación A — acciones embebidas en `Ada.jjt`

Disparo directo durante el parseo, sin nodos de AST para expresiones:

- `AdaParser` recibe un campo `VerificadorSemantico`, inyectado igual que ya
  se inyecta la lista `erroresSintacticos`.
- Entrar/salir de ámbito se añade como acción Java al inicio/fin de
  `procedimiento()`, `funcion()`, `paquete()`.
- Producciones como `declaracionVar()` y `declaracionTipo()` llaman a
  `verificador.declararVariables(...)` justo después de parsear los tokens
  relevantes.
- **Cambio de gramática necesario** (compartido con B, ver más abajo):
  `expresion()`, `relacion()`, `simple()`, `termino()`, `factor()`,
  `primario()`, `nombre()` son hoy `void`. Para A deben convertirse en
  producciones con **atributos sintetizados**: cada una retorna un `TipoAda`
  calculado a partir de sus hijos (p. ej. `termino()` combina los tipos de
  sus `factor()` según la regla del operador multiplicativo), hasta que
  `expresion()` entrega el tipo final a `verificarAsignacion` /
  `verificarLlamada` / etc. JavaCC ya soporta producciones con tipo de
  retorno no-void; no se necesitan nodos de AST para A.
- Recuperación de errores: reutiliza el mecanismo `sincronizar(...)` que ya
  existe en los bloques `catch`. Un error semántico detectado a mitad de una
  producción no aborta el parseo: se reporta y la producción sigue
  devolviendo un tipo de mejor esfuerzo (el tipo declarado, o
  `TipoAda.DESCONOCIDO`) para no encadenar errores — mismo principio que la
  deduplicación "fantasma" ya usada para errores sintácticos.

## 4. Implementación B — visitor de dos pasadas sobre el AST

Requiere dos cambios estructurales previos (compartidos con A donde aplica):

- Las producciones de expresión necesitan nodos de AST reales (`#Expresion`,
  `#Nombre`, etc.) — mismo cambio de gramática que A necesita para sus
  atributos, aquí capturado como nodos de árbol en vez de valores de retorno.
- Las clases de nodo con nombre (`Procedimiento`, `DeclaracionVar`, etc.),
  hoy subclases genéricas de `SimpleNode`, se vuelven clases tipadas (vía
  `NODE_CLASS`) para que el visitor extraiga identificadores/tipos sin
  volver a parsear el texto del token.

Dos pasadas, ambas recorriendo el árbol con la **misma coreografía** de
entrar/salir de `Ambito` (en los mismos puntos donde A entra/sale durante el
parseo):

- **Pasada 1 (`RecolectorDeclaraciones`)**: recorre todo el árbol una vez,
  registra cada símbolo declarado (nombre, `TipoAda`, posición de
  declaración) en el ámbito correspondiente. No verifica usos todavía.
- **Pasada 2 (`VerificadorUsos`)**: vuelve a recorrer el mismo árbol con la
  misma coreografía de ámbitos, resuelve cada uso de nombre y cada expresión
  (tipo calculado de abajo hacia arriba vía el valor de retorno del visitor),
  llamando a los mismos métodos de `VerificadorSemantico` que A.

**Regla de declarado-antes-de-usar en dos pasadas**: como la pasada 1
registra todas las declaraciones de un ámbito antes de que la pasada 2
verifique ningún uso, una resolución "hacia adelante" sería posible si no se
corrige — violaría la regla real de Ada (declarado-antes-de-usar, sin
declaraciones adelantadas de subprogramas ni recursión mutua en este
subconjunto). Por eso la pasada 2 **descarta** una resolución cuya posición
de declaración esté después de la posición de uso en el mismo ámbito. Esto
mantiene a A y B reportando exactamente los mismos errores para el mismo
programa, pese a resolver en dos barridos distintos.

## 5. Estrategia de pruebas

Reutiliza el patrón existente `.ada` + `.expected` en
`src/test/resources/casos/{validos,invalidos}`. Cada caso nuevo se corre
contra **ambos** drivers con el mismo archivo `.expected` — esa doble
ejecución es en sí la comparación que pide la materia, y una discrepancia
entre A y B sobre la misma entrada es señal de bug durante el desarrollo, no
solo al final.

Casos a cubrir (derivados de la regla base y el subconjunto de tipos
soportado, ver `docs/CLAUDE.md`):

- Identificador no declarado.
- Redeclaración en el mismo ámbito.
- Incompatibilidad de tipos en asignación/expresión.
- Aridad o tipos de parámetro incorrectos en una llamada.
- Shadowing entre ámbitos anidados (válido) vs. fuga de ámbito (inválido).
- Chequeos de tipo sobre rango, enumerado, arreglo y registro.
- Intento de escritura sobre una `constant`.

## 6. Limpieza de referencias a E1–E15

Se eliminan como parte de este trabajo (pertenecen a otro entregable):

- `docs/CLAUDE.md`, línea 20 (mención de la batería E1–E15).
- `docs/unidad-0-diseno-ide.md`, §9.2 (mención de la batería E1–E15 y el
  mecanismo de prueba asociado).

## Siguientes pasos

Este documento cubre el diseño; la implementación se planifica por separado
(vía `writing-plans`) una vez aprobado este spec.
