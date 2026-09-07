# Unidad 0 — Documento de diseño: analizador léxico-sintáctico e IDE

**Materia:** Lenguajes y Autómatas II (SCD-1016)
**Entregable:** Analizador léxico y sintáctico (IDE)
**Rama de referencia:** `main` (front-end fusionado)
**Fecha:** 2026-09-06

Este documento describe **lo que realmente se construyó** en la Unidad 0: el
front-end del compilador para el subconjunto de Ada del proyecto (analizador
léxico y sintáctico con recuperación de errores y generación de AST) y el IDE
Swing que lo opera. Las decisiones de lenguaje y de subconjunto están fijadas en
`docs/CLAUDE.md`; aquí se documenta la implementación concreta contenida en
`src/main/javacc/Ada.jjt` y en el paquete `com.compiladorada`.

---

## 1. Introducción y alcance

### 1.1 Qué resuelve la unidad

La Unidad 0 retoma la base léxico-sintáctica de Lenguajes y Autómatas I y la
reconstruye sobre un único stack en Java, de forma que las cuatro unidades
siguientes (semántico, código intermedio, optimización, código objeto) se apoyen
en el **mismo AST** y en la misma tabla de símbolos futura sin rehacer el
front-end.

Entregables concretos cubiertos:

- Editor de código fuente con abrir/guardar y resaltado de sintaxis de Ada.
- Indicador en tiempo real de la posición del cursor (`Ln x, Col y`).
- Etapa léxica: tabla de tokens con columnas *token · tipo · línea · columna*.
- Etapa sintáctica: paneles **separados** de errores léxicos y sintácticos, con
  ubicación (línea:columna), explicación en español y **todos** los errores de
  la pasada (no solo el primero).
- Persistencia de tokens y errores a archivos en `output/`, sobrescritos en cada
  compilación, con formato de línea estilo GCC/GNAT.
- Gramática JJTree que cubre el subconjunto de Ada del proyecto y produce el AST.

### 1.2 Subconjunto de Ada

El proyecto no implementa Ada completo. El subconjunto está definido en
`docs/CLAUDE.md` (sección "Subconjunto de Ada soportado"); se resume aquí.

**Dentro de alcance (lo que el front-end reconoce):**

| Área | Construcciones |
|---|---|
| Unidades de compilación | `procedure`, `function`, `package` (spec y body); una sola unidad por archivo, sin `separate` real |
| Declaraciones | variables y constantes (`constant`) con inicialización opcional; `type` (rango, enumerado, `record`, `array`); `subtype ... is T [range ...]` |
| Tipos predefinidos | `Integer`, `Float`, `Boolean`, `Character`, `String` — **no son palabras reservadas**, son identificadores del paquete `Standard` |
| Literales | enteros, reales, con separador `_`, con base explícita (`16#FF#`), de carácter, de cadena, `null` |
| Operadores | lógicos (`and`, `or`, `xor`, `and then`, `or else`), relacionales, pertenencia (`in` / `not in` sobre rangos), concatenación (`&`), aditivos, multiplicativos (`mod`, `rem`), unarios (`abs`, `not`, signo `+`/`-`), exponenciación (`**`) |
| Control | `if/elsif/else`, `for` (con `reverse`), `while`, `null;`, llamadas a procedimiento |
| Parámetros | modos `in`, `out`, `in out` |
| Excepciones | bloque `begin ... exception ... when ... => ... end` y `raise [nombre];` |
| Comentarios | de línea (`--`), sin comentarios de bloque |

**Fuera de alcance (el front-end lo rechaza o no lo contempla):**

- Programación genérica (`generic`).
- Concurrencia (`task`, `protected`, `select`, `entry`, `accept`, `delay`,
  `abort`, `requeue`, `synchronized`).
- Orientación a objetos (`tagged`, herencia, despacho dinámico, `interface`,
  `overriding`).
- Tipos de acceso / punteros con manejo de memoria (`access`); `null` se acepta
  como literal sintáctico pero sin semántica de punteros.
- Tipos de punto fijo (`delta`, `digits` de precisión decimal).
- Texto internacional (`Wide_Character`, `Wide_String`, …); solo Latin-1/ASCII.
- Cadenas dinámicas (`Ada.Strings.Unbounded` / `Bounded`); solo `String` de
  longitud fija.
- *Subtype predicates*, `renames`, `goto`, `aliased`, compilación separada.
- Biblioteca estándar más allá de lo indispensable (`Ada.Text_IO` no se
  reimplementa).

> El analizador léxico reconoce como palabra reservada cada una de las **73
> palabras reservadas de Ada 2012** (tabla `PalabrasReservadas.TODAS`), incluidas
> las que están fuera del subconjunto: así un programa que use `task` o `generic`
> recibe un error *sintáctico* claro ("no se esperaba aquí") en vez de un error
> léxico confuso.

---

## 2. Modelo de compilación

### 2.1 El pipeline real de Ada (GNAT/GCC)

Ada no se compila en una sola pasada. GNAT ejecuta, de forma simplificada:

```
fuente ──▶ análisis de dependencias entre unidades (cláusulas `with`)
       ──▶ scanner (tokens)
       ──▶ parser  ──▶ AST (Nodos GNAT)
       ──▶ Sem      (análisis semántico y de tipos; varios recorridos del AST)
       ──▶ Expander (expansión de construcciones de alto nivel)
       ──▶ gigi / gnat2gnu  ──▶ GENERIC de GCC
       ──▶ middle-end de GCC  ──▶ GIMPLE ──▶ pasadas de optimización
       ──▶ back-end  ──▶ código objeto nativo
```

El semántico y el resto del pipeline son **multipasada**: recorren el AST
completo varias veces (resolución de nombres, verificación de tipos, expansión,
bajada a representación intermedia, optimización).

### 2.2 Qué replica y qué simplifica este proyecto

El proyecto replica la forma del pipeline y lo simplifica donde el alcance del
curso lo permite:

| Etapa GNAT | En este proyecto |
|---|---|
| Análisis de dependencias entre unidades | **Eliminado.** Una sola unidad de compilación por archivo, sin `separate`. No hay pasada de `with`/dependencias. |
| Scanner + parser | **Una sola pasada.** JavaCC/JJTree genera un parser descendente recursivo LL(k) que consume tokens y construye el AST sobre la marcha. |
| AST | AST JJTree (`SimpleNode` con anotaciones `#Nombre` y posiciones de token vía `TRACK_TOKENS`). |
| Sem, Expander, gigi, middle-end, back-end | **Unidades 1–4**, pasadas sucesivas sobre el AST ya construido. |

### 2.3 Por qué el léxico-sintáctico es de una pasada y el resto multipasada

- **Léxico + sintáctico — una pasada.** El parser LL(k) decide cada producción
  con la ventana de anticipación (`LOOKAHEAD`) y no necesita volver atrás sobre
  tokens ya consumidos. La recuperación en modo pánico **no** lo convierte en
  multipasada: ante un fallo salta tokens hacia adelante hasta un punto de
  sincronización y continúa; nunca reinicia el análisis.
- **Semántico y siguientes — multipasada.** La verificación semántica necesita
  información que solo está disponible tras recorrer todo el árbol (p. ej. una
  variable puede usarse antes de aparecer su declaración en el texto dentro de la
  misma zona declarativa; la resolución de tipos y de sobrecarga necesita la
  tabla de símbolos completa). Cada unidad posterior añade uno o más recorridos
  del mismo AST.
