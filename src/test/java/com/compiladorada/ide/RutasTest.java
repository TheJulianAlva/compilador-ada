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
    void carpeta_de_trabajo_se_lee_del_archivo_de_configuracion(@TempDir Path dir) throws Exception {
        Path trabajo = dir.resolve("mi trabajo");
        Path config = dir.resolve("config.properties");
        Files.writeString(config, "carpetaTrabajo=" + trabajo.toString().replace("\\", "\\\\") + "\n");
        System.setProperty(Rutas.PROP_CONFIG, config.toString());
        try {
            assertEquals(trabajo.toAbsolutePath(), Rutas.carpetaTrabajo());
            assertEquals(trabajo.toAbsolutePath().resolve("output"), Rutas.carpetaSalida());
        } finally {
            System.clearProperty(Rutas.PROP_CONFIG);
        }
    }

    @Test
    void sin_configuracion_la_carpeta_de_trabajo_esta_en_documentos(@TempDir Path dir) {
        System.setProperty(Rutas.PROP_CONFIG, dir.resolve("no-existe.properties").toString());
        try {
            assertEquals(Rutas.carpetaDocumentos().resolve("CompiladorAda"), Rutas.carpetaTrabajo());
        } finally {
            System.clearProperty(Rutas.PROP_CONFIG);
        }
    }

    @Test
    void preparar_crea_estructura_y_copia_los_ejemplos(@TempDir Path dir) throws Exception {
        Path trabajo = dir.resolve("trabajo");
        Path config = dir.resolve("config.properties");
        Files.writeString(config, "carpetaTrabajo=" + trabajo.toString().replace("\\", "\\\\") + "\n");
        System.setProperty(Rutas.PROP_CONFIG, config.toString());
        try {
            Rutas.prepararCarpetaTrabajo();
            assertTrue(Files.isDirectory(trabajo.resolve("output")));
            assertTrue(Files.isDirectory(trabajo.resolve("proyectos")));
            assertTrue(Files.isRegularFile(trabajo.resolve("ejemplos").resolve("nomina_completo.ada")));
        } finally {
            System.clearProperty(Rutas.PROP_CONFIG);
        }
    }

    @Test
    void ejemplos_se_encuentra_desde_el_directorio_del_proyecto() {
        assertTrue(Rutas.carpetaEjemplos().isPresent());
    }
}
