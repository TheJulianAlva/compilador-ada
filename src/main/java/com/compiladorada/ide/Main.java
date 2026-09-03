package com.compiladorada.ide;

import javax.swing.SwingUtilities;

/**
 * Punto de entrada del IDE del Compilador Ada.
 *
 * <p>Etapa actual: analizador lexico y sintactico. Las siguientes unidades
 * (semantico, codigo intermedio, optimizacion, codigo objeto) se construyen
 * sobre el mismo AST y tabla de simbolos que produce esta etapa.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VentanaPrincipal().setVisible(true));
    }
}