- **Pasada léxica dedicada adicional.** Para la tabla de tokens del IDE se hace
  un recorrido léxico separado (`AnalizadorLexico`), independiente del parseo. Se
  necesita la lista **completa** de tokens —incluidos comentarios y los tokens de
  zonas con error— aunque el parseo falle a mitad de camino. Ambos recorridos
  (léxico dedicado + parseo) son triviales en costo para el tamaño de programa
  del curso.

### 2.4 Fases secuenciales: el sintáctico no corre si el léxico tiene errores

`Compilador.analizar` ejecuta las fases **en orden y con parada**: primero el
análisis léxico completo; **solo si no hay ningún error léxico** se lanza el
parser. Con al menos un error léxico, el resultado se devuelve con la lista de
errores sintácticos vacía y sin AST, y `ResultadoCompilacion.sintacticoOmitido()`
devuelve `true`. El IDE lo refleja: la pestaña *Sintácticos* muestra
`(omitido)` con un aviso, `output/errores_sintacticos.txt` dice
`Análisis sintáctico OMITIDO`, y la barra de estado indica cuántos errores
léxicos quedan por corregir.

Es el modelo clásico de fases del curso: no se avanza a la fase *n+1* mientras la
fase *n* tenga errores. Dentro de **cada** fase sí se reportan todos los errores
de una sola pasada (recuperación léxica con tokens trampa; recuperación
sintáctica en modo pánico). Difiere de compiladores de producción (GCC, Clang),
que entrelazan las fases e intentan recuperarse en ambas a la vez.

---

## 3. Análisis léxico

Definido en la sección `TOKEN` / `SKIP` / `SPECIAL_TOKEN` de
`src/main/javacc/Ada.jjt`. Opciones relevantes: `IGNORE_CASE = true` (Ada no
distingue mayúsculas), `TRACK_TOKENS = true` (cada nodo del AST guarda su token
inicial y final).

### 3.1 Descartables y comentarios

| Elemento | Definición | Tratamiento |
|---|---|---|
| Espacio en blanco | `" " \| "\t" \| "\r" \| "\n"` | `SKIP` — descartado |
| Comentario de línea | `<COMENTARIO: "--" (~["\n","\r"])*>` | `SPECIAL_TOKEN` — el parser lo ignora; el recorrido léxico lo recoge para la tabla (tipo `COMENTARIO`) |

### 3.2 Tabla de tokens

Los patrones de literales siguen la tabla de expresiones regulares de
`docs/CLAUDE.md`. Notación JavaCC: `<#NOMBRE>` son producciones léxicas privadas
(no generan token propio).

**Auxiliares privadas:**

| Nombre | Definición |
|---|---|
| `DIGITO` | `["0"-"9"]` |
| `HEX` | `["0"-"9","a"-"f","A"-"F"]` |
| `EXP` | `["e","E"] (["+","-"])? (<DIGITO>)+` |

**Palabras reservadas** (bloque `TOKEN`): 39 tokens `KW_*` declarados; 38 los usa
alguna producción — `declare` se declara (`<KW_DECLARE: "declare">`) pero **ninguna
producción lo consume** (los bloques `declare ... begin` no están en el
subconjunto sintáctico). Los 39 lexemas declarados:
`procedure`, `function`, `package`, `body`, `is`, `begin`, `end`, `return`,
`constant`, `type`, `subtype`, `range`, `record`, `array`, `of`, `in`, `out`,
`if`, `then`, `elsif`, `else`, `loop`, `for`, `while`, `reverse`, `declare`,
`exception`, `when`, `raise`, `others`, `private`, `null`, `and`, `or`, `xor`,
`not`, `abs`, `mod`, `rem`. Cada uno se declara como token literal (`<KW_IF:
"if">`, …) porque la gramática necesita escribir `"if"` en sus producciones; el
orden de declaración de JavaCC hace que ganen frente a `<IDENTIFICADOR>`.

**Delimitadores compuestos** (se declaran antes que los simples para que el
maximal-munch de JavaCC los prefiera):

| Token | Lexema | Token | Lexema |
|---|---|---|---|
| `ASIGNA` | `:=` | `LEQ` | `<=` |
| `FLECHA` | `=>` | `ETIQIZQ` | `<<` |
| `PUNTOPUNTO` | `..` | `ETIQDER` | `>>` |
| `POT` | `**` | `CAJA` | `<>` |
| `NEQ` | `/=` | `BARRA` | `\|` |
| `GEQ` | `>=` | | |

**Delimitadores simples:** `(` `)` `,` `.` `:` `;` `=` `<` `>` `+` `-` `*` `/`
`&` `'`.

**Literales:**

| Token | Expresión regular (JavaCC) | Descripción |
|---|---|---|
| `ENTERO` | `<DIGITO> ( ("_")? <DIGITO> )*` | entero decimal; `_` como separador entre dígitos (`1_000_000`), nunca al final |
| `REAL` | `<DIGITO> ( ("_")? <DIGITO> )* "." <DIGITO> ( ("_")? <DIGITO> )* (<EXP>)?` | punto flotante con exponente opcional |
| `BASADO` | `(<DIGITO>)+ "#" <HEX> (("_")? <HEX>)* ("." <HEX> (("_")? <HEX>)*)? "#" (<EXP>)?` | literal con base explícita, p. ej. `16#FF#`, `2#1010#` |
| `CARACTER` | `"'" (~["'","\n","\r"]) "'"` | literal de carácter |
| `CADENA` | `"\"" ( (~["\"","\n","\r"]) \| "\"\"" )* "\""` | cadena; `""` interno es comilla escapada |

**Identificador:**

| Token | Expresión regular | Notas |
|---|---|---|
| `IDENTIFICADOR` | `["a"-"z","A"-"Z"] (["a"-"z","A"-"Z","0"-"9"])* ("_" (["a"-"z","A"-"Z","0"-"9"])+)*` | empieza por letra; no termina en `_`; no contiene `__` |

### 3.3 Palabras reservadas como `IDENTIFICADOR` + tabla aparte

`docs/CLAUDE.md` indica que las 73 palabras reservadas de Ada 2012 comparten la
expresión regular del identificador y deben distinguirse **después**, contra una
tabla de palabras clave, no con una ER separada.

La implementación lo respeta en la clasificación de la tabla de tokens del IDE:

- `PalabrasReservadas` (`com.compiladorada.lexico`) mantiene
  `PalabrasReservadas.TODAS`, un `Set<String>` con las **73** palabras reservadas
  de Ada 2012 en minúsculas. `esReservada(lexema)` hace `lexema.toLowerCase(Locale.ROOT)`
  antes de consultar el conjunto — comparación **case-insensitive**, coherente
  con Ada.
- `AnalizadorLexico.clasificar()` etiqueta un token cuyo `kind` es
  `IDENTIFICADOR` como `TipoToken.PALABRA_RESERVADA` si
  `PalabrasReservadas.esReservada(t.image)` es cierto, y como
  `TipoToken.IDENTIFICADOR` en caso contrario. Los tokens que la gramática ya
  declara como `<KW_*>` (rango contiguo `KW_PROCEDURE..KW_REM` en el
  `AdaParserConstants` generado) también se etiquetan `PALABRA_RESERVADA`.
