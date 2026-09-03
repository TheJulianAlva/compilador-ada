# Diseño — Front-end léxico-sintáctico e IDE (Unidad 0)

Fecha: 2026-09-02
Entregable asociado: "Analizador léxico y sintáctico (IDE)" — vence 2026-09-03.
Estado: aprobado para implementación.

## 1. Objetivo y alcance

Construir el front-end del compilador de Ada (subconjunto definido en `docs/CLAUDE.md`,
sección "Subconjunto de Ada soportado") y un IDE Swing que lo opera. El front-end
produce, en una sola pasada de parseo, una tabla de tokens, listas separadas de
errores léxicos y sintácticos (reportando *todos* los errores, no solo el primero) y
un AST (JJTree) que servirá de base a las Unidades 1–4.

Cubierto por este diseño:

- Editor de código con abrir/guardar y resaltado de sintaxis de Ada.
- Indicador en tiempo real de la posición del cursor (línea y columna).
- Etapa léxica: tabla de tokens (columnas token | tipo).
- Etapa sintáctica: paneles separados de errores léxicos y sintácticos, con
  ubicación (línea:columna), explicación en español y todos los errores de la pasada.
- Persistencia de tokens y errores a archivos en `output/`, sobrescritos en cada
  compilación.
- Gramática JJTree que cubre el subconjunto de Ada del proyecto y genera el AST.

Fuera de alcance (unidades posteriores): análisis semántico, tabla de símbolos,
código intermedio, optimización, código objeto, empaquetado con Launch4j.

## 2. Contexto: modelo de compilación

Ada no se compila en una sola pasada. GNAT hace scanner -> parser (AST) -> `Sem`
(semántico, varios recorridos) -> `Expander` -> `gigi` (a GENERIC de GCC) ->
middle-end de GCC -> back-end, precedido de un análisis de dependencias entre
unidades a partir de las cláusulas `with`.

Este proyecto replica ese pipeline de forma simplificada:

- Léxico + sintáctico: **una sola pasada** de parseo (JavaCC genera un parser
  descendente recursivo LL(k) que construye el AST sobre la marcha). La
  recuperación en modo pánico no lo convierte en multipasada: salta tokens tras
  un fallo en vez de abortar.
- Para la tabla de tokens del IDE se hace además un recorrido léxico dedicado
  previo (se necesitan todos los tokens, incluidos comentarios y los de zonas con
  error, aunque el parseo falle). Ambos recorridos son triviales en costo.
- Semántico (Unidad 1) y siguientes: pasadas sucesivas sobre el AST ya construido.
- Simplificación principal respecto a GNAT: una sola unidad de compilación por
  archivo, sin `separate` real, por lo que no hay pasada de análisis de
  dependencias entre unidades.

## 3. Arquitectura de módulos

Separación estricta entre el motor de compilación y el IDE. El IDE solo habla con
la fachada `Compilador`; nunca toca las clases generadas por JavaCC. Esto permite
que las Unidades 1–4 se construyan sobre el mismo núcleo.

