# Optimización de código fuente — Diseño

Fecha: 2026-10-28

## Intención

Tercera unidad del curso: la etapa de **optimización**. El requisito de la
materia es, sobre el mismo código fuente: eliminar comentarios, espacios y
retornos de carro innecesarios, y aplicar una técnica de optimización a un
tipo de instrucción. El resultado que importa es **el código fuente Ada ya
optimizado**, que se muestra junto al original para exponerlo y revisarlo.

Debe verse sobrio: dos textos lado a lado, sin resaltado de cambios y sin
notas que expliquen qué técnica se aplicó. Lo único que se pide es que
**funcione a la perfección**: el código optimizado debe ser Ada válido, con
el mismo comportamiento que el original.

## Objetivos

1. **Compactar** el fuente: quitar comentarios, líneas en blanco, indentación
   y espacios que Ada no necesita.
2. **Plegado de constantes** sobre expresiones aritméticas.
3. **Reducción de potencia** (*strength reduction*) sobre expresiones
   aritméticas.
4. Nueva pestaña **Optimizado** con original y optimizado lado a lado.
5. Volcado a `output/codigo_optimizado.ada`.

## No objetivos

- No hay propagación de constantes (reemplazar los usos de `Base` por su
  valor), ni eliminación de código muerto, ni optimización de ciclos, ni
  identidades algebraicas (`X * 1`, `X + 0`, `X * 0`).
- No se resalta lo que cambió ni se anota qué técnica se usó, ni en el IDE
  ni en el archivo de salida.
- No se optimiza código con errores.
- No se convierte `X * 4` en desplazamiento de bits (el subconjunto de Ada no
  tiene operador de desplazamiento).

## Anti-objetivos (lo que cuenta como fallo aunque "compile")

- Un programa optimizado que se comporte distinto al original (valor, o error
  de ejecución que aparece o desaparece).
- Un programa optimizado que no pase el propio compilador sin errores.
- Una vista que parezca "producida": adornos, colores de diff, leyendas.

## Alcance y activación

- La fase corre después del código intermedio y **solo si no hay errores
  léxicos, sintácticos ni semánticos** (misma regla secuencial de
  `Compilador`). Con errores, la pestaña muestra que la optimización se omitió
  y `output/codigo_optimizado.ada` lleva un aviso de omisión, igual que
  `codigo_intermedio.txt`.
- A diferencia del código intermedio (solo el procedimiento principal), la
  optimización recorre **todo el programa**: todos los procedimientos y
  funciones, cuerpos de `if`/`while`/`for`, condiciones, inicializadores,
  rangos y argumentos de llamada. Plegar y reducir son transformaciones
  locales y sin memoria de variables, por lo que no dependen del flujo.

## Transformaciones

### 1. Compactación

Se hace sobre la lista de tokens que ya produce `AnalizadorLexico` (incluye
los comentarios como tokens de tipo `COMENTARIO`).

- Se descartan los comentarios.
- Cada token se emite con su texto exacto (las cadenas y los caracteres con
  espacios internos no se tocan; se respeta que Ada no distingue mayúsculas
  de minúsculas pero se conserva la capitalización original).
- Se escribe un espacio entre dos tokens **solo si pegarlos cambiaría cómo se
  separan en tokens** (dos palabras/identificadores/números seguidos; `-`
  seguido de `-` formaría un comentario; `*` seguido de `*`, `.` seguido de
  `.`, y casos análogos). En el resto no hay espacio: `Total:=A+B*2;`,
  `Total:Integer;`. (Corrección a lo comentado en el chat: ahí se puso
  `Total : Integer;` como ejemplo; el criterio real es el mínimo necesario.)
- Hay un salto de línea después de `;` y después de las palabras que abren un
  bloque o ramas (`is`, `begin`, `then`, `else`, `loop`, `declare`,
  `exception`, `record`, `private`, y `=>` de una rama `when`), de modo que
  el texto queda una sentencia por línea. No hay líneas en blanco ni
  indentación. Los saltos **no** se escriben cuando la palabra forma parte
  de una construcción de varias palabras: `end loop;`, `end record;`,
  `end if;`, `or else` y `and then` se quedan en una sola línea.

### 2. Plegado de constantes