- El resaltado del editor (`AdaTokenMaker`) usa el **mismo** conjunto
  `PalabrasReservadas.TODAS` en un `TokenMap` case-insensitive, de modo que las
  73 palabras se colorean aunque solo 39 tengan token propio en la gramática.

Los tipos predefinidos (`Integer`, `Float`, `Boolean`, `Character`, `String`)
**no** están en `PalabrasReservadas.TODAS`: son identificadores ordinarios.

### 3.4 Tokens trampa y catch-all para errores léxicos

Para reportar errores léxicos sin abortar el recorrido, la gramática declara
—justo antes de `<IDENTIFICADOR>` y del catch-all, para no desplazar los rangos
de `kind` contiguos que asume `AnalizadorLexico`— cuatro **tokens trampa** que
casan patrones inválidos y permiten emitir un mensaje específico:

| Token trampa | Casa | Mensaje (`AnalizadorLexico.mensajeLexico`) |
|---|---|---|
| `CADENA_SIN_CERRAR` | `"` seguido de texto y fin de línea sin `"` de cierre | `cadena sin cerrar antes de fin de línea` |
| `CARACTER_MALFORMADO` | `'` con dos o más caracteres antes del `'` de cierre | `literal de carácter mal formado: <lexema>` |
| `IDENT_MALFORMADO` | identificador que termina en `_` **o** que contiene `__` (sin caracteres ajenos) | `identificador no válido '<lexema>': no puede terminar en '_' ni contener '__'` |
| `LEXEMA_INVALIDO` | secuencia contigua de letras/dígitos/`_` con **al menos un** carácter ajeno al alfabeto (`@ $ ? \ ! ~`, no-ASCII…) | `secuencia no válida '<lexema>': contiene caracteres ajenos al lenguaje` |

`LEXEMA_INVALIDO` se define con dos clases privadas: `CAR_PALABRA`
(`[a-zA-Z0-9_]`) y `CAR_AJENO` (cualquier carácter que no sea de palabra, ni
espacio en blanco, ni inicio de un delimitador válido). El patrón es
`(CAR_PALABRA | CAR_AJENO)* CAR_AJENO (CAR_PALABRA | CAR_AJENO)*`, es decir, un
tramo maximal que exige ≥ 1 carácter ajeno. Como es un emparejamiento más largo
que `<IDENTIFICADOR>` / `<ENTERO>`, JavaCC lo prefiere: **`i@f` sale como un
único token de error**, no partido en `i` / `@` / `f`. El tramo no cruza
espacios ni delimitadores, así que `x $ y` sí produce tres tokens.

Y como **última** definición del bloque `TOKEN`:

```
<ERROR_LEXICO: ~[]>
```

red de seguridad para un carácter suelto que pertenece al alfabeto de
delimitadores pero no forma token por sí solo —en la práctica, un `#` aislado
fuera de un literal con base (`16#FF#`)—. El mensaje es `carácter no válido '<c>'`.

En los cinco casos (`CADENA_SIN_CERRAR`, `CARACTER_MALFORMADO`,
`IDENT_MALFORMADO`, `LEXEMA_INVALIDO`, `ERROR_LEXICO`) el token **sí aparece en
la tabla de tokens** con `TipoToken.ERROR`, y `AnalizadorLexico` añade un
`ErrorCompilacion` de categoría `LEXICO` con la línea y columna del token. El
recorrido continúa con el siguiente token; solo se detiene si el `TokenManager`
lanza `TokenMgrError` —caso aparte, no es un token— (situación no esperada con el
catch-all presente), en cuyo caso se registra un error léxico genérico en `1:1`.

---

## 4. Análisis sintáctico

Gramática completa en `src/main/javacc/Ada.jjt`, producciones bajo
`PARSER_END(AdaParser)`. El parser es descendente recursivo LL(k) generado por
JavaCC; JJTree añade la construcción del AST.

### 4.1 Gramática en BNF

Notación: `{ X }` = cero o más; `[ X ]` = opcional; `|` = alternativa;
`'x'` = token literal; `MAYÚSCULAS` = token léxico.

```
programa            ::= ( unidadCompilacion )+ EOF

unidadCompilacion   ::= paquete | procedimiento | funcion
                        (* LOOKAHEAD(2): 'package' → paquete *)

procedimiento       ::= 'procedure' IDENT [ '(' parametros ')' ] 'is'
                          { declaracion }
                        'begin'
                          { sentencia }
                        [ bloqueExcepcion ]
                        'end' [ IDENT ] ';'

funcion             ::= 'function' IDENT [ '(' parametros ')' ] 'return' tipoRef 'is'
                          { declaracion }
                        'begin'
                          { sentencia }
                        [ bloqueExcepcion ]
                        'end' [ IDENT ] ';'

paquete             ::= 'package'
                        ( 'body' IDENT 'is'
                              { declaracion }
                          [ 'begin' { sentencia } ]
                          [ bloqueExcepcion ]
                          'end' [ IDENT ] ';'
                        | IDENT 'is'
                              { declaracion }
                          [ 'private' { declaracion } ]
                          'end' [ IDENT ] ';' )

parametros          ::= parametro { ';' parametro }
parametro           ::= IDENT { ',' IDENT } ':' [ 'in' 'out' | 'in' | 'out' ] tipoRef
                        (* LOOKAHEAD(2) para 'in' 'out' *)

tipoRef             ::= IDENT

declaracion         ::= 'type' declaracionTipo
                      | 'subtype' declaracionSubtipo
                      | declaracionVar

declaracionTipo     ::= IDENT 'is' definicionTipo ';'
declaracionSubtipo  ::= IDENT 'is' IDENT [ 'range' rango ] ';'
definicionTipo      ::= 'range' rango
                      | '(' IDENT { ',' IDENT } ')'
                      | 'record' { componenteRegistro } 'end' 'record'
                      | 'array' '(' rango ')' 'of' tipoRef
componenteRegistro  ::= IDENT { ',' IDENT } ':' tipoRef [ ':=' expresion ] ';'
declaracionVar      ::= IDENT { ',' IDENT } ':' [ 'constant' ] tipoRef [ ':=' expresion ] ';'

sentencia           ::= 'null' ';'
                      | sentenciaIf
                      | sentenciaFor
                      | sentenciaWhile
                      | sentenciaRaise
                      | asignacion            (* LOOKAHEAD(IDENT ':=') *)
                      | llamadaProc

asignacion          ::= IDENT ':=' expresion ';'
llamadaProc         ::= IDENT [ '(' expresion { ',' expresion } ')' ] ';'
sentenciaIf         ::= 'if' expresion 'then' { sentencia }
                        { 'elsif' expresion 'then' { sentencia } }
                        [ 'else' { sentencia } ]
                        'end' 'if' ';'
sentenciaFor        ::= 'for' IDENT 'in' [ 'reverse' ] rango 'loop'
                          { sentencia }
                        'end' 'loop' ';'
sentenciaWhile      ::= 'while' expresion 'loop' { sentencia } 'end' 'loop' ';'
sentenciaRaise      ::= 'raise' [ IDENT ] ';'

bloqueExcepcion     ::= 'exception' ( manejador )+
manejador           ::= 'when' ( 'others' | IDENT { '|' IDENT } ) '=>' { sentencia }

rango               ::= expresion '..' expresion

expresion           ::= relacion { ( 'and' [ 'then' ] | 'or' [ 'else' ] | 'xor' ) relacion }
relacion            ::= simple [ ( '=' | '/=' | '<' | '<=' | '>' | '>=' ) simple
                                | [ 'not' ] 'in' rango ]
simple              ::= [ '+' | '-' ] termino { ( '+' | '-' | '&' ) termino }
termino             ::= factor { ( '*' | '/' | 'mod' | 'rem' ) factor }
factor              ::= [ 'abs' | 'not' ] primario [ '**' primario ]
primario            ::= '(' expresion ')'
                      | ENTERO | REAL | BASADO | CARACTER | CADENA | 'null'
                      | IDENT [ '(' expresion { ',' expresion } ')' ] { '.' IDENT }
```

