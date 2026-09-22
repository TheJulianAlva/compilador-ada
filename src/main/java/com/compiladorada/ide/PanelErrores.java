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

/** Pantalla de errores del IDE con pestañas separadas para léxicos, sintácticos y semánticos. */
public class PanelErrores extends JPanel {

    private final Modelo lexicos = new Modelo();
    private final Modelo sintacticos = new Modelo();
    private final Modelo semanticos = new Modelo();
    private final JTabbedPane pestanias = new JTabbedPane();
    private final JTable tablaLex = new JTable(lexicos);
    private final JTable tablaSin = new JTable(sintacticos);
    private final JTable tablaSem = new JTable(semanticos);
    private BiConsumer<Integer, Integer> onSeleccion = (l, c) -> { };

    public PanelErrores() {
        super(new BorderLayout());
        pestanias.addTab("Léxicos (0)", new JScrollPane(tablaLex));
        pestanias.addTab("Sintácticos (0)", new JScrollPane(tablaSin));
        pestanias.addTab("Semánticos (0)", new JScrollPane(tablaSem));
        add(pestanias, BorderLayout.CENTER);
        instalarDobleClic(tablaLex, lexicos);
        instalarDobleClic(tablaSin, sintacticos);
        instalarDobleClic(tablaSem, semanticos);
    }

    public void setErrores(List<ErrorCompilacion> lex, List<ErrorCompilacion> sin) {
        setErrores(lex, sin, false);
    }

    /**
     * @param sintacticoOmitido si {@code true}, la pestaña sintáctica indica que
     *        el análisis no se ejecutó por haber errores léxicos (no que el
     *        programa sea correcto).
     */
    public void setErrores(List<ErrorCompilacion> lex, List<ErrorCompilacion> sin,
                           boolean sintacticoOmitido) {
        setErrores(lex, sin, sintacticoOmitido, List.of(), false);
    }

    /**
     * @param sintacticoOmitido si {@code true}, la pestaña sintáctica indica que
     *        el análisis no se ejecutó por haber errores léxicos (no que el
     *        programa sea correcto).
     * @param semanticoOmitido si {@code true}, la pestaña semántica indica que
     *        el análisis no se ejecutó por haber errores léxicos o sintácticos
     *        (no que el programa sea correcto).
     */
    public void setErrores(List<ErrorCompilacion> lex, List<ErrorCompilacion> sin,
                           boolean sintacticoOmitido,
                           List<ErrorCompilacion> sem, boolean semanticoOmitido) {
        lexicos.datos = new ArrayList<>(lex);
        lexicos.aviso = null;
        sintacticos.datos = new ArrayList<>(sin);
        sintacticos.aviso = sintacticoOmitido
                ? "análisis sintáctico no ejecutado: corrige primero los errores léxicos"
                : null;
        semanticos.datos = new ArrayList<>(sem);
        semanticos.aviso = semanticoOmitido
                ? "análisis semántico no ejecutado: corrige primero los errores léxicos o sintácticos"
                : null;
        lexicos.fireTableDataChanged();
        sintacticos.fireTableDataChanged();
        semanticos.fireTableDataChanged();
        pestanias.setTitleAt(0, "Léxicos (" + lex.size() + ")");
        pestanias.setTitleAt(1, sintacticoOmitido
                ? "Sintácticos (omitido)"
                : "Sintácticos (" + sin.size() + ")");
        pestanias.setTitleAt(2, semanticoOmitido
                ? "Semánticos (omitido)"
                : "Semánticos (" + sem.size() + ")");
    }

    public int getFilasLexicas() { return lexicos.datos.size(); }

    public int getFilasSintacticas() { return sintacticos.datos.size(); }

    public int getFilasSemanticas() { return semanticos.datos.size(); }

    public void setOnSeleccion(BiConsumer<Integer, Integer> cb) {
        this.onSeleccion = cb != null ? cb : (l, c) -> { };
    }

    private void instalarDobleClic(JTable tabla, Modelo modelo) {
        tabla.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !modelo.datos.isEmpty()) {
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
        /** Fila informativa que se muestra cuando no hay errores que listar. */
        private String aviso;

        public int getRowCount() {
            return datos.isEmpty() && aviso != null ? 1 : datos.size();
        }

        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }

        public Object getValueAt(int r, int c) {
            if (datos.isEmpty() && aviso != null) {
                return c == 0 ? "" : aviso;
            }
            ErrorCompilacion e = datos.get(r);
            return c == 0 ? e.linea() + ":" + e.columna() : e.mensaje();
        }
    }
}
