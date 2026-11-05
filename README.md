# Compilador Ada

Compilador e IDE para un subconjunto del lenguaje **Ada**, desarrollado para la
materia **Lenguajes y Autómatas II** (SCD-1016).

El objetivo del semestre es construir un compilador completo sobre las cuatro
unidades del curso (análisis semántico, código intermedio, optimización y código
objeto), tomando como base una etapa léxico-sintáctica que replica —de forma
simplificada— el pipeline real de GNAT/GCC.

## Estado actual

Etapas léxica, sintáctica, semántica, de código intermedio y de optimización, con IDE. Base léxico-sintáctica:

- Editor con resaltado de sintaxis, números de línea e indicador `Ln, Col`.
- Abrir y guardar archivos `.ada`.
- Tabla de tokens (token, tipo, línea, columna).
- Panel de errores **léxicos y sintácticos separados**, con ubicación y mensaje,
  mostrando todos los errores de la compilación.
- Volcado de errores a archivo, actualizado en cada compilación.

Sobre ese front-end ya hay análisis semántico (pestaña **Errores → Semánticos**)
y **generación de código intermedio aritmético** (pestaña **Código intermedio**):
para cada expresión con operadores (en una declaración con valor inicial, una
asignación o un argumento de llamada) del procedimiento principal se muestran
los **cuádruplos** paso a paso (`# | Operador | Arg 1 | Arg 2 | Resultado`), la
notación **prefija** (preorden), la **posfija** (postorden) y el **valor** evaluado con una memoria de variables (Integer de 32 bits, Float).
Solo corre si no hay errores previos; el contenido de `if`/`while`/`for` se
ignora. El reporte también se guarda en `output/codigo_intermedio.txt` (menú **Compilar → Abrir carpeta output/** la abre en el explorador de archivos). Ejemplo:
`ejemplos/codigo_intermedio.ada`.

Con un programa sin errores, la pestaña **Optimizado** (en el panel derecho,
junto a la tabla de tokens) muestra el fuente optimizado, frente al original del
editor (y se guarda en `output/codigo_optimizado.ada`). La
optimización quita comentarios, líneas en blanco, indentación y espacios
innecesarios, y aplica dos técnicas sobre expresiones aritméticas de todo el
programa: **plegado de constantes** (`2 ** 5 + 3` → `35`) y **reducción de
potencia** (`X ** 2` → `X * X`, `X * 2` → `X + X`, `X / 2.0` → `X * 0.5`).
Ejemplo: `ejemplos/optimizacion.ada`.

La siguiente unidad (código objeto) se construye sobre el mismo AST y tabla de
símbolos.

## Stack

- **Java 17** para todas las etapas del compilador.
- **JavaCC** para generar el analizador léxico y sintáctico desde una sola
  gramática (`src/main/javacc/Ada.jjt`).
- **Swing + RSyntaxTextArea** para el IDE.
- **Maven** como sistema de build.

## Requisitos

- JDK 17 o superior
- Maven 3.9+

## Uso

```bash
# Compilar el proyecto (genera el analizador desde la gramática)
mvn -q package

# Ejecutar el IDE
mvn -q exec:java
# o, tras 'mvn -q package':
java -jar target/compilador-ada-0.1.0-SNAPSHOT.jar

# Ejecutar las pruebas
mvn -q test
```

En el IDE: abrir un archivo `.ada` y pulsar **F5** para compilar.

## Estructura

```
src/main/javacc/Ada.jjt          Gramática (léxico + sintáctico)
src/main/java/com/compiladorada/
  lexico/                        Analizador léxico y tipos de token
  sintactico/                    AST y traducción de mensajes
  semantico/                     Tabla de símbolos, tipos y verificación
  intermedio/                    Código intermedio: prefijo, posfijo, cuádruplos, evaluación
  optimizacion/                  Compactación, plegado de constantes y reducción de potencia
  errores/                       Modelo de errores y volcado a archivo
  ide/                           IDE Swing (editor, tablas, paneles)
  Compilador.java                Orquesta el análisis de una fuente
src/test/resources/casos/        Casos válidos e inválidos con salida esperada
ejemplos/                        Programas Ada de ejemplo
docs/                            Documentos de diseño por unidad
```

## Subconjunto de Ada soportado

Estructura de programa (`procedure`, `function`, `package`), variables y
constantes, tipos escalares (`Integer`, `Float`, `Boolean`, `Character`),
`String`, tipos del usuario (`range`, enumerados, `array`, `record`, `subtype`),
literales con base y separadores, operadores completos, control de flujo
(`if/elsif/else`, `for`, `while`), modos de parámetros (`in`, `out`, `in out`) y
manejo de excepciones simplificado. Ver `docs/CLAUDE.md` para el detalle de lo
que entra y lo que queda fuera de alcance.
