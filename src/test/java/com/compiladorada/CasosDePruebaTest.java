package com.compiladorada;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CasosDePruebaTest {

    private static final Path VALIDOS = Path.of("src/test/resources/casos/validos");
    private static final Path INVALIDOS = Path.of("src/test/resources/casos/invalidos");

    @TestFactory
    Stream<DynamicTest> casos_validos_sin_errores() throws IOException {
        List<Path> archivos = Files.list(VALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .sorted()
                .toList();
        assertTrue(archivos.size() >= 8,
                "esperados >= 8 casos válidos, encontrados " + archivos.size());
        return archivos.stream()
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    ResultadoCompilacion r = Compilador.analizar(Files.readString(p),
                            p.getFileName().toString());
                    assertTrue(r.erroresLexicos().isEmpty(),
                            "léxicos inesperados: " + r.erroresLexicos());
                    assertTrue(r.erroresSintacticos().isEmpty(),
                            "sintácticos inesperados: " + r.erroresSintacticos());
                }));
    }

    @TestFactory
    Stream<DynamicTest> casos_invalidos_contienen_los_errores_esperados() throws IOException {
        List<Path> archivos = Files.list(INVALIDOS)
                .filter(p -> p.toString().endsWith(".ada"))
                .sorted()
                .toList();
        assertTrue(archivos.size() >= 10,
                "esperados >= 10 casos inválidos, encontrados " + archivos.size());
        return archivos.stream()
                .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
                    ResultadoCompilacion r = Compilador.analizar(Files.readString(p),
                            p.getFileName().toString());
                    List<ErrorCompilacion> todos = new java.util.ArrayList<>();
                    todos.addAll(r.erroresLexicos());
                    todos.addAll(r.erroresSintacticos());

                    Path esperado = p.resolveSibling(
                            p.getFileName().toString().replace(".ada", ".expected"));
                    List<String> esperados = Files.readAllLines(esperado).stream()
                            .filter(l -> !l.isBlank()).toList();
                    for (String linea : esperados) {
                        if (linea.isBlank()) continue;
                        String[] pt = linea.trim().split(":");
                        int ln = Integer.parseInt(pt[0]);
                        int col = Integer.parseInt(pt[1]);
                        ErrorCompilacion.Categoria cat = ErrorCompilacion.Categoria.valueOf(pt[2]);
                        assertTrue(
                                todos.stream().anyMatch(e -> e.linea() == ln
                                        && e.columna() == col && e.categoria() == cat),
                                "falta el error " + linea + " en " + p.getFileName()
                                        + "; errores reales: " + todos);
                    }
                    assertEquals(esperados.size(), todos.size(),
                            () -> "errores extra en " + p.getFileName() + ": " + todos);
                }));
    }
}