```
com.compiladorada
├── Compilador                 FACHADA. analizar(String fuente, String nombreArchivo)
│                              -> ResultadoCompilacion. Nunca lanza excepción.
│
├── lexico/
│   ├── Ada.jjt                (en src/main/javacc/) tokens + gramática + anotaciones JJTree
│   ├── TipoToken              enum: IDENTIFICADOR, PALABRA_RESERVADA, ENTERO, REAL,
│   │                          CARACTER, CADENA, DELIM_SIMPLE, DELIM_COMPUESTO,
│   │                          COMENTARIO, ERROR
│   ├── TokenLexico            { String lexema, TipoToken tipo, int linea, int columna }
│   └── PalabrasReservadas     Set<String> con las 73 palabras reservadas de Ada 2012;
│                              lookup case-insensitive. Solo para clasificar en la tabla.
│
├── sintactico/
│   ├── (parser + TokenManager generados por JavaCC/JJTree)
│   └── nodos/                 SimpleNode + subclases concretas por producción relevante
│
├── errores/
│   ├── ErrorCompilacion       { Categoria categoria (LEXICO|SINTACTICO), int linea,
│   │                            int columna, String mensaje }
│   └── EscritorErrores        vuelca List<ErrorCompilacion> y List<TokenLexico> a output/
│
├── ResultadoCompilacion       { List<TokenLexico> tokens,
│                                List<ErrorCompilacion> erroresLexicos,
│                                List<ErrorCompilacion> erroresSintacticos,
│                                NodoPrograma ast /* nullable */ }
│
└── ide/
    ├── Main                   punto de entrada; construye VentanaPrincipal en el EDT
    ├── VentanaPrincipal       layout, JMenuBar, orquesta el flujo "Compilar"
    ├── EditorPanel            RSyntaxTextArea + abrir/guardar + estado de modificación
    ├── BarraEstado            CaretListener -> "Ln x, Col y" en vivo + resultado compilación
    ├── TablaTokensPanel       JTable no editable (panel derecho)
    └── PanelErrores           JTabbedPane: pestaña léxicos / pestaña sintácticos (abajo)
```

Flujo de datos, un solo sentido y sin estado compartido mutable:
`EditorPanel` da el texto -> `VentanaPrincipal` llama `Compilador.analizar(...)` ->
`ResultadoCompilacion` -> se reparte a `TablaTokensPanel` y `PanelErrores`, y
`EscritorErrores` vuelca a disco.

## 4. Gramática `src/main/javacc/Ada.jjt`

### 4.1 Configuración

- `IGNORE_CASE = true` global (Ada es case-insensitive).
- JJTree con `NODE_DEFAULT_VOID` (solo generan nodo las producciones anotadas con
  `#Nombre`).
- El plugin Maven usa el goal `jjtree-javacc`; salida a `target/generated-sources/`.

### 4.2 Léxico

- Literales según la tabla de ER de `docs/CLAUDE.md`: entero decimal, real, entero/real
  con base explícita, literal de carácter, literal de cadena.
- Identificador: `[A-Za-z][A-Za-z0-9]*(_[A-Za-z0-9]+)*` — no termina en `_`, no
  contiene `__`.
- Comentario `--[^\n]*`: se define como `SPECIAL_TOKEN` (el parser lo ignora) pero el
  recorrido léxico previo lo recoge para la tabla de tokens.
- Whitespace `[ \t\r\n]+`: descartado.
- Delimitadores simples y compuestos según la tabla de ER.
- Palabras reservadas: se declaran como tokens JavaCC (necesario para escribir
  `"procedure"` etc. en las producciones); el orden de JavaCC las distingue del
  identificador. `PalabrasReservadas` se usa solo para etiquetar la columna "tipo"
  de la tabla de tokens.

### 4.3 Sintáctico

Producciones del fragmento BNF de `docs/CLAUDE.md` más lo que añade el subconjunto:

- `subtype <id> is <tipo> [ range <expr> .. <expr> ] ;`
- pertenencia `<simple> [ not ] in <rango>` dentro de `<relacion>`
- modos de parámetro `in` / `out` / `in out`
- bloque `declare / begin / exception / when ... => / end` y `raise [ <id> ] ;`
- `array ( <rango> ) of <tipo>`, `record { <componente> } end record`, enumerados
  `( <id> { , <id> } )`

### 4.4 Nodos JJTree

Anotadas con `#Nombre` y con clase concreta (guardan token de inicio con línea/columna):
`Programa`, `Procedimiento`, `Funcion`, `Paquete`, `DeclaracionVar`,
`DeclaracionTipo`, `Parametro`, `Asignacion`, `If`, `For`, `While`, `LlamadaProc`,
`ExpresionBinaria`, `ExpresionUnaria`, `Literal`, `Referencia`.
El resto de producciones no genera nodo (o queda como `SimpleNode` si se necesita
estructura intermedia).

