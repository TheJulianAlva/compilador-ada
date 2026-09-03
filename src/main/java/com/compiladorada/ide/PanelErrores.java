package com.compiladorada.ide;

import com.compiladorada.errores.ErrorCompilacion;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Pantalla de errores del IDE con pestañas separadas para léxicos y sintácticos. */
public class PanelErrores extends JPanel {

    private final Modelo lexicos = new Modelo();
    private final Modelo sintacticos = new Modelo();
    private final JTabbedPane pestanias = new JTabbedPane();
    private final JTable tablaLex = new JTable(lexicos);
    private final JTable tablaSin = new JTable(sintacticos);
    private BiConsumer<Integer, Integer> onSeleccion = (l, c) -> { };

    public PanelErrores() {
        super(new BorderLayout());
        pestanias.addTab("Léxicos (0)", new JScrollPane(tablaLex));
        pestanias.addTab("Sintácticos (0)", new JScrollPane(tablaSin));
        add(pestanias, BorderLayout.CENTER);
        instalarDobleClic(tablaLex, lexicos);
        instalarDobleClic(tablaSin, sintacticos);
    }

    public void setErrores(List<ErrorCompilacion> lex, List<ErrorCompilacion> sin) {
        lexicos.datos = new ArrayList<>(lex);
        sintacticos.datos = new ArrayList<>(sin);
        lexicos.fireTableDataChanged();
        sintacticos.fireTableDataChanged();
        pestanias.setTitleAt(0, "Léxicos (" + lex.size() + ")");
        pestanias.setTitleAt(1, "Sintácticos (" + sin.size() + ")");
    }

    public int getFilasLexicas() { return lexicos.datos.size(); }

    public int getFilasSintacticas() { return sintacticos.datos.size(); }

    public void setOnSeleccion(BiConsumer<Integer, Integer> cb) {
        this.onSeleccion = cb != null ? cb : (l, c) -> { };
    }

    private void instalarDobleClic(JTable tabla, Modelo modelo) {
        tabla.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int fila = tabla.getSelectedRow();
                    if (fila >= 0) {
                        ErrorCompilacion err = modelo.datos.get(tabla.convertRowIndexToModel(fila));
                        onSeleccion.accept(err.linea(), err.columna());
                    }
                }
            }
        });
    }

    private static class Modelo extends AbstractTableModel {
        private final String[] cols = {"Ln:Col", "Mensaje"};
        private List<ErrorCompilacion> datos = new ArrayList<>();

        public int getRowCount() { return datos.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            ErrorCompilacion e = datos.get(r);
            return c == 0 ? e.linea() + ":" + e.columna() : e.mensaje();
        }
    }
}
