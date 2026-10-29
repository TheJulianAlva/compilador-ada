package com.compiladorada.ide;

import com.compiladorada.optimizacion.ResultadoOptimizacion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Pestaña "Optimizado": el fuente optimizado, de solo lectura y con el mismo
 * resaltado Ada del editor. El original es el que está en el editor, a su
 * izquierda; aquí no se marcan diferencias.
 */
public class PanelOptimizado extends JPanel {

    private static final String AVISO = "Optimización no ejecutada: "
            + "corrige primero los errores léxicos, sintácticos o semánticos.";

    private final RSyntaxTextArea area = new RSyntaxTextArea(10, 40);

    public PanelOptimizado() {
        super(new BorderLayout());
        area.setEditable(false);
        area.setCodeFoldingEnabled(false);
        area.setTabSize(3);
        // el mapeo "text/ada" lo registra EditorPanel; se repite por si este panel se crea antes
        ((org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory)
                org.fife.ui.rsyntaxtextarea.TokenMakerFactory.getDefaultInstance())
                .putMapping("text/ada", "com.compiladorada.ide.AdaTokenMaker");
        area.setSyntaxEditingStyle("text/ada");
        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        add(scroll, BorderLayout.CENTER);
        setResultado(null);
    }

    /** @param r resultado de la fase, o {@code null} si se omitió por errores previos */
    public void setResultado(ResultadoOptimizacion r) {
        area.setText(r == null ? AVISO : r.optimizado());
        area.setCaretPosition(0);
    }

    public String getOptimizado() {
        return area.getText();
    }
}