### 4.5 Ambigüedades y LOOKAHEAD

- Inicio de sentencia: `<id> :=` (asignación) vs `<id> ( ... ) ;` o `<id> ;`
  (llamada a procedimiento) -> `LOOKAHEAD` local sobre el token tras el identificador.
- `package body <id>` vs `package <id> is` (spec) -> `LOOKAHEAD(2)`.
- Cierres: `end ;` / `end if ;` / `end loop ;` / `end record` / `end <id> ;` ->
  decisión por el token siguiente a `end`.
- `type <id> is ( ... )` (enumerado) vs otras `<definicion_tipo>` -> por el token
  tras `is`.

## 5. Recuperación de errores

Objetivo: una compilación produce todas las listas de errores de una pasada y
`Compilador.analizar` nunca propaga una excepción al IDE.

### 5.1 Errores léxicos

- Token catch-all `<ERROR_LEXICO: ~[]>` como última definición: captura cualquier
  carácter que ningún otro token aceptó. Una acción registra
  `ErrorCompilacion(LEXICO, linea, columna, "carácter no válido '<c>'")` en una lista
  acumuladora del `TokenManager` y descarta el carácter. El carácter sí aparece en
  la tabla de tokens con `TipoToken.ERROR`.
- Casos adicionales detectados con tokens "trampa" que matchean el patrón inválido,
  registran el error y continúan:
  - cadena sin cerrar antes de fin de línea,
  - literal de carácter mal formado,
  - identificador que termina en `_` o contiene `__`.

### 5.2 Errores sintácticos: modo pánico con sincronización

`try/catch (ParseException e)` alrededor de las producciones de nivel:

- `declaracion()` — sincroniza hasta `;` (consumiéndolo) o hasta `begin` / `end`
  (sin consumir).
- `sentencia()` — sincroniza hasta `;`, o hasta
  `end` / `elsif` / `else` / `exception` / `when` (sin consumir).
- `unidadCompilacion()` — sincroniza hasta el siguiente
  `procedure` / `function` / `package` a nivel 0, o EOF.

El `catch` ejecuta `errores.add(traducir(e)); error_skipto(TOKENS_SYNC);`.
`error_skipto` consume tokens hasta ver uno del conjunto de sincronización, contando
anidamiento de `(` `)` para no salir de más.

### 5.3 Traducción de mensajes

`ParseException` ("Encountered X, expected one of {...}") se reescribe a español:
token encontrado (lexema real), tokens esperados en notación amigable (`';'`,
`'then'`, `un identificador`), línea y columna del token ofensor. Tabla
`Map<imagen_token, descripción>` para los nombres legibles.

### 5.4 Errores fantasma y límites

- Heurística "un error por punto de fallo": tras registrar un error sintáctico, si
  el siguiente cae a <= 1 token de distancia y en la misma línea, se omite.
- Límite de seguridad: si se acumulan > 200 errores, o el parser no avanza en 2
  iteraciones de recuperación, se corta con un error final "demasiados errores,
  análisis interrumpido".

## 6. Salida a archivos (`output/`)

Carpeta fija `output/` en la raíz del proyecto (configurable más adelante).
Se sobrescriben completos en cada compilación:

- `output/tokens.txt`
- `output/errores_lexicos.txt`
- `output/errores_sintacticos.txt`
- `output/fuente_sin_guardar.ada` — solo si el editor no tiene archivo guardado;
  el IDE muestra la ruta exacta en la barra de estado y en un diálogo la primera vez.

Formato de los archivos de errores: texto plano, una línea por error, estilo GCC/GNAT:

```
<archivo>:<linea>:<columna>: <categoria>: <mensaje>
```

Ejemplo:

```
mi_programa.ada:12:8: error léxico: carácter no válido '$'
mi_programa.ada:20:1: error sintáctico: se esperaba ';' antes de 'end'
```