Observaciones sobre la implementación real frente al fragmento BNF de
`docs/CLAUDE.md`:

- `simple` incorpora un **signo unario inicial `[ '+' | '-' ]`** que el fragmento
  base no tenía. Es fiel a Ada real (`X := -1;`).
- `factor` deja `**` **no asociativo**: `primario [ '**' primario ]` (a lo sumo un
  `**`). `2 ** 3 ** 2` se rechaza, igual que en la BNF de `docs/CLAUDE.md`.
- `primario` admite indexación/llamada `( ... )` y calificación con `.` para
  soportar `Paquete.Elemento` y `A(I)` dentro de expresiones; la distinción
  semántica (arreglo vs función vs componente) queda para la Unidad 1.
- `tipoRef` es solo `IDENT`: las definiciones de tipos nuevos viven en
  `declaracionTipo` / `definicionTipo`, no como anónimas dentro de una
  declaración de variable.

### 4.2 Nodos del AST (JJTree)

Opciones del bloque `options { }` de `Ada.jjt`: `MULTI = true`, `VISITOR = true`,
`NODE_DEFAULT_VOID = true`, `TRACK_TOKENS = true`. El paquete de los nodos
generados, `com.compiladorada.sintactico.nodos`, se fija fuera de la gramática,
en el parámetro `<nodePackage>` de la configuración del `javacc-maven-plugin` en
`pom.xml`. Solo las producciones anotadas con `#Nombre` generan nodo:

`Programa`, `Procedimiento`, `Funcion`, `Paquete`, `DeclaracionTipo`,
`DeclaracionSubtipo`, `DefinicionTipo`, `ComponenteRegistro`, `DeclaracionVar`,
`Parametro`, `Asignacion`, `LlamadaProc`, `If`, `For`, `While`.

Todos son subclases **genéricas de `SimpleNode`** generadas por JJTree (no hay
clases con lógica escrita a mano). `TRACK_TOKENS` deja en cada nodo su primer y
último token, de donde salen línea y columna. Las clases tipadas con campos
semánticos (tipo resuelto, referencia a símbolo, …) se difieren a la Unidad 1
(ver sección 9). Las producciones de expresión (`expresion`, `relacion`,
`simple`, `termino`, `factor`, `primario`) **no** generan nodo en esta unidad.

### 4.3 Ambigüedades y resolución con `LOOKAHEAD`

Ada tiene varias construcciones que empiezan con el mismo token; el parser LL las
resuelve con anticipación local:

| Ambigüedad | Punto de conflicto | Resolución en `Ada.jjt` |
|---|---|---|
| **Asignación vs llamada a procedimiento** | ambas empiezan con `IDENT`: `X := ...` vs `P(...);` o `P;` | `LOOKAHEAD(<IDENTIFICADOR> ":=")` en `sentencia()`: si tras el identificador viene `:=`, es `asignacion()`; si no, `llamadaProc()` |
| **`package body` vs `package` spec** | ambas empiezan con `package` | `LOOKAHEAD(2) paquete()` en `unidadCompilacion()`; dentro de `paquete()` la alternativa se decide por el token que sigue a `package`: `body` → cuerpo, `IDENT` → especificación |
| **Modo de parámetro `in out`** | `in` puede ser modo simple o inicio de `in out` | `LOOKAHEAD(2) <KW_IN> <KW_OUT>` antes de las alternativas `<KW_IN>` / `<KW_OUT>` sueltas en `parametro()` |
| **`and` vs `and then`, `or` vs `or else`** | `and`/`or` pueden ir solos o formar el operador de cortocircuito | en `expresion()`: tras consumir `and` se hace `LOOKAHEAD(2) <KW_THEN>` para absorber el `then` opcional; análogo con `or` / `else` |
| **Variantes de `end`** | `end;` / `end if;` / `end loop;` / `end record` / `end IDENT;` | no requiere `LOOKAHEAD` explícito: cada producción que abre un bloque (`sentenciaIf`, `sentenciaFor`, `sentenciaWhile`, `definicionTipo` con `record`, subprogramas) consume su propio cierre concreto, y el `IDENT` final opcional de subprograma/paquete se toma con `( <IDENTIFICADOR> )?` |
| **Definición de tipo enumerado vs otras** | `type T is ( ... )` vs `type T is range ...` / `record` / `array` | `definicionTipo()` discrimina por el primer token tras `is`: `range` / `(` / `record` / `array` |

Cuando JavaCC no puede resolver un conflicto con `LOOKAHEAD` numérico se usa
`LOOKAHEAD` sintáctico (patrón entre paréntesis), como en la asignación.

---

## 5. Recuperación de errores

Objetivo: una compilación produce **todas** las listas de errores de una sola
pasada y `Compilador.analizar` **nunca** propaga una excepción al IDE.

### 5.1 Errores léxicos

Ya descritos en la sección 3.4: tokens trampa (`CADENA_SIN_CERRAR`,
`CARACTER_MALFORMADO`, `IDENT_MALFORMADO`, `LEXEMA_INVALIDO`) y catch-all
`<ERROR_LEXICO: ~[]>`. El recorrido léxico registra el error y continúa con el
siguiente token; el token inválido queda en la tabla con tipo `ERROR`.

Si tras la pasada léxica **hay al menos un error**, el análisis sintáctico **no
se ejecuta** (ver §2.4): el resultado se entrega con `erroresSintacticos` vacía,
`ast == null` y `sintacticoOmitido() == true`. Hay que corregir los errores
léxicos y volver a compilar para que el parser corra.

### 5.2 Modo pánico con conjuntos de sincronización

Tres producciones envuelven su cuerpo en `try { ... } catch (ParseException e)`.
El `catch` llama a `registrar(e)` (traduce y acumula el error) y luego a
`sincronizar(...)`, que **consume tokens hacia adelante hasta —sin consumirlo—
uno del conjunto de sincronización o EOF**, contando anidamiento de paréntesis
(`prof`) para no salirse de una lista entre `(` y `)`:

| Producción | Conjunto de sincronización | Post-proceso |
|---|---|---|
| `declaracion()` | `;` · `begin` · `end` | si el token en cabeza es `;`, se consume |
| `sentencia()` | `;` · `end` · `elsif` · `else` · `exception` · `when` | si el token en cabeza es `;`, se consume |
| `unidadCompilacion()` | `procedure` · `function` · `package` | — (no consume el token de arranque de la siguiente unidad) |