Una subexpresión aritmética formada solo por literales se reemplaza por su
resultado. `2 ** 5 + 3` → `35`; `C : constant Integer := 3 * 4;` →
`C:constant Integer:=12;`.

- Reutiliza el árbol (`ArbolExpresion`) y el `Evaluador` de la fase
  intermedia, para que la aritmética sea idéntica (Integer de 32 bits con
  división truncada, `mod`/`rem` de Ada; Float como `double`).
- **No se pliega** si la evaluación fallaría en ejecución (división entre
  cero, desbordamiento de Integer, exponente negativo de Integer): esa
  subexpresión se deja tal cual, para conservar el error en ejecución.
- Un resultado negativo se escribe como menos unario y, si el contexto lo
  exige, entre paréntesis (`A * (-3)`), de modo que el árbol resultante sea
  el mismo.
- Los Float se pliegan solo si el resultado es finito, y se escriben como
  literal real de Ada (siempre con punto decimal, p. ej. `35.0`, y con
  exponente cuando haga falta).
- Los nombres (variables y constantes) **no** se sustituyen por su valor.
  `A + 2 * 3` → `A+6`.

### 3. Reducción de potencia

Se aplica después del plegado, sobre los operandos que son **un identificador
simple o un literal** (no llamadas a función, ni elementos de arreglo, ni
campos de registro: duplicarlos evaluaría dos veces).

| Original | Optimizado |
|---|---|
| `X ** 2` | `X * X` |
| `X * 2`, `2 * X` (Integer o Float, con `2` o `2.0`) | `X + X` |
| `X / 2.0` (Float) | `X * 0.5` |

- Se regenera el texto de la expresión afectada desde el árbol. **La
  estructura del árbol se conserva estrictamente**: el texto generado debe
  volver a analizarse al mismo árbol que se tenía tras transformar, de modo
  que el orden de evaluación (y con él dónde ocurre un desbordamiento o un
  redondeo de Float) no cambia. Por eso se parentiza todo subárbol que el
  texto sin paréntesis reagruparía de otra forma: `A * 2 * B` → `(A+A)*B`;
  `2 ** 5 + A * 2` → `32+(A+A)`; `A - B * 2` → `A-(B+B)`. Los paréntesis
  que no hacen falta para conservar el árbol no se escriben.
- Si una expresión no cambió, se emiten sus tokens originales sin
  regenerarla.
- Cada regla preserva el valor y los errores de desbordamiento del original.

### Dónde se aplican y a qué tipos

- **Subexpresiones, no solo expresiones completas.** Se recorre el AST; en
  cada expresión se buscan los **subárboles aritméticos maximales** (si la
  expresión entera no es aritmética, como `X > 2 * 3`, `F(1) + 2 * 3`,
  `A(I * 2)` o `Put(2 * 3)`, se baja a sus hijos). Los subárboles se
  transforman y se reinsertan en su posición.
- **Plegado:** solo necesita literales, no tipos. Un literal entero es
  siempre aritmético, aunque el contexto sea un tipo con rango del usuario
  (`G : Grado := 50 * 3` → `G:Grado:=150`; si 150 viola el rango, el error en
  ejecución sigue ocurriendo igual que antes).
- **Reducción:** solo sobre identificadores cuyo tipo declarado es
  exactamente `Integer` o `Float` (variables, constantes, parámetros y
  variables de `for` de ese tipo, en el ámbito donde se usan). Los tipos del
  usuario (`range`, subtipos, derivados) no se tocan. De dónde sale la
  información de tipos (tabla de símbolos del análisis semántico o una
  reconstrucción propia) lo decide el constructor.
- Un resultado negativo plegado se escribe como `(-3)` cuando la gramática
  no admite un menos unario en esa posición (p. ej. `A * (-3)`, `X ** (-1)`).

## Componentes

Paquete nuevo `com.compiladorada.optimizacion`:

| Unidad | Responsabilidad |
|---|---|
| `Optimizador` | Punto de entrada: recibe el fuente, los tokens y el AST; devuelve `ResultadoOptimizacion`. |
| `Compactador` | Tokens → texto compacto con las reglas de espaciado y saltos de línea. |
| `PlegadorConstantes` | Plegado sobre el árbol de expresión. |
| `ReductorPotencia` | Reducción de potencia sobre el árbol de expresión. |
| `ResultadoOptimizacion` | Fuente original y fuente optimizado (texto). |
| `EscritorOptimizacion` | Vuelca `output/codigo_optimizado.ada`. |

