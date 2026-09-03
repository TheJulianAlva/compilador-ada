package com.compiladorada.ide;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Barra inferior del IDE: posición del cursor y resultado de la última compilación. */
public class BarraEstado extends JPanel {

    private final JLabel posicion = new JLabel("Ln 1, Col 1");
    private final JLabel resultado = new JLabel(" ");

    public BarraEstado() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(posicion, BorderLayout.WEST);
        add(resultado, BorderLayout.EAST);
    }

    public void setPosicionCursor(int linea, int columna) {
        posicion.setText("Ln " + linea + ", Col " + columna);
    }

    public String getTextoPosicion() {
        return posicion.getText();
    }

    public void setResultado(String texto) {
        resultado.setText(texto);
    }

    public String getTextoResultado() {
        return resultado.getText();
    }
}