Así, un `;` faltante en una declaración se resincroniza en la siguiente
declaración o en `begin`; un error dentro de un `if` se resincroniza en el
`elsif`/`else`/`end` sin arrastrar el resto del subprograma; y un error en la
cabecera de un subprograma salta a la siguiente unidad.

### 5.3 Traducción de mensajes

`TraductorMensajes.traducir(ParseException)` reescribe el mensaje de JavaCC
("Encountered X, expected one of ...") a español:

- **Token encontrado:** la imagen real del token ofensor (`e.currentToken.next`
  si existe, si no `e.currentToken`); `kind == 0` se traduce como
  `fin del archivo`.
- **Tokens esperados:** el primer token de cada secuencia esperada
  (`e.expectedTokenSequences`), pasado por un `Map` de nombres amigables (`";"` →
  `';'`, `<IDENTIFICADOR>` → `un identificador`, `<ENTERO>` → `un número entero`,
  …). Los que no están en el mapa se muestran tal cual.
- **Formato:** `se esperaba A o B o C pero se encontró 'X'`, o
  `construcción no válida cerca de 'X'` cuando no hay secuencias esperadas.

La línea y columna del error son las del token ofensor.

### 5.4 Errores fantasma y límites

- **Heurística `esFantasma`** (`AdaParser.esFantasma`): tras registrar un error,
  si el siguiente cae en la **misma línea** y a **≤ 2 columnas** del anterior, se
  descarta como "fantasma" (mismo punto de fallo reportado dos veces por la
  cascada del parser). Guarda `ultLinea`/`ultCol` y compara.
- **Tope de 200 errores:** si `erroresSintacticos` supera 200 entradas,
  `registrar` lanza `RuntimeException("demasiados errores sintácticos, análisis
  interrumpido")`, que `Compilador.analizar` captura como `Throwable` y conserva
  lo acumulado.
- **Garantía de avance:** en `programa()`, tras cada `unidadCompilacion()` se
  comprueba si la recuperación consumió algún token (`getToken(1) == antes`); si
  no avanzó y no es EOF, se fuerza `getNextToken()`. Evita el bucle infinito
  cuando el error está en el primer token de la unidad y la sincronización no
  puede avanzar.
- Si al final `ast == null` y no hay ningún error sintáctico acumulado,
  `Compilador` añade uno genérico: `no se pudo construir el árbol sintáctico` en
  `1:1`.

### 5.5 Ejemplos reales (de `src/test/resources/casos/invalidos/`)

**Ejemplo 1 — `04_falta_punto_y_coma.ada`** (`;` faltante en una declaración):

```ada
procedure P is
   X : Integer
begin
   null;
end P;
```

Salida (`errores_sintacticos.txt`):

```
04_falta_punto_y_coma.ada:3:1: error sintáctico: se esperaba ':=' o ';' o ... pero se encontró 'begin'
```

El error se reporta en `3:1` (el `begin`), que es donde el parser detecta que
faltó el `;`. `declaracion()` sincroniza hasta `begin` y el análisis del cuerpo
continúa sin más errores.

**Ejemplo 2 — `08_multiples_errores.ada`** (tres errores en líneas distintas,
verifica la recuperación):

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

Salida:

```
08_multiples_errores.ada:3:4: error sintáctico: se esperaba ':=' o ';' pero se encontró 'Y'
08_multiples_errores.ada:6:4: error sintáctico: se esperaba "in" o "and" o ... o ';' o ... pero se encontró 'Y'
08_multiples_errores.ada:7:9: error sintáctico: se esperaba "null" o "not" o "abs" o '(' o ... o un identificador pero se encontró ';'
```

Los tres errores (declaración sin `;`, sentencia sin `;`, expresión vacía en
`Z := ;`) se detectan en la misma pasada; cada uno se resincroniza en la
siguiente declaración/sentencia.

**Ejemplo 3 — `10_errores_lexicos_bloquean_sintactico.ada`** (los errores léxicos
detienen la fase sintáctica):

```ada
procedure P is
   contador_ : Integer := 1 @ 2
begin
   null
end P;
```

Este archivo tiene errores léxicos (`contador_`, `@`) **y** errores de sintaxis
(faltan dos `;`). Salida — `errores_lexicos.txt`:

```
10_...:5:4:  error léxico: identificador no válido 'contador_': no puede terminar en '_' ni contener '__'
10_...:5:29: error léxico: secuencia no válida '@': contiene caracteres ajenos al lenguaje
```

`errores_sintacticos.txt`:

```
Análisis sintáctico OMITIDO: hay 2 errores léxicos pendientes.
Corrígelos y vuelve a compilar.
```

El parser **no se ejecuta** porque la fase léxica falló (§2.4). Los `;` que
faltan no se reportan hasta que se corrijan `contador_` y `@` y se recompile.

---

## 6. Arquitectura del software

### 6.1 Separación motor / IDE

```
com.compiladorada                        ← MOTOR (sin dependencias de Swing)
│
├── Compilador                  fachada estática: analizar(fuente, nombre) → ResultadoCompilacion
├── ResultadoCompilacion        record: tokens, erroresLexicos, erroresSintacticos, ast
│
├── lexico/
│   ├── AnalizadorLexico        recorrido léxico dedicado → List<TokenLexico> + List<ErrorCompilacion>
│   ├── TokenLexico             record { lexema, tipo, linea, columna }
│   ├── TipoToken               enum: IDENTIFICADOR, PALABRA_RESERVADA, ENTERO, REAL,
│   │                                 BASADO, CARACTER, CADENA, DELIMITADOR_SIMPLE,
│   │                                 DELIMITADOR_COMPUESTO, COMENTARIO, ERROR
│   └── PalabrasReservadas      Set con las 73 palabras reservadas de Ada 2012; lookup case-insensitive
│
├── sintactico/
│   ├── TraductorMensajes       ParseException → mensaje en español con nombres amigables
│   └── nodos/                  SimpleNode + nodos AST generados por JJTree (paquete nodePackage)
│
├── errores/
│   ├── ErrorCompilacion        record { categoria (LEXICO|SINTACTICO), linea, columna, mensaje };
│   │                                   formatear(nombre) → "archivo:linea:columna: categoria: mensaje"
│   └── EscritorErrores         vuelca ResultadoCompilacion a {tokens,errores_lexicos,errores_sintacticos}.txt
│                               en la carpeta de salida que le pasa VentanaPrincipal (por defecto output/)
│
└── generado/                   AdaParser, AdaParserTokenManager, Token, ParseException, … (JavaCC/JJTree)

com.compiladorada.ide                     ← IDE (Swing + RSyntaxTextArea 3.5.4)
│
├── Main                        punto de entrada; construye VentanaPrincipal en el EDT
├── VentanaPrincipal            JFrame: JMenuBar, layout con JSplitPane anidados, flujo compilar()
├── EditorPanel                 RSyntaxTextArea + RTextScrollPane; nuevo/abrir/guardar/guardarComo, flag modificado
├── AdaTokenMaker               TokenMaker de RSyntaxTextArea para el resaltado de Ada
├── BarraEstado                 JPanel sur: "Ln x, Col y" (oeste) + resultado de compilación (este)
├── TablaTokensPanel            JTable no editable con AbstractTableModel sobre List<TokenLexico>
└── PanelErrores                JTabbedPane: pestaña "Léxicos (n)" / "Sintácticos (m)", doble clic → irA(linea,col)
```

