package com.compiladorada.ide;

import com.compiladorada.intermedio.Cuadruplo;
import com.compiladorada.intermedio.FilaIntermedia;
import com.compiladorada.intermedio.ResultadoIntermedio;
import com.compiladorada.intermedio.ValorFinal;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pestaña "Código intermedio": una fila por expresión aritmética (fuente,
 * prefijo, posfijo, cuádruplos, resultado) y, debajo, los métodos utilizados y
 * los valores finales de las variables.
 */
public class PanelIntermedio extends JPanel {

    private final Modelo modelo = new Modelo();
    private final JTable tabla = new JTable(modelo);
    private final JTextArea detalle = new JTextArea();

    public PanelIntermedio() {
        super(new BorderLayout());
        detalle.setEditable(false);
        detalle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] anchos = {50, 200, 60, 160, 160, 330, 220};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tabla), new JScrollPane(detalle));
        division.setResizeWeight(0.6);
        add(division, BorderLayout.CENTER);
        setOmitido();
    }

    /** @param r resultado de la fase, o {@code null} si se omitió por errores previos */
    public void setResultado(ResultadoIntermedio r) {
        if (r == null) {
            setOmitido();
            return;
        }
        modelo.datos = new ArrayList<>(r.filas());
        modelo.fireTableDataChanged();
        StringBuilder sb = new StringBuilder("Métodos utilizados:\n");
        for (String m : r.metodos()) {
            sb.append("  * ").append(m).append('\n');
        }
        sb.append("\nValores finales:\n");
        if (r.valoresFinales().isEmpty()) {
            sb.append("  (sin variables numéricas)\n");
        }
        for (ValorFinal v : r.valoresFinales()) {
            sb.append("  ").append(v.nombre()).append(" : ").append(v.tipo())
              .append(" = ").append(v.texto()).append('\n');
        }
        detalle.setText(sb.toString());
        detalle.setCaretPosition(0);
    }

    private void setOmitido() {
        modelo.datos = new ArrayList<>();
        modelo.fireTableDataChanged();
        detalle.setText("Generación de código intermedio no ejecutada: "
                + "corrige primero los errores léxicos, sintácticos o semánticos.");
    }

    public int getFilas() {
        return modelo.datos.size();
    }

    public String getDetalle() {
        return detalle.getText();
    }

    private static class Modelo extends AbstractTableModel {
        private final String[] cols = {"Ln", "Fuente", "Tipo", "Prefijo", "Posfijo", "Cuádruplos", "Resultado"};
        private List<FilaIntermedia> datos = new ArrayList<>();

        public int getRowCount() { return datos.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            FilaIntermedia f = datos.get(r);
            return switch (c) {
                case 0 -> f.linea();
                case 1 -> f.fuente();
                case 2 -> f.tipo() == null ? "-" : f.tipo();
                case 3 -> f.prefijo();
                case 4 -> f.posfijo();
                case 5 -> f.cuadruplos().stream().map(Cuadruplo::toString).collect(Collectors.joining(" "));
                default -> f.resultado();
            };
        }
    }
}
