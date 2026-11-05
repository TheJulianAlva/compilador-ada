# Retirar el intermedio optimizado y la validación Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el programa muestre un solo código intermedio (el del fuente original), sin selector ni validación contra una versión optimizada; eliminar la rama de prueba; y asegurar que cada compilación escriba el fuente optimizado en un archivo.

**Architecture:** La optimización queda como está en `main` (sobre el fuente: compactación, plegado de constantes y reducción de potencia). Se deshace por completo el commit `e6aeaf8` ("código intermedio del optimizado y validación"), que ya está en `origin/main`, con un `git revert` (sin reescribir historia). Después se limpia la documentación del repositorio y de la bóveda y se borra la rama local `feat/optimizacion-en-codigo-intermedio`.

**Tech Stack:** Java 17, Swing, JUnit 5, Maven, Git.

**Spec:** `docs/superpowers/specs/2026-10-28-optimizacion-design.md` (se le quita la sección "Validación con el código intermedio").

## Global Constraints

- `Compilador.analizar` vuelve a generar **un solo** código intermedio (`GeneradorIntermedio.generar(ast)`, del fuente original) y la optimización vuelve a ser `Optimizador.optimizar(fuente, tokens, ast)`; el texto optimizado **no** se vuelve a analizar.
- `ResultadoOptimizacion` vuelve a ser `record(String original, String optimizado)`; desaparecen `Validacion`, `validar(...)` y los campos `intermedio` y `validacion`.
- `PanelIntermedio` vuelve a tener `setResultado(ResultadoIntermedio)` y ningún selector.
- `EscritorOptimizacion` escribe solo `output/codigo_optimizado.ada`; no existe `output/codigo_intermedio_optimizado.txt`.
- Cada compilación sobrescribe `output/codigo_optimizado.ada` con el fuente optimizado (o `Optimización OMITIDA: …` si hay errores).
- Los commits no llevan línea `Co-Authored-By`.
- Se conserva la carpeta inicial de los diálogos de archivo, la pestaña *Optimizado* junto a *Tokens* y todo lo demás de `main`.

## Review Focus

- Compilar un programa con errores: el panel de código intermedio queda omitido y `codigo_optimizado.ada` dice `Optimización OMITIDA`.
- Compilar dos veces seguidas: el archivo se sobrescribe y no conserva texto de la compilación anterior.
- Compilar un programa sin guardar (el editor sin ruta): el archivo se escribe igual en `output/`.
- Ningún texto de la interfaz, de los reportes ni de la documentación menciona ya el selector, la validación ni `codigo_intermedio_optimizado.txt`.
- Los `ejemplos/*.ada` siguen cumpliendo: el optimizado compila sin errores, optimizarlo otra vez da el mismo texto y los valores finales del intermedio no cambian.

---

### Task 1: Deshacer el intermedio optimizado y la validación

**Files:**
- Revert: `e6aeaf8` (afecta `Compilador.java`, `ResultadoOptimizacion.java`, `EscritorOptimizacion.java`, `PanelIntermedio.java`, `VentanaPrincipal.java`, `Validacion.java`, `ValidacionTest.java`, `VentanaPrincipalTest.java`, `OptimizadorTest.java`, `README.md`, spec de optimización)
- Test: `src/test/java/com/compiladorada/optimizacion/OptimizadorTest.java`, `src/test/java/com/compiladorada/ide/VentanaPrincipalTest.java`

**Interfaces:**
- Produces: `Compilador.analizar(String, String) → ResultadoCompilacion` con `intermedio()` del original y `optimizacion()` con `(original, optimizado)`; `PanelIntermedio.setResultado(ResultadoIntermedio)`.

- [ ] **Step 1:** `git revert --no-commit e6aeaf8` y resolver los conflictos que haya con `43a8bd8` (solo pueden estar en `README.md` y en el spec de optimización).
- [ ] **Step 2:** Confirmar que desaparecen `Validacion.java` y `ValidacionTest.java` y que `ResultadoOptimizacion` y `PanelIntermedio` quedan como antes de `e6aeaf8`.
- [ ] **Step 3:** `mvn -q test`; esperado: sin fallos. Las pruebas del selector y de `codigo_intermedio_optimizado.txt` ya no existen tras el revert.

### Task 2: Archivo con el fuente optimizado en cada compilación

**Files:**
- Modify (si hace falta): `src/main/java/com/compiladorada/optimizacion/EscritorOptimizacion.java`
- Test: `src/test/java/com/compiladorada/ide/VentanaPrincipalTest.java`