### 6.2 Responsabilidad de cada clase

**Motor:**

- **`Compilador`** — fachada. Único punto de entrada del IDE al motor. Método
  estático `analizar(String fuente, String nombreArchivo) → ResultadoCompilacion`.
  Ejecuta el recorrido léxico dedicado y el parseo, captura **cualquier**
  `Throwable` del parser (incluido el corte por tope de 200) y devuelve siempre
  un `ResultadoCompilacion` con lo acumulado. **Nunca lanza.**
- **`ResultadoCompilacion`** — record inmutable con las cuatro salidas; helper
  `tieneErrores()`.
- **`AnalizadorLexico`** — instancia el `AdaParserTokenManager` generado y lo
  recorre token a token; recoge comentarios (`specialToken`), clasifica cada
  token en un `TipoToken` y genera un `ErrorCompilacion` léxico por cada token de
  error. No lanza.
- **`TokenLexico` / `TipoToken`** — modelo de la tabla de tokens.
- **`PalabrasReservadas`** — tabla case-insensitive de las 73 palabras
  reservadas; solo se usa para clasificar y para el resaltado.
- **`ErrorCompilacion`** — modelo de error con categoría, posición y mensaje;
  `formatear` produce la línea estilo GCC.
- **`EscritorErrores`** — serializa `ResultadoCompilacion` a los tres archivos
  `.txt` dentro del directorio de salida que recibe como parámetro (por defecto
  `output/`, fijado en `VentanaPrincipal`): cabecera con fecha ISO + total, luego
  una línea por entrada.
- **`TraductorMensajes`** — aislado del parser generado; convierte
  `ParseException` a español.
- **`AdaParser`** (generado desde `Ada.jjt`) — parser + lógica de recuperación
  embebida en el bloque `PARSER_BEGIN` (`registrar`, `esFantasma`,
  `sincronizar`).

**IDE:**

- **`Main`** — `SwingUtilities.invokeLater(() -> new VentanaPrincipal().setVisible(true))`.
- **`VentanaPrincipal`** — orquesta todo: construye el menú, el layout y conecta
  el `CaretListener` (posición del cursor) y el callback de doble clic en errores.
  `compilar()` es **síncrono** en el hilo llamante (una compilación del
  subconjunto es instantánea y así el método es testeable sin sincronización).
- **`EditorPanel`** — ciclo de vida del archivo fuente; registra el mapeo
  `text/ada → AdaTokenMaker` en la `TokenMakerFactory` por defecto.
- **`AdaTokenMaker`** — `AbstractTokenMaker`: colorea palabras reservadas
  (vía `PalabrasReservadas.TODAS`), comentarios `--`, cadenas, literales de
  carácter y números. Es un lexer mínimo independiente del de la gramática.
- **`BarraEstado`, `TablaTokensPanel`, `PanelErrores`** — vistas pasivas; reciben
  datos vía setters y exponen getters para las pruebas.

### 6.3 El papel de la fachada `Compilador`

`Compilador` es la **única** frontera entre el IDE y el motor. Ninguna clase de
`com.compiladorada.ide` importa nada del paquete `com.compiladorada.generado` ni
construye un `AdaParser`. Ventajas:

- El IDE no necesita saber que hay JavaCC detrás.
- El contrato `analizar(fuente, nombre) → ResultadoCompilacion` no cambia entre
  unidades: la Unidad 1 añadirá `erroresSemanticos()` y una tabla de símbolos al
  `ResultadoCompilacion` sin tocar el IDE.
- `Compilador` absorbe toda excepción: el IDE nunca tiene que envolver
  `compilar()` en un `try/catch` de errores del parser.

Flujo de datos, un solo sentido:
`EditorPanel.getTexto()` → `VentanaPrincipal.compilar()` →
`Compilador.analizar(...)` → `ResultadoCompilacion` →
`TablaTokensPanel.setTokens()` + `PanelErrores.setErrores()` +
`EscritorErrores.volcar(...)` + `BarraEstado.setResultado()`.

---

## 7. El IDE

### 7.1 Layout

`VentanaPrincipal` (`JFrame`, 1100×750) usa `BorderLayout` con dos `JSplitPane`
anidados y una barra de estado al sur:

```
┌─────────────────────────────────────────────────────────────┐
│ Archivo   Editar   Compilar   Ver   Ayuda        (JMenuBar)  │
├───────────────────────────────┬─────────────────────────────┤
│                               │  Token │ Tipo │ Línea │ Col  │
│  Editor                       │  ──────┼──────┼───────┼───── │
│  (RSyntaxTextArea + gutter    │  ...   │ ...  │ ...   │ ...  │
│   de números de línea,        │       (TablaTokensPanel,     │
│   resaltado de Ada)           │        JTable ordenable)     │
├───────────────────────────────┴─────────────────────────────┤
│  [ Léxicos (n) | Sintácticos (m) ]              (PanelErrores)│
│  Ln:Col │ Mensaje                                            │
│  ...    │ ...            (JTable; doble clic → salta al error)│
├─────────────────────────────────────────────────────────────┤
│  Ln 12, Col 8                    Compilado: 0 léxicos, ...   │  BarraEstado
└─────────────────────────────────────────────────────────────┘
```

- `JSplitPane` horizontal (`resizeWeight = 0.62`): editor a la izquierda, tabla
  de tokens a la derecha.
- `JSplitPane` vertical (`resizeWeight = 0.72`): lo anterior arriba, panel de
  errores abajo.
- `BarraEstado` al sur: `Ln x, Col y` al oeste (actualizado por un
  `CaretListener` en **cada** movimiento del cursor, no solo al compilar) y el
  resultado de la última compilación al este.

### 7.2 Menú único (`JMenuBar`), sin barra de herramientas

| Menú | Ítems |
|---|---|
| **Archivo** | Nuevo · Abrir… · Guardar · Guardar como… · —— · Salir |
| **Editar** | Deshacer · Rehacer · —— · Cortar · Copiar · Pegar |
| **Compilar** | Compilar (**F5**) · —— · Abrir carpeta `output/` |
| **Ver** | Mostrar/ocultar tabla de tokens · Mostrar/ocultar panel de errores |
| **Ayuda** | Acerca de… |

No hay `JToolBar`. "Compilar" lleva acelerador `F5`
(`KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)`) activo sin abrir el menú. Abrir y
Guardar como usan `JFileChooser`.

### 7.3 Flujo "Compilar" (menú Compilar o F5)

`VentanaPrincipal.compilar()`:

1. Toma `editor.getTexto()` y `editor.getNombreArchivo()` (este devuelve
   `fuente_sin_guardar.ada` si el editor no tiene archivo asociado).
2. `ResultadoCompilacion r = Compilador.analizar(fuente, nombre)` — síncrono.
3. `tablaTokens.setTokens(r.tokens())` y
   `panelErrores.setErrores(r.erroresLexicos(), r.erroresSintacticos())`
   (actualiza los contadores de las pestañas).
4. Crea `output/` si no existe. Si el editor no tiene ruta, escribe el texto en
   `output/fuente_sin_guardar.ada`.
