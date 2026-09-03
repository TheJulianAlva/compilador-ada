package com.compiladorada.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EditorPanelTest {

    @Test
    void nuevo_editor_no_esta_modificado_y_tiene_nombre_por_defecto() {
        EditorPanel e = new EditorPanel();
        assertFalse(e.isModificado());
        assertEquals("fuente_sin_guardar.ada", e.getNombreArchivo());
    }

    @Test
    void set_texto_y_get_texto() {
        EditorPanel e = new EditorPanel();
        e.setTexto("procedure P is begin null; end;");
        assertEquals("procedure P is begin null; end;", e.getTexto());
    }

    @Test
    void set_texto_programatico_no_marca_modificado() {
        EditorPanel e = new EditorPanel();
        e.setTexto("procedure P is begin null; end;");
        assertFalse(e.isModificado());
    }

    @Test
    void abrir_carga_contenido_y_nombre(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("demo.ada");
        Files.writeString(f, "procedure Demo is begin null; end;");
        EditorPanel e = new EditorPanel();
        e.abrir(f);
        assertEquals("procedure Demo is begin null; end;", e.getTexto());
        assertEquals("demo.ada", e.getNombreArchivo());
        assertFalse(e.isModificado());
        assertTrue(e.getRutaArchivo().isPresent());
    }

    @Test
    void guardar_persiste_y_limpia_el_flag_modificado(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("out.ada");
        EditorPanel e = new EditorPanel();
        e.getTextArea().replaceSelection("procedure X is begin null; end;");
        assertTrue(e.isModificado());
        e.guardarComo(f);
        assertEquals("procedure X is begin null; end;", Files.readString(f));
        assertFalse(e.isModificado());
        e.guardar();
        assertFalse(e.isModificado());
    }

    @Test
    void nuevo_limpia_estado(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("demo.ada");
        Files.writeString(f, "abc");
        EditorPanel e = new EditorPanel();
        e.abrir(f);
        e.nuevo();
        assertEquals("", e.getTexto());
        assertEquals("fuente_sin_guardar.ada", e.getNombreArchivo());
        assertFalse(e.isModificado());
        assertTrue(e.getRutaArchivo().isEmpty());
    }
}
