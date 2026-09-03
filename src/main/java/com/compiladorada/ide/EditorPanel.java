package com.compiladorada.ide;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Panel de edición del IDE: envuelve un {@link RSyntaxTextArea} y gestiona el
 * ciclo de vida del archivo fuente (nuevo/abrir/guardar) y el flag "modificado".
 */
public class EditorPanel extends JPanel {

    private static final String NOMBRE_POR_DEFECTO = "fuente_sin_guardar.ada";

    private final RSyntaxTextArea area = new RSyntaxTextArea(30, 80);
    private Path rutaActual;
    private boolean modificado;
    /** Verdadero mientras se carga texto de forma programática; suprime el flag. */
    private boolean cargando;

    public EditorPanel() {
        super(new BorderLayout());
        area.setCodeFoldingEnabled(false);
        area.setTabSize(3);
        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { marcar(); }
            public void removeUpdate(DocumentEvent e) { marcar(); }
            public void changedUpdate(DocumentEvent e) { marcar(); }
        });
    }

    private void marcar() {
        if (!cargando) {
            modificado = true;
        }
    }

    private void cargarTexto(String texto) {
        cargando = true;
        try {
            area.setText(texto);
            area.setCaretPosition(0);
        } finally {
            cargando = false;
        }
    }

    public RSyntaxTextArea getTextArea() {
        return area;
    }

    public String getTexto() {
        return area.getText();
    }

    public void setTexto(String texto) {
        cargarTexto(texto);
    }

    public boolean isModificado() {
        return modificado;
    }

    public String getNombreArchivo() {
        return rutaActual != null ? rutaActual.getFileName().toString() : NOMBRE_POR_DEFECTO;
    }

    public Optional<Path> getRutaArchivo() {
        return Optional.ofNullable(rutaActual);
    }

    public void nuevo() {
        cargarTexto("");
        rutaActual = null;
        modificado = false;
    }

    public void abrir(Path ruta) {
        try {
            cargarTexto(Files.readString(ruta, StandardCharsets.UTF_8));
            rutaActual = ruta;
            modificado = false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void guardar() {
        if (rutaActual == null) {
            throw new IllegalStateException("no hay ruta asignada; usar guardarComo");
        }
        guardarComo(rutaActual);
    }

    public void guardarComo(Path ruta) {
        try {
            Files.writeString(ruta, area.getText(), StandardCharsets.UTF_8);
            rutaActual = ruta;
            modificado = false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void addCaretListener(CaretListener l) {
        area.addCaretListener(l);
    }
}
