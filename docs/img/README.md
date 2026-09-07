# Capturas del IDE — Unidad 0

Este directorio contiene las capturas de pantalla referenciadas desde
`docs/unidad-0-diseno-ide.md` (sección 7.5). El entorno de build no tiene
display, así que hay que tomarlas a mano.

## Cómo ejecutar el IDE

```bash
mvn -q exec:java
# o, tras 'mvn -q package':
java -jar target/compilador-ada-0.1.0-SNAPSHOT.jar
```

## Capturas a añadir (3)

| Archivo | Qué debe mostrar |
|---|---|
| `01-editor-resaltado.png` | El editor (panel izquierdo) con un programa Ada de ejemplo cargado —por ejemplo `src/test/resources/casos/validos/07_expresiones.ada`— visible el resaltado de sintaxis (palabras reservadas, comentarios `--`, cadenas y números coloreados) y el *gutter* de números de línea a la izquierda. En la barra de estado debe verse `Ln x, Col y`. |
| `02-tabla-tokens.png` | Tras pulsar **F5** (Compilar) con un programa válido cargado: el panel derecho "Tabla de tokens" poblado, con las columnas *Token · Tipo · Línea · Columna* y filas de varios tipos (`PALABRA_RESERVADA`, `IDENTIFICADOR`, `ENTERO`, `DELIMITADOR_SIMPLE`, `COMENTARIO`, …). |
| `03-panel-errores.png` | Tras compilar `ejemplos/errores_sintacticos.ada`: el panel inferior de errores, pestaña **Sintácticos (m)** con varias filas `Ln:Col | Mensaje`. Opcionalmente una segunda captura con `ejemplos/errores_lexicos_y_sintacticos.ada` para mostrar la pestaña **Léxicos (n)** poblada y **Sintácticos (omitido)** (el análisis sintáctico no corre mientras haya errores léxicos). |

Guardar los PNG con exactamente esos nombres para que los enlaces del documento
funcionen.
