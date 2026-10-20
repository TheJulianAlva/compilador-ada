# Generación de código intermedio — Diseño

Fecha: 2026-10-13

## Objetivo

Agregar al compilador una fase de generación de código intermedio **solo para
operaciones aritméticas**. Para cada expresión aritmética del programa se
muestra su notación **prefija**, su notación **posfija**, los **cuádruplos**
(código de tres direcciones), el método usado para obtenerlos y el **valor
resultante**, de modo que se pueda validar que la operación se hizo bien.
Funciona con literales y con identificadores (variables o constantes)
numéricos, respetando la compatibilidad de tipos.

## Alcance

- La fase corre después del análisis semántico y **solo si no hay errores
  léxicos, sintácticos ni semánticos** (misma regla secuencial que
  `Compilador`).
- Se procesan, en orden de aparición:
  - asignaciones (`X := A + B * 2;`),
  - inicializadores de declaraciones (`C : constant Integer := 3 * 4;`),
  - argumentos aritméticos de llamadas tipo `Put_Line`.
- Operaciones: `+ - * / mod rem ** abs`, signo unario y paréntesis.
- Operandos: literales numéricos e identificadores de tipo numérico
  (`Integer`, `Float` y subtipos numéricos).
- **Fuera de alcance:** el contenido de `if`, `while` y `for` se ignora por
  completo; expresiones lógicas, de cadenas o con llamadas a funciones se
  omiten; no hay intérprete de control de flujo.

## Componentes (paquete `com.compiladorada.intermedio`)

| Unidad | Responsabilidad |
|---|---|
| `ArbolExpresion` | Árbol pequeño (`Operando`, `Binario`, `Unario`) construido desde los nodos del AST (`Simple`, `Termino`, `Factor`, `Literal`, `Nombre`). |
| `Notaciones` | Prefijo = recorrido en preorden; posfijo = recorrido en postorden. |
| `GeneradorCuadruplos` | Mismo postorden; emite pasos numerados `# | operador | arg1 | arg2 | resultado` con temporales `T1, T2…`, reiniciados en cada expresión. No se emiten asignaciones. |
| `Evaluador` | Mantiene memoria `nombre → valor` y calcula cada expresión. |
| `ResultadoIntermedio` | Filas (línea, fuente, prefijo, posfijo, cuádruplos, valor o error), memoria final y descripción de métodos. |

`ResultadoCompilacion` gana un campo `intermedio` (vacío/nulo si la fase se
omitió). Se agrega un `intermedioOmitido()` análogo a `semanticoOmitido()`.

## Reglas de tipos y evaluación

- La compatibilidad de tipos viene del análisis semántico (`VerificadorSemantico`,
  `TipoAda`); la fase no duplica reglas. Una expresión con tipos
  incompatibles que llegue aquí se marca como no generada.
- `Integer op Integer` → `Integer` (división truncada hacia cero; `mod`/`rem`
  con semántica de Ada). `Float` usa `double`.
- División entre cero: error de evaluación en esa fila; la variable destino
  queda sin valor y las expresiones que la usen se marcan "no evaluado".
- Variable sin valor inicial: indefinida; usarla se reporta en la fila, no se
  asume cero.
- Las constantes con valor inicial se evalúan como las variables.

## IDE y salidas

- Nueva pestaña **"Código intermedio"**: tabla con una fila por expresión;
  debajo, sección "Métodos utilizados" (preorden/postorden/cuádruplos) y
  sección "Valores finales" con la memoria al terminar el programa.
- Reporte de texto `output/codigo_intermedio.txt`, junto a los volcados de
  errores existentes.
- Ejemplo nuevo `ejemplos/codigo_intermedio.ada` (Integer, Float,
  constantes y un caso de error de tipos).

## Pruebas

- Unitarias de prefijo, posfijo y cuádruplos con expresiones conocidas
  (`A + B * 2`, `(A + B) * (C - D)`): precedencia, `**`, `abs`, signo unario,
  `mod`/`rem`, división entera y Float, división entre cero.
- Integración: compilar un `.ada` completo y comprobar filas y memoria final.
- La fase no genera nada si hay errores previos; el contenido de
  `if`/`while`/`for` se ignora.
- Prueba de IDE: la pestaña muestra las filas tras `compilar()`.

## Decisiones tomadas

- Recorrido del AST en un paso nuevo (no acciones embebidas en `Ada.jjt`, no
  shunting-yard sobre tokens) para reutilizar la precedencia del parser y
  mantener la fase testeable de forma aislada.
- Entregables de salida: prefijo, posfijo y cuádruplos (confirmado por el
  usuario), valores evaluados con memoria acumulativa (confirmado).

## Revisión (2026-10-19, indicada por la profesora)

- No se muestran asignaciones: solo las expresiones con al menos un operador
  generan bloque (las asignaciones simples solo actualizan la memoria).
- Cada bloque: título con la sentencia completa, tabla de cuádruplos,
  prefijo, posfijo y resultado final.
- La pestaña del IDE es maestro-detalle (lista de expresiones + tabla de la
  seleccionada).