Cada archivo lleva una cabecera con fecha/hora de la compilación y el conteo total
de entradas. `output/tokens.txt` tiene columnas alineadas: lexema, tipo, línea, columna.

Se descarta CSV/JSON salvo que la asignatura lo exija; en ese caso sería un segundo
writer sobre el mismo modelo de datos.

## 7. IDE Swing

### 7.1 Menú único (`JMenuBar`), sin toolbar

- **Archivo** — Nuevo · Abrir… · Guardar · Guardar como… · —— · Salir
  (confirmación si hay cambios sin guardar)
- **Editar** — Deshacer · Rehacer · —— · Cortar · Copiar · Pegar · —— · Buscar…
- **Compilar** — Compilar (F5) · —— · Abrir carpeta `output/`
- **Ver** — Mostrar/ocultar panel de tokens · Mostrar/ocultar panel de errores
- **Ayuda** — Acerca de…

`Compilar` lleva acelerador `F5` activo sin abrir el menú.
`JFileChooser` filtrado a `*.ada` / `*.adb` / `*.ads`.

### 7.2 Layout

`BorderLayout` con `JSplitPane` anidados:

```
┌─────────────────────────────────────────────────────────┐
│  Archivo   Editar   Compilar   Ver   Ayuda      (JMenuBar)│
├───────────────────────────────┬─────────────────────────┤
│                               │  Análisis léxico        │
│   Editor                      │  ┌───────────────────┐  │
│   (RSyntaxTextArea,           │  │ Token │ Tipo      │  │
│    resaltado Ada, scroll,     │  │ ...   │ ...       │  │
│    gutter de nº de línea)     │  └───────────────────┘  │
│                               │  (JTable, panel derecho)│
├───────────────────────────────┴─────────────────────────┤
│  Errores  [ Léxicos (n) | Sintácticos (m) ]  (JTabbedPane)│
│  ┌────────────────────────────────────────────────────┐ │
│  │ Ln:Col │ Mensaje                          (JTable)  │ │
│  └────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│  Ln 12, Col 8        output/ · archivo: mi_programa.ada  │  BarraEstado
└─────────────────────────────────────────────────────────┘
```

`JSplitPane` vertical exterior (editor + tabla arriba / errores abajo);
`JSplitPane` horizontal interior (editor / tabla de tokens).

### 7.3 Componentes

- `EditorPanel` — `RSyntaxTextArea` dentro de `RTextScrollPane` (activa el gutter de
  números de línea). No hay estilo Ada de fábrica: se registra un `TokenMaker` propio
  mínimo (palabras reservadas + comentarios + cadenas + números). Expone `abrir()`,
  `guardar()`, `guardarComo()`, `getTexto()`, `getNombreArchivo()`, `isModificado()`.
- `BarraEstado` — `CaretListener` sobre el editor -> `Ln x, Col y` actualizado en
  cada movimiento del cursor (no solo al compilar). Muestra también el resultado de
  la última compilación.
- `TablaTokensPanel` — `JTable` no editable, `AbstractTableModel` sobre
  `List<TokenLexico>`.
- `PanelErrores` — `JTabbedPane` con un `JTable` por pestaña; el título muestra el
  conteo. Doble clic en una fila -> mueve el cursor del editor a esa línea/columna.

### 7.4 Flujo "Compilar" (menú Compilar o F5)

1. `VentanaPrincipal` toma `editor.getTexto()` y el nombre
   (o `fuente_sin_guardar.ada`).
2. `ResultadoCompilacion r = Compilador.analizar(texto, nombre)` en un `SwingWorker`
   (no congelar el EDT, aunque sea rápido).
3. Al terminar, en el EDT: `tablaTokens.setDatos(r.tokens)`,
   `panelErrores.setDatos(r.erroresLexicos, r.erroresSintacticos)`, refresca títulos.
4. `EscritorErrores.volcar(r, dirOutput)` sobrescribe los tres archivos.
5. `BarraEstado` muestra "Compilado: n léxicos, m sintácticos — output/ actualizado".

