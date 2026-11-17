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

## Capturas a añadir (5)

| Archivo | Qué debe mostrar |
|---|---|
| `01-editor-resaltado.png` | El editor (panel izquierdo) con un programa Ada de ejemplo cargado —por ejemplo `src/test/resources/casos/validos/07_expresiones.ada`— visible el resaltado de sintaxis (palabras reservadas, comentarios `--`, cadenas y números coloreados) y el *gutter* de números de línea a la izquierda. En la barra de estado debe verse `Ln x, Col y`. |
| `02-tabla-tokens.png` | Tras pulsar **F5** (Compilar) con un programa válido cargado: el panel derecho "Tabla de tokens" poblado, con las columnas *Token · Tipo · Línea · Columna* y filas de varios tipos (`PALABRA_RESERVADA`, `IDENTIFICADOR`, `ENTERO`, `DELIMITADOR_SIMPLE`, `COMENTARIO`, …). |
| `03-panel-errores.png` | Tras compilar `ejemplos/nomina_errores.ada`: el panel inferior de errores, pestañas **Léxicos (1)**, luego **Sintácticos (2)** (comentando `[L1]`) y **Semánticos (5)** (comentando `[P1]` y `[P2]`), con las filas `Ln:Col | Mensaje`. |
| `04-codigo-intermedio.png` | Tras compilar `ejemplos/nomina_completo.ada`: la pestaña inferior **Código intermedio** con la lista de expresiones, la tabla de cuádruplos y, al pie, los valores finales. Opcional: `ejemplos/nomina_errores.ada` con las líneas `[L1]`, `[P]` y `[S]` comentadas, para mostrar los errores de evaluación `[I1]`..`[I4]`. |
| `05-optimizado.png` | Tras compilar `ejemplos/nomina_completo.ada`: el editor a la izquierda y, a la derecha, la pestaña **Optimizado** con el código compactado. |

Guardar los PNG con exactamente esos nombres para que los enlaces del documento
funcionen.
