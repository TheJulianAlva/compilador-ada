package com.compiladorada.ide;

import com.compiladorada.optimizacion.ResultadoOptimizacion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;

/**
 * Pestaña "Optimizado": a la izquierda el fuente original tal como estaba en
 * el editor al compilar; a la derecha el fuente optimizado. Ambos de solo
 * lectura, con el mismo resaltado Ada del editor y sin marcar diferencias.
 */
public class PanelOptimizado extends JPanel {

    private static final String AVISO = "Optimización no ejecutada: "
            + "corrige primero los errores léxicos, sintácticos o semánticos.";

    private final RSyntaxTextArea original = crearArea();
    private final RSyntaxTextArea optimizado = crearArea();

    public PanelOptimizado() {
        super(new BorderLayout());
        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                envolver(original), envolver(optimizado));
        division.setResizeWeight(0.5);
        add(division, BorderLayout.CENTER);
        setResultado(null);
    }

    private static RSyntaxTextArea crearArea() {
        RSyntaxTextArea area = new RSyntaxTextArea(10, 40);
        area.setEditable(false);
        area.setCodeFoldingEnabled(false);
        area.setTabSize(3);
        // el mapeo "text/ada" lo registra EditorPanel; se repite por si este panel se crea antes
        ((org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory)
                org.fife.ui.rsyntaxtextarea.TokenMakerFactory.getDefaultInstance())
                .putMapping("text/ada", "com.compiladorada.ide.AdaTokenMaker");
        area.setSyntaxEditingStyle("text/ada");
        return area;
    }

    private static RTextScrollPane envolver(RSyntaxTextArea area) {
        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        return scroll;
    }

    /** @param r resultado de la fase, o {@code null} si se omitió por errores previos */
    public void setResultado(ResultadoOptimizacion r) {
        if (r == null) {
            original.setText("");
            optimizado.setText(AVISO);
        } else {
            original.setText(r.original());
            optimizado.setText(r.optimizado());
        }
        original.setCaretPosition(0);
        optimizado.setCaretPosition(0);
    }

    public String getOriginal() {
        return original.getText();
    }

    public String getOptimizado() {
        return optimizado.getText();
    }
}