## 8. Pruebas

JUnit 5 sobre la fachada `Compilador`. El IDE no se prueba automáticamente.

- `src/test/resources/casos/validos/` — ~8 `.ada` que deben dar 0 errores:
  procedimiento mínimo, función con parámetros, paquete spec + body, todos los tipos
  definidos por el usuario, if/elsif/else anidado, for reverse + while, expresiones
  con toda la precedencia, bloque con `exception` / `raise`.
- `src/test/resources/casos/invalidos/` — ~10 `.ada`, cada uno con un archivo
  `.expected` al lado (lista de `linea:columna:categoria` esperados). Cubren:
  carácter ilegal, cadena sin cerrar, identificador con `__`, `;` faltante, `end` sin
  `if`, paréntesis desbalanceado, `then` faltante, y al menos 2 casos con múltiples
  errores en distintas líneas para verificar la recuperación.
- Test de robustez: `analizar` nunca lanza excepción, con entrada basura aleatoria.
- Test de `EscritorErrores`: formato exacto de línea, cabecera, sobrescritura.

## 9. Orden de implementación (por riesgo de tiempo)

| # | Tarea | Riesgo | Nota |
|---|---|---|---|
| 1 | `pom.xml` -> goal `jjtree-javacc`; `Ada.jjt` solo con tokens + `main` que los lista | medio | valida la cadena de build JJTree -> JavaCC -> compile |
| 2 | Modelo: `TokenLexico`, `TipoToken`, `ErrorCompilacion`, `ResultadoCompilacion` | bajo | POJOs |
| 3 | Gramática sintáctica completa del subconjunto, sin recuperación | alto | grueso del tiempo; ambigüedades LOOKAHEAD |
| 4 | Recuperación de errores (léxica + pánico sintáctico) | alto | depende de 3 |
| 5 | Fachada `Compilador.analizar` + `EscritorErrores` | bajo | integra todo |
| 6 | Tests con los casos válidos/inválidos | medio | descubre huecos de la gramática |
| 7 | IDE: layout + editor + abrir/guardar + barra de estado | medio | mecánico pero voluminoso |
| 8 | IDE: tabla de tokens + panel de errores + flujo Compilar | medio | |
| 9 | `TokenMaker` de resaltado Ada | bajo | recortable |
| 10 | Documento de diseño de la unidad (`docs/unidad-0-diseno-ide.md`) | medio | 30% de la nota de la unidad |

Recorte de emergencia si falta tiempo: #9 (resaltado -> `SYNTAX_STYLE_NONE`); reducir
el subconjunto sintáctico dejando records / enumerados / excepciones al final; el AST
JJTree puede quedar con nodos `SimpleNode` genéricos y refinarse en la Unidad 1.

## 10. Riesgos

- **Tiempo.** El checklist completo en un día es agresivo. Mitigación: orden por
  riesgo de la sección 9 y plan de recorte.
- **Ambigüedades LL(k) de la gramática.** Ada tiene varias construcciones que
  arrancan con identificador. Mitigación: `LOOKAHEAD` local documentado en la
  sección 4.5; si alguna resiste, `LOOKAHEAD` sintáctico (más caro) como último
  recurso.
- **Ruido del modo pánico.** Errores fantasma tras recuperación. Mitigación:
  heurística de la sección 5.4; los tests de casos con múltiples errores fijan el
  comportamiento esperado.
- **`TokenMaker` de RSyntaxTextArea.** API algo mal documentada. Mitigación: es
  recortable (#9); no bloquea el resto.

## 11. Documento de diseño de la unidad (entregable académico)

Aparte de esta spec, la unidad exige `docs/unidad-0-diseno-ide.md` (30% de la nota):
gramática BNF final, tabla de tokens con ER, estrategia de recuperación de errores
con ejemplos, diagrama de módulos, capturas del IDE, tabla de casos de prueba y
resultados.
