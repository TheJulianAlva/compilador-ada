package com.compiladorada.ide;

import javax.swing.filechooser.FileSystemView;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolución de carpetas del sistema: Documentos del usuario y ejemplos que viajan con el programa. */
public final class Rutas {

    /** Propiedad de sistema opcional que apunta a la carpeta de instalación. */
    public static final String PROP_HOME = "compilador.home";

    /** Propiedad de sistema opcional que fija la ruta del archivo de configuración (pruebas). */
    public static final String PROP_CONFIG = "compilador.config";

    /** Clave del archivo de configuración con la carpeta de trabajo elegida en el instalador. */
    public static final String CLAVE_CARPETA_TRABAJO = "carpetaTrabajo";

    /** Nombre de la carpeta de trabajo por defecto dentro de Documentos. */
    public static final String NOMBRE_CARPETA_TRABAJO = "CompiladorAda";

    private Rutas() {
    }

    /**
     * Archivo de configuración del usuario: {@code %APPDATA%\CompiladorAda\config.properties}
     * (o {@code ~/.compiladorada/config.properties} si no hay APPDATA). Lo escribe el instalador.
     */
    public static Path archivoConfig() {
        String fijo = System.getProperty(PROP_CONFIG);
        if (fijo != null && !fijo.isBlank()) {
            return Path.of(fijo);
        }
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isBlank()) {
            return Path.of(appdata, NOMBRE_CARPETA_TRABAJO, "config.properties");
        }
        return Path.of(System.getProperty("user.home", "."), ".compiladorada", "config.properties");
    }

    /**
     * Carpeta de trabajo del usuario (contiene {@code output/}, {@code ejemplos/} y {@code proyectos/}).
     * Se toma de {@code carpetaTrabajo} en el archivo de configuración; si falta o es inválida,
     * cae a {@code Documentos/CompiladorAda}. No crea nada en disco.
     */
    public static Path carpetaTrabajo() {
        Path config = archivoConfig();
        if (Files.isRegularFile(config)) {
            try (var in = Files.newBufferedReader(config, java.nio.charset.StandardCharsets.UTF_8)) {
                var props = new java.util.Properties();
                props.load(in);
                String valor = props.getProperty(CLAVE_CARPETA_TRABAJO);
                if (valor != null && !valor.isBlank()) {
                    return Path.of(valor.trim()).toAbsolutePath();
                }
            } catch (java.io.IOException | RuntimeException ignore) {
                // configuración ilegible: se usa la carpeta por defecto
            }
        }
        return carpetaDocumentos().resolve(NOMBRE_CARPETA_TRABAJO);
    }

    /** Carpeta {@code output/} dentro de la carpeta de trabajo. */
    public static Path carpetaSalida() {
        return carpetaTrabajo().resolve("output");
    }

    /**
     * Crea la carpeta de trabajo con {@code output/}, {@code proyectos/} y {@code ejemplos/}; esta
     * última se rellena con los ejemplos que viajan con el programa si todavía no existe.
     * Si no se puede escribir en ella, no falla: el IDE avisará al compilar.
     */
    public static Path prepararCarpetaTrabajo() {
        Path trabajo = carpetaTrabajo();
        try {
            Files.createDirectories(trabajo.resolve("output"));
            Files.createDirectories(trabajo.resolve("proyectos"));
            Path destino = trabajo.resolve("ejemplos");
            if (!Files.isDirectory(destino)) {
                Files.createDirectories(destino);
                var origen = carpetaEjemplos();
                if (origen.isPresent() && !origen.get().equals(destino.toAbsolutePath())) {
                    try (var archivos = Files.list(origen.get())) {
                        for (Path f : (Iterable<Path>) archivos::iterator) {
                            if (Files.isRegularFile(f)) {
                                Files.copy(f, destino.resolve(f.getFileName()),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException ignore) {
            // carpeta no escribible: se reporta al compilar
        }
        return trabajo;
    }

    /** Ejemplos que ofrece "Abrir ejemplos": la copia del usuario si existe; si no, los de la instalación. */
    public static Optional<Path> ejemplosParaAbrir() {
        Path copia = carpetaTrabajo().resolve("ejemplos");
        if (Files.isDirectory(copia)) {
            return Optional.of(copia.toAbsolutePath());
        }
        return carpetaEjemplos();
    }

    /** Carpeta Documentos del usuario (en Windows "Documentos"); cae a la carpeta personal. */
    public static Path carpetaDocumentos() {
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath();
        var predeterminada = FileSystemView.getFileSystemView().getDefaultDirectory();
        if (predeterminada != null) {
            Path p = predeterminada.toPath().toAbsolutePath();
            // En Windows es "Documentos"; en Linux suele ser la carpeta personal.
            if (Files.isDirectory(p) && !p.equals(home)) {
                return p;
            }
        }
        for (String nombre : new String[] {"Documents", "Documentos"}) {
            Path p = home.resolve(nombre);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return home;
    }

    /**
     * Carpeta {@code ejemplos/}: primero junto a la instalación ({@value #PROP_HOME}), luego junto
     * al ejecutable/JAR (o su carpeta padre, para {@code target/} en desarrollo) y por último
     * en el directorio de trabajo.
     */
    public static Optional<Path> carpetaEjemplos() {
        List<Path> bases = new ArrayList<>();
        String home = System.getProperty(PROP_HOME);
        if (home != null && !home.isBlank()) {
            bases.add(Path.of(home));
        }
        try {
            var fuente = Main.class.getProtectionDomain().getCodeSource();
            if (fuente != null) {
                Path ubicacion = Path.of(fuente.getLocation().toURI());
                Path carpeta = Files.isDirectory(ubicacion) ? ubicacion : ubicacion.getParent();
                if (carpeta != null) {
                    bases.add(carpeta);
                    if (carpeta.getParent() != null) {
                        bases.add(carpeta.getParent());
                    }
                }
            }
        } catch (URISyntaxException | RuntimeException ignore) {
        }
        bases.add(Path.of("").toAbsolutePath());
        for (Path base : bases) {
            Path ejemplos = base.resolve("ejemplos");
            if (Files.isDirectory(ejemplos)) {
                return Optional.of(ejemplos.toAbsolutePath());
            }
        }
        return Optional.empty();
    }
}