`ResultadoCompilacion` gana el campo `optimizacion` (nulo si la fase se
omitió) y un `optimizacionOmitido()`, igual que `intermedio`. Se conservan los
constructores existentes para no romper las pruebas actuales.

El compactador trabaja sobre los tokens de `AnalizadorLexico`, que sale del
mismo `AdaParserTokenManager` que alimenta al parser: las líneas y columnas
de esos tokens coinciden con las de `jjtGetFirstToken()` y el token final de
cada nodo del AST, así que los rangos de expresión se localizan por
posición.

La construcción del árbol puede reutilizar `ConstructorArbol` y
`ArbolExpresion` del paquete `intermedio`; se pueden hacer públicos o
generalizar lo necesario. Los límites de cada expresión en el fuente salen de
los tokens inicial y final de los nodos del AST (`jjtGetFirstToken()` y su
equivalente final).

## IDE

- Nueva pestaña **"Optimizado"** en el panel inferior, junto a *Errores* y
  *Código intermedio*.
- Dos áreas de texto de solo lectura en un `JSplitPane` horizontal, con el
  mismo resaltado de sintaxis Ada del editor: a la izquierda el fuente
  original tal como está en el editor al compilar; a la derecha el
  optimizado. Sin sincronía de desplazamiento (los textos tienen distinto
  número de líneas) y sin resaltado de cambios.
- Se rellena en cada `compilar()`; con errores previos muestra el aviso de
  omisión.
- El volcado a `output/` se hace junto a los demás en `compilar()`.

## Pruebas

- **Compactador:** sin comentarios, sin líneas en blanco ni indentación;
  espaciado mínimo; `--` no se forma al pegar tokens; cadenas intactas.
- **Plegado:** casos con cada operador, precedencia, signo, Float, y los casos
  que no se pliegan (división entre cero, desbordamiento, `**` con exponente
  negativo).
- **Reducción:** las tres reglas, con paréntesis correctos, y los casos que no
  se reducen (operando que no es identificador simple ni literal).
- **Propiedades sobre los ejemplos de `ejemplos/`:**
  - el código optimizado vuelve a compilar sin errores;
  - optimizar el código ya optimizado devuelve el mismo texto (idempotencia);
  - los valores finales del procedimiento principal calculados por la fase
    intermedia coinciden antes y después de optimizar;
  - un programa con errores no produce optimización.
- **IDE:** tras `compilar()`, la pestaña muestra original y optimizado; con
  errores muestra la omisión.

## Entregables extra

- `ejemplos/optimizacion.ada`: programa con comentarios, espacios sobrantes,
  expresiones plegables y reducibles, y algún caso que no debe tocarse.
- Actualizar `README.md` (estado y estructura).

## Decisiones propias (no confirmadas por el usuario)

- Una sentencia por línea en el optimizado, no todo en una sola línea.
- Espaciado mínimo posible (no se conserva ni un espacio "cosmético").
- La optimización recorre todo el programa, no solo el procedimiento
  principal.
- Float también se pliega; el plegado y la reducción de Float usan `double`,
  igual que el resto del compilador.
- Operandos "simples" para reducción: solo identificador o literal.
- Estructura del árbol estrictamente conservada al regenerar expresiones
  (se corrigió un ejemplo previo que reagrupaba `A * 2`).
- Plegado y reducción actúan sobre subárboles aritméticos maximales dentro de
  cualquier expresión.
- La reducción solo toca identificadores de tipo exactamente `Integer` o
  `Float`; los tipos del usuario con rango quedan intactos.
- Se mantienen juntas las construcciones de varias palabras (`end loop;`,
  `or else`, …) al repartir saltos de línea.

## Pendiente del constructor

- Forma exacta de determinar el rango de tokens de cada expresión cuando hay
  paréntesis externos que el AST no representa (los paréntesis no generan
  nodo en el AST; hay que ampliar el rango hasta ellos o reemplazar solo el
  contenido interno).
- Formato textual exacto de los literales Float generados.
- Tratamiento de literales basados (`16#FF#`) al pliegar: el resultado se
  escribe en decimal.