5. `EscritorErrores.volcar(r, output/, nombre)` — sobrescribe por completo
   `tokens.txt`, `errores_lexicos.txt`, `errores_sintacticos.txt`.
6. `barraEstado.setResultado("Compilado: n léxicos, m sintácticos — <ruta
   absoluta de output/> actualizado")`, más una nota si se guardó la fuente sin
   guardar.
7. Si falla la escritura en disco, la barra de estado muestra
   `No se pudo escribir en <ruta>` y no se detiene la aplicación.

Doble clic en una fila del panel de errores → `VentanaPrincipal.irA(linea,
columna)` mueve el cursor del editor a esa posición.

### 7.4 Archivos de `output/`

Carpeta fija `output/` en la raíz del proyecto (`Path.of("output")`, configurable
con `setDirectorioSalida`). Se **sobrescriben completos** en cada compilación.

| Archivo | Contenido |
|---|---|
| `tokens.txt` | cabecera (`== TABLA DE TOKENS ==`, fecha ISO, total) + tabla de columnas alineadas: `LEXEMA` (recortado a 24, con `\n`/`\t` escapados) · `TIPO` · `LINEA` · `COLUMNA` |
| `errores_lexicos.txt` | cabecera (`== ERRORES LÉXICOS ==`, fecha ISO, total) + una línea por error, o `Sin errores.` |
| `errores_sintacticos.txt` | ídem con `== ERRORES SINTÁCTICOS ==` |
| `fuente_sin_guardar.ada` | solo si el editor no tiene archivo: copia del texto compilado |

Formato de cada línea de error (estilo GCC/GNAT), producido por
`ErrorCompilacion.formatear`:

```
<archivo>:<linea>:<columna>: <categoria>: <mensaje>
```

donde `<categoria>` es `error léxico` o `error sintáctico`. Ejemplo real:

```
mi_programa.ada:2:21: error léxico: carácter no válido '$'
mi_programa.ada:3:1: error sintáctico: se esperaba ':=' o ';' pero se encontró 'begin'
```

### 7.5 Capturas de pantalla

> El entorno de desarrollo actual no tiene display; las capturas las toma la
> persona usuaria ejecutando `mvn -q exec:java` (o el jar del *shade*) y
> guardándolas en `docs/img/`. Ver `docs/img/README.md` para el detalle de qué
> mostrar en cada una.

> **[Captura pendiente: editor con resaltado de sintaxis de Ada]** — `docs/img/01-editor-resaltado.png`

> **[Captura pendiente: tabla de tokens poblada tras compilar]** — `docs/img/02-tabla-tokens.png`

> **[Captura pendiente: panel de errores con múltiples errores léxicos y sintácticos]** — `docs/img/03-panel-errores.png`

---

## 8. Pruebas

Suite JUnit 5 (`junit-jupiter` 5.11.4). Ejecutada con `mvn -q test`.

### 8.1 Batería de casos `.ada`

`src/test/resources/casos/`, ejercitada por `CasosDePruebaTest` (un
`@TestFactory` por carpeta; cada archivo es un `DynamicTest`). Todos pasan.

**Casos válidos** (`casos/validos/`, deben dar **0 errores** léxicos y
sintácticos):

| Archivo | Qué ejercita | Resultado esperado | Obtenido |
|---|---|---|---|
| `01_procedimiento_minimo.ada` | `procedure … is begin null; end;` | 0 errores | 0 errores ✔ |
| `02_funcion_con_parametros.ada` | `function` con parámetros y `return` | 0 errores | 0 errores ✔ |
| `03_paquete_spec_y_body.ada` | `package … is` con variable y `constant` | 0 errores | 0 errores ✔ |
| `04_tipos_del_usuario.ada` | `type` rango, enumerado, `record`, `array`; `subtype … range` | 0 errores | 0 errores ✔ |
| `05_control_anidado.ada` | `if/elsif/else` con asignaciones | 0 errores | 0 errores ✔ |
| `06_ciclos.ada` | `for … in reverse … loop`, `while … loop` | 0 errores | 0 errores ✔ |
| `07_expresiones.ada` | precedencia completa: `+ - * /`, `**`, `mod`, `rem`, `and then`, `or else`, `not`, paréntesis | 0 errores | 0 errores ✔ |
| `08_excepciones.ada` | `exception / when X \| Y => / when others => / raise;` | 0 errores | 0 errores ✔ |

**Casos inválidos** (`casos/invalidos/`, cada uno con un `.expected` que lista
`linea:columna:CATEGORIA`; el test verifica que **cada** error esperado esté
presente):

| Archivo | Error(es) esperado(s) | Categoría | Obtenido |
|---|---|---|---|
| `01_caracter_ilegal.ada` | `2:21` — `$` ilegal | LÉXICO | presente ✔ |
| `02_cadena_sin_cerrar.ada` | `2:18` — cadena sin cerrar | LÉXICO | presente ✔ |
| `03_identificador_doble_guion.ada` | `2:4` — `mi__var` | LÉXICO | presente ✔ (el error sintáctico que provoca no se reporta: fase léxica con errores) |
| `04_falta_punto_y_coma.ada` | `3:1` — `;` faltante (detectado en `begin`) | SINTÁCTICO | presente ✔ |
| `05_end_if_faltante.ada` | `6:5` — falta `end if;` | SINTÁCTICO | presente ✔ |
| `06_parentesis_desbalanceado.ada` | `4:19` — `(` sin cerrar | SINTÁCTICO | presente ✔ |
| `07_then_faltante.ada` | `5:7` — falta `then` | SINTÁCTICO | presente ✔ |
| `08_multiples_errores.ada` | `3:4`, `6:4`, `7:9` — tres errores en líneas distintas | SINTÁCTICO | los 3 presentes ✔ |
| `09_loop_sin_end.ada` | `5:5` — `loop` sin `end loop;` | SINTÁCTICO | presente ✔ |
| `10_errores_lexicos_bloquean_sintactico.ada` | `5:4` y `5:29` — dos errores léxicos; los `;` faltantes **no** se reportan | LÉXICO | los 2 presentes; 0 sintácticos ✔ |

El caso 08 valida la **recuperación sintáctica** (varios errores en una pasada);
el caso 10 valida el **gating de fases** (con errores léxicos, el parser no
corre y su `errores_sintacticos.txt` dice `OMITIDO`).

### 8.2 Conteo total de tests

`mvn -q test` → **121 tests, 0 fallos, 0 errores** (agregado de
`target/surefire-reports/`). Desglose por clase:

