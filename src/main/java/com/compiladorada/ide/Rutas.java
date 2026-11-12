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

    private Rutas() {
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
