package com.compiladorada.ide;

import com.compiladorada.Compilador;
import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.errores.EscritorErrores;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ventana principal del IDE: barra de menú única (sin barra de herramientas),
 * layout con {@link JSplitPane} anidados (editor + tabla de tokens arriba,
 * panel de errores abajo, barra de estado al sur) y el flujo de compilación.
 *
 * <p>El análisis en {@link #compilar()} se ejecuta de forma <b>síncrona</b> en
 * el hilo llamante (no en un {@code SwingWorker}): una compilación del subconjunto
 * Ada del curso es instantánea y así el método es testeable sin sincronización.
 */
public class VentanaPrincipal extends JFrame {

    private final EditorPanel editor = new EditorPanel();
    private final TablaTokensPanel tablaTokens = new TablaTokensPanel();
    private final PanelErrores panelErrores = new PanelErrores();
    private final BarraEstado barraEstado = new BarraEstado();
    private Path directorioSalida = Path.of("output");

    public VentanaPrincipal() {
        super("Compilador Ada — IDE");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setJMenuBar(construirMenu());

        JSplitPane arriba = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, tablaTokens);
        arriba.setResizeWeight(0.62);
        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, arriba, panelErrores);
        principal.setResizeWeight(0.72);

        add(principal, BorderLayout.CENTER);
        add(barraEstado, BorderLayout.SOUTH);

        editor.addCaretListener(actualizarPosicion());
        panelErrores.setOnSeleccion(this::irA);
    }

    private CaretListener actualizarPosicion() {
        return e -> {
            var area = editor.getTextArea();
            try {
                int offset = area.getCaretPosition();
                int linea = area.getLineOfOffset(offset);
                int col = offset - area.getLineStartOffset(linea);
                barraEstado.setPosicionCursor(linea + 1, col + 1);
            } catch (BadLocationException ignore) {
                barraEstado.setPosicionCursor(1, 1);
            }
        };
    }

    private JMenuBar construirMenu() {
        JMenuBar barra = new JMenuBar();

        JMenu archivo = new JMenu("Archivo");
        archivo.add(item("Nuevo", e -> editor.nuevo()));
        archivo.add(item("Abrir…", e -> accionAbrir()));
        archivo.add(item("Guardar", e -> accionGuardar()));
        archivo.add(item("Guardar como…", e -> accionGuardarComo()));
        archivo.addSeparator();
        archivo.add(item("Salir", e -> dispose()));
        barra.add(archivo);

        JMenu editar = new JMenu("Editar");
        var area = editor.getTextArea();
        editar.add(item("Deshacer", e -> area.undoLastAction()));
        editar.add(item("Rehacer", e -> area.redoLastAction()));
        editar.addSeparator();
        editar.add(item("Cortar", e -> area.cut()));
        editar.add(item("Copiar", e -> area.copy()));
        editar.add(item("Pegar", e -> area.paste()));
        barra.add(editar);

        JMenu compilar = new JMenu("Compilar");
        JMenuItem compItem = item("Compilar", e -> compilar());
        compItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        compilar.add(compItem);
        compilar.addSeparator();
        compilar.add(item("Abrir carpeta output/", e -> abrirCarpetaSalida()));
        barra.add(compilar);

        JMenu ver = new JMenu("Ver");
        ver.add(item("Mostrar/ocultar tabla de tokens", e -> tablaTokens.setVisible(!tablaTokens.isVisible())));
        ver.add(item("Mostrar/ocultar panel de errores", e -> panelErrores.setVisible(!panelErrores.isVisible())));
        barra.add(ver);

        JMenu ayuda = new JMenu("Ayuda");
        ayuda.add(item("Acerca de…", e -> JOptionPane.showMessageDialog(this,
                "Compilador Ada — IDE\nLenguajes y Autómatas II")));
        barra.add(ayuda);

        return barra;
    }

    private JMenuItem item(String texto, java.awt.event.ActionListener a) {
        JMenuItem it = new JMenuItem(texto);
        it.addActionListener(a);
        return it;
    }

    /** Toma el texto del editor, analiza, actualiza los paneles y vuelca a {@code output/}. */
    public void compilar() {
        String fuente = editor.getTexto();
        String nombre = editor.getNombreArchivo();
        ResultadoCompilacion r = Compilador.analizar(fuente, nombre);

        tablaTokens.setTokens(r.tokens());
        panelErrores.setErrores(r.erroresLexicos(), r.erroresSintacticos(), r.sintacticoOmitido());

        Path fuenteSinGuardar = null;
        try {
            Files.createDirectories(directorioSalida);
            if (editor.getRutaArchivo().isEmpty()) {
                fuenteSinGuardar = directorioSalida.resolve("fuente_sin_guardar.ada");
                Files.writeString(fuenteSinGuardar, fuente);
            }
            EscritorErrores.volcar(r, directorioSalida, nombre);
        } catch (IOException | UncheckedIOException e) {
            barraEstado.setResultado("No se pudo escribir en " + directorioSalida.toAbsolutePath());
            return;
        }

        String mensaje = r.sintacticoOmitido()
                ? String.format(
                    "Compilado: %d errores léxicos — análisis sintáctico omitido hasta corregirlos (%s)",
                    r.erroresLexicos().size(), directorioSalida.toAbsolutePath())
                : String.format(
                    "Compilado: %d léxicos, %d sintácticos — %s actualizado",
                    r.erroresLexicos().size(), r.erroresSintacticos().size(),
                    directorioSalida.toAbsolutePath());
        if (fuenteSinGuardar != null) {
            mensaje += " (fuente sin guardar en " + fuenteSinGuardar.toAbsolutePath() + ")";
        }
        barraEstado.setResultado(mensaje);
    }

    private void irA(int linea, int columna) {
        var area = editor.getTextArea();
        try {
            int offset = area.getLineStartOffset(Math.max(0, linea - 1)) + Math.max(0, columna - 1);
            area.setCaretPosition(Math.min(offset, area.getText().length()));
            area.requestFocusInWindow();
        } catch (BadLocationException ignore) {
        }
    }

    private void accionAbrir() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            editor.abrir(fc.getSelectedFile().toPath());
        }
    }

    private void accionGuardar() {
        if (editor.getRutaArchivo().isPresent()) {
            editor.guardar();
        } else {
            accionGuardarComo();
        }
    }

    private void accionGuardarComo() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            editor.guardarComo(fc.getSelectedFile().toPath());
        }
    }

    private void abrirCarpetaSalida() {
        try {
            Files.createDirectories(directorioSalida);
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directorioSalida.toFile());
            }
        } catch (IOException | RuntimeException ignore) {
        }
    }

    public void setDirectorioSalida(Path dir) {
        this.directorioSalida = dir;
    }

    public Path getDirectorioSalida() {
        return directorioSalida;
    }

    public EditorPanel getEditor() {
        return editor;
    }

    public TablaTokensPanel getTablaTokens() {
        return tablaTokens;
    }

    public PanelErrores getPanelErrores() {
        return panelErrores;
    }

    public BarraEstado getBarraEstado() {
        return barraEstado;
    }
}