**Interfaces:**
- Consumes: `EscritorOptimizacion.volcar(ResultadoCompilacion, Path)`, `VentanaPrincipal.compilar()`.
- Produces: `output/codigo_optimizado.ada` en cada `compilar()`.

- [ ] **Step 1:** Prueba `compilar_dos_veces_sobrescribe_codigo_optimizado`: compilar `B := A * 2;`, luego un programa distinto, y comprobar que el archivo contiene solo el segundo optimizado.
- [ ] **Step 2:** Prueba `compilar_sin_guardar_escribe_codigo_optimizado` (editor sin ruta) y mantener `con_errores_la_pestana_optimizado_avisa_y_el_archivo_lo_indica`.
- [ ] **Step 3:** Ejecutar las pruebas. Si ya pasan, `EscritorOptimizacion` no cambia: el archivo ya se escribe en `main`. **Pendiente de confirmar con el usuario** si además quiere una copia junto al archivo `.ada` abierto (por ejemplo `programa_optimizado.ada`); en ese caso se añade a `VentanaPrincipal.compilar()` solo cuando el editor tenga ruta.

### Task 3: Documentación del repositorio

**Files:**
- Modify: `README.md`, `docs/unidad-0-diseno-ide.md`, `docs/superpowers/specs/2026-10-13-codigo-intermedio-design.md`, `docs/superpowers/specs/2026-10-28-optimizacion-design.md`, `docs/superpowers/plans/2026-10-28-optimizacion.md`, `docs/img/README.md`, `docs/CLAUDE.md` (ignorado por git; se edita en disco)

- [ ] **Step 1:** Quitar toda mención al selector *Original / Optimizado*, a la validación y a `codigo_intermedio_optimizado.txt` (`grep -rn "alidaci\|Original / Optimizado\|intermedio_optimizado" README.md docs` debe quedar sin coincidencias relevantes).
- [ ] **Step 2:** En `docs/unidad-0-diseno-ide.md` actualizar §6 (clases), §7.1 (pestaña *Código intermedio* sin selector), §7.4 (tabla de `output/`), §8.2 (conteo y tabla de pruebas, sin `ValidacionTest`) y §9.2; en `docs/img/README.md` ajustar la captura `04-codigo-intermedio.png`.
- [ ] **Step 3:** En el spec de optimización eliminar la sección "Validación con el código intermedio"; en el plan `2026-10-28-optimizacion.md` eliminar el párrafo "Validación con el código intermedio" de "Cambios posteriores"; en el spec `2026-10-13-codigo-intermedio-design.md` eliminar la actualización "Selector Original / Optimizado".
- [ ] **Step 4:** `docs/CLAUDE.md`: quitar de la etapa 3 y 4 la mención a generar el intermedio del optimizado y a la validación.

### Task 4: Bóveda de Obsidian

**Files:**
- Modify: `01-Proyectos/compilador-ada/{compilador-ada-info.md, bitacora.md, etapas/etapa-3-generacion-codigo-intermedio.md, etapas/etapa-4-optimizacion.md, decisiones/codigo-intermedio-del-original-y-del-optimizado.md}`, `02-Areas/school/7th/lenguajes-automatas-ii/entregables/documentacion-del-proyecto.md` (solo si menciona la validación)

- [ ] **Step 1:** Marcar `decisiones/codigo-intermedio-del-original-y-del-optimizado.md` con `estado: superada` y una nota breve: el programa muestra un solo código intermedio (el del original) y se retiró la validación.
- [ ] **Step 2:** Quitar de las etapas 3 y 4 el entregable de intermedio del optimizado y de validación, y sus enlaces a la decisión; actualizar el índice del proyecto.
- [ ] **Step 3:** Añadir a la bitácora una entrada del 2026-11-03: se retira el intermedio optimizado y la validación, se elimina la rama de prueba y el programa genera el archivo del fuente optimizado en cada compilación.

### Task 5: Cerrar: rama, build y commit

- [ ] **Step 1:** `mvn -q clean test`; esperado: sin fallos; anotar el nuevo conteo de pruebas y usarlo en `docs/unidad-0-diseno-ide.md` §8.2.
- [ ] **Step 2:** Un único commit en `main` (continúa el `git revert`), sin `Co-Authored-By`, con los cambios de código y documentación del repositorio.
- [ ] **Step 3:** Borrar la rama de prueba: `git branch -D feat/optimizacion-en-codigo-intermedio` (solo existe en local; `-D` porque no está unida a `main`; su único commit, `1a64339`, quedaría solo en el `reflog`). Verificar con `git branch -a`.
- [ ] **Step 4:** `git status` limpio y `git log --oneline -3`; no se hace `push`.
