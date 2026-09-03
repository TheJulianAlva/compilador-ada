# Compilador Ada

Compilador e IDE para un subconjunto del lenguaje **Ada**, desarrollado para la
materia **Lenguajes y Autómatas II** (SCD-1016).

El objetivo del semestre es construir un compilador completo sobre las cuatro
unidades del curso (análisis semántico, código intermedio, optimización y código
objeto), tomando como base una etapa léxico-sintáctica que replica —de forma
simplificada— el pipeline real de GNAT/GCC.

## Estado actual

Etapa **léxica y sintáctica** con IDE:

- Editor con resaltado de sintaxis, números de línea e indicador `Ln, Col`.
- Abrir y guardar archivos `.ada`.
- Tabla de tokens (token, tipo, línea, columna).
- Panel de errores **léxicos y sintácticos separados**, con ubicación y mensaje,
  mostrando todos los errores de la compilación.
- Volcado de errores a archivo, actualizado en cada compilación.

Las siguientes unidades (semántico, intermedio, optimización, objeto) se
construyen sobre el mismo AST y tabla de símbolos que produce esta etapa.

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
