# Ejemplos de errores léxicos

Cada archivo aísla **un tipo** de error léxico del subconjunto de Ada.
Sirven para abrirlos en el IDE (menú **Archivo → Abrir…**), pulsar
**Compilar (F5)** y comprobar que el panel **Errores → Léxicos** los
lista todos, con línea y columna, separados de los sintácticos.

El analizador léxico reconoce cinco clases de error, todas con
recuperación (nunca aborta):

| Token trampa / regla | Qué detecta |
|---|---|
| `<LEXEMA_INVALIDO>` | secuencia contigua de letras/dígitos/`_` **y** al menos un carácter ajeno al lenguaje (`@ $ ? \ ! ~ ^ %`, letras acentuadas…). `i@f`, `10$2`, `año` salen como **un solo** token de error, no partidos |
| `<ERROR_LEXICO: ~[]>` | red de seguridad para un carácter suelto que sí es delimitador del alfabeto pero no forma token por sí solo (p. ej. un `#` aislado, fuera de `16#FF#`) |
| `<CADENA_SIN_CERRAR>` | literal de cadena sin la comilla de cierre antes del fin de línea |
| `<CARACTER_MALFORMADO>` | literal de carácter con ≠ 1 carácter entre comillas simples (`'ab'`) |
| `<IDENT_MALFORMADO>` | identificador que termina en `_` o contiene `__` (sin caracteres ajenos) |

`<LEXEMA_INVALIDO>` se declara antes que `<IDENTIFICADOR>` y `<ENTERO>` y,
por ser el emparejamiento más largo, JavaCC lo prefiere: así `i@f` no se
convierte en `i` / `@` / `f`. La secuencia **no** cruza espacios en blanco
ni delimitadores válidos, de modo que `x $ y` sí produce tres tokens
(`x`, `$`, `y`) porque los espacios separan.

## Resultado esperado por archivo

Verificado contra `com.compiladorada.Compilador.analizar` (rama
`feat/front-end-lexico-sintactico`). El número de errores sintácticos
que acompaña es consecuencia de la recuperación en modo pánico y puede
variar; lo que estos ejemplos fijan es la parte **léxica**.

| Archivo | Errores léxicos esperados |
|---|---|
| `01_caracter_no_valido.ada` | 3 — secuencia no válida `$` (8:11), `?` (9:11), `@` (10:11), cada una aislada por espacios |
| `02_cadena_sin_cerrar.ada` | 2 — cadena sin cerrar (5:23) y (8:14). La cadena `"Ada"` de la línea 6 **no** da error |
| `03_caracter_malformado.ada` | 2 — `'ab'` (7:22), `'xyz'` (8:22). El literal `'a'` de la línea 6 **no** da error |
| `04_identificador_doble_guion.ada` | 4 — `total__parcial` y `x__y__z`, cada uno en su declaración y en su uso. `mi_variable` (un solo `_`) **no** da error |
| `05_identificador_termina_en_guion.ada` | 6 — `contador_` y `suma_` en cada aparición. `promedio` **no** da error |
| `06_varios_lexicos.ada` | 10 — cadena sin cerrar, `'ab'`, `dato__x` y `n_` en cada aparición, `#` suelto (12:18, `<ERROR_LEXICO>`) y `\` suelto (13:23, `<LEXEMA_INVALIDO>`) |

### Nota sobre identificadores malformados repetidos

Cuando el identificador malformado está en la zona de declaraciones, el
parser se sincroniza saltando al `begin`, y el lexer vuelve a encontrar
el mismo identificador en cada uso posterior, así que se reporta una vez
por aparición. Es comportamiento correcto: cada ocurrencia es, en sí
misma, un token léxicamente inválido.

## Comprobación rápida desde consola

```bash
# tras 'mvn -q package -DskipTests'
CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"
for f in ejemplos/errores_lexicos/*.ada; do
  echo "== $f =="
  jshell -q --class-path "$CP" -s - <<EOF
var r = com.compiladorada.Compilador.analizar(java.nio.file.Files.readString(java.nio.file.Path.of("$f")), "$f");
r.erroresLexicos().forEach(e -> System.out.println("  " + e.formatear("$f")));
EOF
done
```

O simplemente ábrelos en el IDE y pulsa F5.
