package com.compiladorada.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VentanaPrincipalTest {

    @Test
    void compilar_programa_valido_llena_tokens_y_no_muestra_errores(@TempDir Path dir) {
        VentanaPrincipal v = new VentanaPrincipal();
        v.setDirectorioSalida(dir);
        v.getEditor().setTexto("procedure P is begin null; end;");

        v.compilar();

        assertTrue(v.getTablaTokens().getFilas() > 0);
        assertEquals(0, v.getPanelErrores().getFilasLexicas());
        assertEquals(0, v.getPanelErrores().getFilasSintacticas());
        assertTrue(Files.exists(dir.resolve("tokens.txt")));
        assertTrue(Files.exists(dir.resolve("errores_lexicos.txt")));
        assertTrue(Files.exists(dir.resolve("errores_sintacticos.txt")));
    }

    @Test
    void compilar_programa_con_error_sintactico_lo_muestra(@TempDir Path dir) {
        VentanaPrincipal v = new VentanaPrincipal();
        v.setDirectorioSalida(dir);
        // léxicamente limpio, falta un ';'
        v.getEditor().setTexto("procedure P is X : Integer begin null; end;");

        v.compilar();

        assertEquals(0, v.getPanelErrores().getFilasLexicas());
        assertTrue(v.getPanelErrores().getFilasSintacticas() > 0);
    }

    @Test
    void con_errores_lexicos_omite_el_sintactico(@TempDir Path dir) throws Exception {
        VentanaPrincipal v = new VentanaPrincipal();
        v.setDirectorioSalida(dir);
        v.getEditor().setTexto("procedure P is X : Integer $ begin null end;");

        v.compilar();

        assertTrue(v.getPanelErrores().getFilasLexicas() > 0);
        assertEquals(0, v.getPanelErrores().getFilasSintacticas());
        String sint = Files.readString(dir.resolve("errores_sintacticos.txt"));
        assertTrue(sint.contains("OMITIDO"), "el archivo sintáctico debe indicar que se omitió: " + sint);
    }

    @Test
    void el_menu_tiene_las_secciones_requeridas() {
        VentanaPrincipal v = new VentanaPrincipal();
        java.util.List<String> menus = new java.util.ArrayList<>();
        for (int i = 0; i < v.getJMenuBar().getMenuCount(); i++) {
            menus.add(v.getJMenuBar().getMenu(i).getText());
        }
        assertEquals(java.util.List.of("Archivo", "Editar", "Compilar", "Ver", "Ayuda"), menus);
    }
}
