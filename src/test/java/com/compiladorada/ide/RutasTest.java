package com.compiladorada.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RutasTest {

    @Test
    void documentos_es_un_directorio_existente() {
        assertTrue(Files.isDirectory(Rutas.carpetaDocumentos()));
    }

    @Test
    void ejemplos_se_resuelve_con_la_propiedad_de_instalacion(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("ejemplos"));
        System.setProperty(Rutas.PROP_HOME, dir.toString());
        try {
            assertEquals(dir.resolve("ejemplos").toAbsolutePath(), Rutas.carpetaEjemplos().orElseThrow());
        } finally {
            System.clearProperty(Rutas.PROP_HOME);
        }
    }

    @Test
    void ejemplos_se_encuentra_desde_el_directorio_del_proyecto() {
        assertTrue(Rutas.carpetaEjemplos().isPresent());
    }
}
