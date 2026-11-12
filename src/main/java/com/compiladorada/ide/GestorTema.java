package com.compiladorada.ide;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.util.prefs.Preferences;

/** Look and feel moderno (FlatLaf) con tema claro/oscuro y su persistencia. */
public final class GestorTema {

    public enum Tema {
        CLARO("/org/fife/ui/rsyntaxtextarea/themes/default.xml"),
        OSCURO("/org/fife/ui/rsyntaxtextarea/themes/dark.xml");

        private final String recursoEditor;

        Tema(String recursoEditor) {
            this.recursoEditor = recursoEditor;
        }
    }

    private static final String CLAVE = "tema";
    private static Tema actual = Tema.CLARO;

    private GestorTema() {
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(GestorTema.class);
    }

    /** Lee la preferencia guardada e instala el look and feel; llamar antes de crear ventanas. */
    public static void iniciar() {
        Tema guardado = Tema.CLARO;
        try {
            guardado = Tema.valueOf(prefs().get(CLAVE, Tema.CLARO.name()));
        } catch (IllegalArgumentException | SecurityException ignore) {
        }
        instalarLaf(guardado);
    }

    public static Tema getActual() {
        return actual;
    }

    /** Cambia el tema en caliente y lo recuerda. */
    public static void aplicar(Tema tema) {
        instalarLaf(tema);
        try {
            prefs().put(CLAVE, tema.name());
        } catch (SecurityException ignore) {
        }
        FlatLaf.updateUI();
        for (Window w : Window.getWindows()) {
            restilizar(w);
        }
    }

    private static void instalarLaf(Tema tema) {
        actual = tema;
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TabbedPane.tabHeight", 32);
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("SplitPane.dividerSize", 5);
        UIManager.put("MenuItem.selectionArc", 6);
        if (tema == Tema.OSCURO) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    /** Aplica el tema de color y la fuente monoespaciada a un área de código. */
    public static void estilizar(RSyntaxTextArea area) {
        try (InputStream in = GestorTema.class.getResourceAsStream(actual.recursoEditor)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (IOException ignore) {
        }
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
    }

    private static void restilizar(Component c) {
        if (c instanceof RSyntaxTextArea area) {
            estilizar(area);
        }
        if (c instanceof Container cont) {
            for (Component hijo : cont.getComponents()) {
                restilizar(hijo);
            }
        }
        if (c instanceof javax.swing.JMenu m) {
            for (Component hijo : m.getMenuComponents()) {
                restilizar(hijo);
            }
        }
        if (c instanceof Window w) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}
