package com.compiladorada.semantico;

import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.Compilador;
import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.generado.AdaParser;
import com.compiladorada.semantico.visitor.RecolectorDeclaraciones;
import com.compiladorada.semantico.visitor.VerificadorUsos;
import com.compiladorada.sintactico.nodos.SimpleNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre cada caso .ada/.expected de casos/semanticos contra AMBAS
 * implementaciones (A: Compilador.analizar, embebida en el parseo; B:
 * RecolectorDeclaraciones + VerificadorUsos, visitor de dos pasadas) y
 * exige que las dos coincidan exactamente con lo esperado — esa doble
 * ejecución es la comparación que pide la materia (ver spec, sección 5).
 */
class CasosSemanticosTest {

    private static final Path VALIDOS = Path.of("src/test/resources/casos/semanticos/validos");
    private static final Path INVALIDOS = Path.of("src/test/resources/casos/semanticos/invalidos");

    private List<ErrorCompilacion> conB(String fuente) throws Exception {
        AdaParser parser = new AdaParser(new StringReader(fuente));
        SimpleNode raiz = parser.programa();
        // Igual disciplina que Compilador.analizar para Implementación A: si
        // hubo errores sintácticos, el AST puede tener nodos con valor null
        // (recuperación de errores a mitad de parseo), y RecolectorDeclaraciones/
        // VerificadorUsos pueden lanzar NullPointerException sobre ese árbol
        // mal formado. Ninguno de los casos de este corpus debería tener
        // errores sintácticos, pero esta guarda es defensiva, no un requisito
        // de corpus (ver Task 9, review).
        if (!parser.getErroresSintacticos().isEmpty()) {
            return List.of();
        }
        RecolectorDeclaraciones pasada1 = RecolectorDeclaraciones.recolectar(raiz);
        VerificadorUsos.verificar(raiz, pasada1);
        return pasada1.verificador().errores();
    }

    @TestFactory
    Stream<DynamicTest> casos_validos_no_reportan_errores_en_ninguna_implementacion() throws IOException {
        List<Path> archivos = Files.list(VALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .sorted()
                .toList();
        assertTrue(archivos.size() >= 2, "esperados >= 2 casos válidos, encontrados " + archivos.size());
        return archivos.stream()
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    String fuente = Files.readString(p);
                    ResultadoCompilacion r = Compilador.analizar(fuente, p.getFileName().toString());
                    assertTrue(r.erroresLexicos().isEmpty(), "léxicos inesperados: " + r.erroresLexicos());
                    assertTrue(r.erroresSintacticos().isEmpty(), "sintácticos inesperados: " + r.erroresSintacticos());
                    assertTrue(r.erroresSemanticos().isEmpty(),
                            "semánticos inesperados (A): " + r.erroresSemanticos());
                    assertTrue(conB(fuente).isEmpty(), "semánticos inesperados (B): " + conB(fuente));
                }));
    }

    @TestFactory
    Stream<DynamicTest> casos_invalidos_reportan_lo_esperado_en_ambas_implementaciones() throws IOException {
        List<Path> archivos = Files.list(INVALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .sorted()
                .toList();
        assertTrue(archivos.size() >= 5, "esperados >= 5 casos inválidos, encontrados " + archivos.size());
        return archivos.stream()
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    String fuente = Files.readString(p);
                    Path esperado = p.resolveSibling(p.getFileName().toString().replace(".ada", ".expected"));
                    List<String> esperados = Files.readAllLines(esperado).stream()
                            .filter(l -> !l.isBlank()).toList();

                    ResultadoCompilacion r = Compilador.analizar(fuente, p.getFileName().toString());
                    assertTrue(r.erroresLexicos().isEmpty(), "léxicos inesperados: " + r.erroresLexicos());
                    assertTrue(r.erroresSintacticos().isEmpty(), "sintácticos inesperados: " + r.erroresSintacticos());
                    assertCoincideConEsperado(esperados, r.erroresSemanticos(), p.getFileName() + " (A)");

                    List<ErrorCompilacion> deB = conB(fuente);
                    assertCoincideConEsperado(esperados, deB, p.getFileName() + " (B)");

                    assertMismoTextoDeMensajes(r.erroresSemanticos(), deB, p.getFileName().toString());
                }));
    }

    // Más allá de coincidir con el .expected (que solo exige linea+categoria),
    // en todos los casos explorados A y B producen mensajes byte-idénticos
    // para el mismo error lógico — un invariante real que vale la pena
    // proteger. A y B pueden reportar el mismo conjunto de errores en ORDEN
    // distinto (difieren en qué recorren primero), así que ambas listas se
    // ordenan por (linea, mensaje) antes de comparar mensaje a mensaje —
    // de lo contrario esta aserción dependería espuriamente del orden.
    private void assertMismoTextoDeMensajes(List<ErrorCompilacion> erroresA, List<ErrorCompilacion> erroresB,
                                             String etiqueta) {
        assertEquals(erroresA.size(), erroresB.size(),
                () -> "cantidad de errores distinta entre A y B en " + etiqueta
                        + "; A=" + erroresA + " B=" + erroresB);
        Comparator<ErrorCompilacion> orden = Comparator.comparingInt(ErrorCompilacion::linea)
                .thenComparing(ErrorCompilacion::mensaje);
        List<ErrorCompilacion> ordenadosA = erroresA.stream().sorted(orden).toList();
        List<ErrorCompilacion> ordenadosB = erroresB.stream().sorted(orden).toList();
        for (int i = 0; i < ordenadosA.size(); i++) {
            assertEquals(ordenadosA.get(i).mensaje(), ordenadosB.get(i).mensaje(),
                    () -> "mensaje distinto entre A y B en " + etiqueta
                            + "; A=" + ordenadosA + " B=" + ordenadosB);
        }
    }

    // A ancla los errores dentro de una expresión binaria/condición en el
    // token del OPERADOR (las acciones embebidas disparan al consumirlo); B
    // ancla en node.jjtGetFirstToken(), el inicio de toda la sub-expresión
    // (es lo que reciben los parámetros de posición del núcleo compartido
    // VerificadorSemantico desde el visitor). Es una diferencia real y
    // permanente entre cómo cada implementación reporta la posición para el
    // mismo error lógico — no un bug a corregir (ver Task 9, review) — así
    // que esta comparación ignora deliberadamente `columna` y solo exige
    // coincidencia en `linea` y `categoria`.
    private void assertCoincideConEsperado(List<String> esperados, List<ErrorCompilacion> reales, String etiqueta) {
        for (String linea : esperados) {
            String[] pt = linea.trim().split(":");
            int ln = Integer.parseInt(pt[0]);
            ErrorCompilacion.Categoria cat = ErrorCompilacion.Categoria.valueOf(pt[2]);
            assertTrue(
                    reales.stream().anyMatch(e -> e.linea() == ln && e.categoria() == cat),
                    "falta el error " + linea + " en " + etiqueta + "; errores reales: " + reales);
        }
        assertEquals(esperados.size(), reales.size(), () -> "errores extra en " + etiqueta + ": " + reales);
    }
}
