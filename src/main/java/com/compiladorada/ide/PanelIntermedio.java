package com.compiladorada.ide;

import com.compiladorada.intermedio.Cuadruplo;
import com.compiladorada.intermedio.FilaIntermedia;
import com.compiladorada.intermedio.ResultadoIntermedio;
import com.compiladorada.intermedio.ValorFinal;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña "Código intermedio" en vista maestro-detalle: a la izquierda la
 * lista de expresiones aritméticas; a la derecha, para la seleccionada, su
 * tabla de cuádruplos {@code # | Operador | Arg 1 | Arg 2 | Resultado} y debajo
 * prefijo, posfijo y resultado. Abajo, los métodos utilizados y los valores
 * finales de las variables.
 */
public class PanelIntermedio extends JPanel {

    private final DefaultListModel<String> listaModelo = new DefaultListModel<>();
    private final JList<String> lista = new JList<>(listaModelo);
    private final Modelo modelo = new Modelo();
    private final JTable tabla = new JTable(modelo);
    private final JTextArea resumen = new JTextArea(4, 40);
    private final JTextArea detalle = new JTextArea();
    private List<FilaIntermedia> filas = new ArrayList<>();

    public PanelIntermedio() {
        super(new BorderLayout());
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        resumen.setEditable(false);
        resumen.setFont(mono);
        detalle.setEditable(false);
        detalle.setFont(mono);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrar(lista.getSelectedIndex());
            }
        });

        JPanel derecha = new JPanel(new BorderLayout());
        derecha.add(new JScrollPane(tabla), BorderLayout.CENTER);
        derecha.add(new JScrollPane(resumen), BorderLayout.SOUTH);

        JSplitPane arriba = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(lista), derecha);
        arriba.setResizeWeight(0.35);
        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, arriba, new JScrollPane(detalle));
        division.setResizeWeight(0.65);
        add(division, BorderLayout.CENTER);
        setOmitido();
    }

    /** @param r resultado de la fase, o {@code null} si se omitió por errores previos */
    public void setResultado(ResultadoIntermedio r) {
        if (r == null) {
            setOmitido();
            return;
        }
        filas = new ArrayList<>(r.filas());
        listaModelo.clear();
        for (FilaIntermedia f : filas) {
            listaModelo.addElement("Ln " + f.linea() + ": " + f.fuente());
        }
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
        if (filas.isEmpty()) {
            mostrar(-1);
        } else {
            lista.setSelectedIndex(0);
            mostrar(0);
        }
    }

    private void setOmitido() {
        filas = new ArrayList<>();
        listaModelo.clear();
        mostrar(-1);
        detalle.setText("Generación de código intermedio no ejecutada: "
                + "corrige primero los errores léxicos, sintácticos o semánticos.");
    }

    private void mostrar(int indice) {
        if (indice < 0 || indice >= filas.size()) {
            modelo.datos = new ArrayList<>();
            resumen.setText("");
        } else {
            FilaIntermedia f = filas.get(indice);
            modelo.datos = new ArrayList<>(f.cuadruplos());
            resumen.setText("Prefijo  : " + f.prefijo() + "\n"
                    + "Posfijo  : " + f.posfijo() + "\n"
                    + "Resultado: " + f.resultado() + (f.tipo() != null ? " (" + f.tipo() + ")" : ""));
        }
        modelo.fireTableDataChanged();
    }

    /** Número de expresiones listadas. */
    public int getFilas() {
        return filas.size();
    }

    public String getDetalle() {
        return detalle.getText();
    }

    /** Filas de la tabla de cuádruplos de la expresión seleccionada. */
    public int getFilasCuadruplos() {
        return modelo.datos.size();
    }

    public String getResumen() {
        return resumen.getText();
    }

    private static class Modelo extends AbstractTableModel {
        private final String[] cols = {"#", "Operador", "Arg 1", "Arg 2", "Resultado"};
        private List<Cuadruplo> datos = new ArrayList<>();

        public int getRowCount() { return datos.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            Cuadruplo q = datos.get(r);
            return switch (c) {
                case 0 -> q.numero();
                case 1 -> q.op();
                case 2 -> q.arg1();
                case 3 -> q.arg2();
                default -> q.resultado();
            };
        }
    }
}
