package com.compiladorada.ide;

import com.compiladorada.lexico.TokenLexico;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/** Tabla de la etapa léxica: token, tipo, línea y columna. */
public class TablaTokensPanel extends JPanel {

    private final Modelo modelo = new Modelo();

    public TablaTokensPanel() {
        super(new BorderLayout());
        JTable tabla = new JTable(modelo);
        tabla.setAutoCreateRowSorter(true);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    public void setTokens(List<TokenLexico> tokens) {
        modelo.datos = new ArrayList<>(tokens);
        modelo.fireTableDataChanged();
    }

    public int getFilas() {
        return modelo.datos.size();
    }

    private static class Modelo extends AbstractTableModel {
        private final String[] cols = {"Token", "Tipo", "Línea", "Columna"};
        private List<TokenLexico> datos = new ArrayList<>();

        public int getRowCount() { return datos.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            TokenLexico t = datos.get(r);
            return switch (c) {
                case 0 -> t.lexema();
                case 1 -> t.tipo().toString();
                case 2 -> t.linea();
                case 3 -> t.columna();
                default -> "";
            };
        }
    }
}