| Clase de test | Tests | Cubre |
|---|---:|---|
| `com.compiladorada.CasosDePruebaTest` | 19 | batería `.ada` válidos (9) + inválidos (10), `@TestFactory` |
| `com.compiladorada.CompiladorTest` | 5 | fachada: nunca lanza, AST presente/ausente, léxico bloquea sintáctico |
| `com.compiladorada.ModeloDatosTest` | 5 | records `TokenLexico`, `ErrorCompilacion`, `ResultadoCompilacion` |
| `com.compiladorada.errores.EscritorErroresTest` | 4 | formato de línea, cabecera, sobrescritura, aviso de sintáctico omitido |
| `com.compiladorada.lexico.AnalizadorLexicoTest` | 11 | clasificación de tokens, `_` en enteros, `LEXEMA_INVALIDO`, comentarios, posiciones, guarda de rangos |
| `com.compiladorada.lexico.LexerHumoTest` | 5 | humo del `TokenManager` generado; `i@f` como un solo token |
| `com.compiladorada.lexico.PalabrasReservadasTest` | 4 | 73 palabras, case-insensitive, tipos predefinidos no reservados |
| `com.compiladorada.lexico.RecuperacionLexicaTest` | 6 | tokens trampa + catch-all |
| `com.compiladorada.sintactico.GramaticaSubprogramasTest` | 6 | `procedure` / `function` / `package` |
| `com.compiladorada.sintactico.GramaticaTiposTest` | 7 | `type`, `subtype`, `record`, `array`, enumerados |
| `com.compiladorada.sintactico.GramaticaSentenciasTest` | 8 | `if`, `for`, `while`, asignación (incl. `A(i)` y `R.campo`), llamada, `raise` |
| `com.compiladorada.sintactico.GramaticaExpresionesTest` | 5 | precedencia, cortocircuito, pertenencia, `**` no asociativo |
| `com.compiladorada.sintactico.RecuperacionSintacticaTest` | 7 | modo pánico, errores fantasma, múltiples errores, basura final |
| `com.compiladorada.sintactico.AstTest` | 5 | nodos JJTree generados y posiciones |
| `com.compiladorada.ide.EditorPanelTest` | 6 | nuevo/abrir/guardar, flag modificado |
| `com.compiladorada.ide.PanelesTest` | 8 | `TablaTokensPanel`, `PanelErrores` (incl. sintáctico omitido), `BarraEstado` |
| `com.compiladorada.ide.VentanaPrincipalTest` | 4 | flujo `compilar()`, escritura de `output/`, gating léxico→sintáctico |
| `com.compiladorada.ide.AdaTokenMakerTest` | 6 | resaltado: palabras reservadas, comentarios, cadenas, números |
| **Total** | **121** | |

El IDE se prueba construyendo componentes Swing reales (no *mock*); la suite
**no** es portable a un CI *headless* sin un servidor X virtual (ver sección 9).

---

## 9. Limitaciones conocidas y trabajo futuro

### 9.1 Limitaciones del front-end actual

- **AST sin clases tipadas.** Los nodos son subclases genéricas de `SimpleNode`
  generadas por JJTree, identificadas solo por la anotación `#Nombre` y con
  posiciones vía `TRACK_TOKENS`. No hay campos semánticos (tipo resuelto,
  referencia a símbolo, valor constante). Las clases de nodo concretas se
  construyen en la Unidad 1.
- **Signo unario en `simple`.** La producción `simple` admite un `[ '+' | '-' ]`
  inicial que no está en el fragmento BNF de `docs/CLAUDE.md`; es una extensión
  deliberada y fiel a Ada real.
- **`**` no asociativo.** `factor` permite a lo sumo un `**` (`primario [ '**'
  primario ]`), de modo que `2 ** 3 ** 2` se **rechaza**. Es lo que dicta la BNF
  de `docs/CLAUDE.md`; Ada real también obliga a parentizar.
- **`CARACTER_MALFORMADO` puede tragarse tokens entre apóstrofos.** El patrón
  `'` … `'` con más de un carácter interno casa cosas como `'a b'`. Es inofensivo
  en el subconjunto porque los atributos de Ada (`X'First`, `T'Range`) están
  fuera de alcance y el apóstrofo solo aparece en literales de carácter.
- **Recuperación de unidad gruesa.** `unidadCompilacion()` sincroniza hasta el
  siguiente `procedure` / `function` / `package`. Un error en la **cabecera** de
  un subprograma (antes de `is`) puede hacer que se salte el resto de esa unidad
  sin más diagnósticos.
- **`esFantasma` puede ocultar un error real.** Si un segundo error genuino cae
  en la misma línea y a ≤ 2 columnas del anterior, la heurística lo descarta.
  Situación poco frecuente en programas reales.
- **Separador `_` limitado a uno.** Las ER de `ENTERO` y `REAL` en `Ada.jjt`
  permiten un único `_` (`<DIGITO> ("_")? (<DIGITO>)*`), simplificación del
  `[0-9](_?[0-9])*` de `docs/CLAUDE.md`. `1_000_000` no se acepta como un solo
  token; se parte.
- **Suite Swing no *headless*.** `EditorPanelTest`, `PanelesTest`,
  `VentanaPrincipalTest` y `AdaTokenMakerTest` instancian componentes reales;
  requieren un display (o `Xvfb`) para correr en CI.
- **Excepciones parseadas pero no ejecutadas.** `begin/exception/when/raise` se
  analiza por completo en léxico y sintáctico (y se hará en semántico), pero las
  Unidades 2–4 lo simplificarán a una comprobación explícita, no a una tabla de
  manejadores en tiempo de ejecución.
- **Construcciones fuera de alcance.** Genéricos, concurrencia, OO, tipos de
  acceso reales, punto fijo, texto internacional y compilación separada no tienen
  producción. Solo las 39 palabras reservadas declaradas como token propio en
  `Ada.jjt` provocan un error sintáctico allí donde aparezcan; las otras ~34
  reservadas de Ada 2012 (`task`, `generic`, `tagged`, ...) se lexan como
  `IDENTIFICADOR` y el parser las acepta sin error, aunque la tabla de tokens del
  IDE las siga etiquetando como `PALABRA_RESERVADA`: es una divergencia conocida
  entre la tabla léxica y el parser.

### 9.2 Conexión con la Unidad 1 (análisis semántico)

- **Punto de enganche: el AST JJTree.** La Unidad 1 recibe el `SimpleNode` raíz
  (`Programa`) desde `ResultadoCompilacion.ast()`. Las opciones `VISITOR = true`
  y `MULTI = true` ya generan la infraestructura de visitantes; el semántico
  implementará uno o varios `AdaParserVisitor` que recorran el árbol.
- **Nodos tipados.** Se sustituirán las subclases genéricas de `SimpleNode` por
  clases con estado (tipo, ámbito, símbolo), añadiendo `NODE_CLASS` o código en
  las producciones `#Nombre` del `.jjt`. Como el IDE solo habla con `Compilador`,
  este cambio no lo afecta.
- **Tabla de símbolos.** No existe todavía. La Unidad 1 la añadirá como una
  estructura de ámbitos anidados poblada en un primer recorrido del AST
  (declaraciones) y consultada en un segundo (usos), implementando la regla base:
  *todo identificador declarado al menos una vez y con un solo tipo*. La batería
  de errores semánticos E1–E15 se probará con el mismo mecanismo de casos
  `.ada` + `.expected` de la sección 8.
- **Ampliación de `ResultadoCompilacion`.** Se le añadirá `erroresSemanticos()` y
  un acceso a la tabla de símbolos, manteniendo la firma
  `Compilador.analizar(fuente, nombre)`. El `PanelErrores` del IDE ganará una
  tercera pestaña sin más cambios estructurales.
- **Hacia las Unidades 2–4.** El mismo AST anotado por el semántico alimenta la
  generación de código intermedio (representación tipo GENERIC/GIMPLE
  simplificada), la optimización (propagación de constantes, eliminación de
  código muerto) y la generación de NASM, sin volver a analizar el texto fuente.
