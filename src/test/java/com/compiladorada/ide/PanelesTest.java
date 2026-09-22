package com.compiladorada.ide;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PanelesTest {

    @Test
    void barra_estado_formatea_la_posicion() {
        BarraEstado b = new BarraEstado();
        b.setPosicionCursor(12, 8);
        assertEquals("Ln 12, Col 8", b.getTextoPosicion());
    }

    @Test
    void barra_estado_guarda_resultado() {
        BarraEstado b = new BarraEstado();
        b.setResultado("2 errores");
        assertEquals("2 errores", b.getTextoResultado());
    }

    @Test
    void tabla_tokens_refleja_la_lista() {
        TablaTokensPanel p = new TablaTokensPanel();
        p.setTokens(List.of(
                new TokenLexico("P", TipoToken.IDENTIFICADOR, 1, 1),
                new TokenLexico(";", TipoToken.DELIMITADOR_SIMPLE, 1, 2)));
        assertEquals(2, p.getFilas());
    }

    @Test
    void tabla_tokens_reemplaza_contenido() {
        TablaTokensPanel p = new TablaTokensPanel();
        p.setTokens(List.of(new TokenLexico("P", TipoToken.IDENTIFICADOR, 1, 1)));
        p.setTokens(List.of());
        assertEquals(0, p.getFilas());
    }

    @Test
    void panel_errores_separa_lexicos_de_sintacticos() {
        PanelErrores p = new PanelErrores();
        p.setErrores(
                List.of(new ErrorCompilacion(Categoria.LEXICO, 1, 1, "a")),
                List.of(new ErrorCompilacion(Categoria.SINTACTICO, 2, 1, "b"),
                        new ErrorCompilacion(Categoria.SINTACTICO, 3, 1, "c")));
        assertEquals(1, p.getFilasLexicas());
        assertEquals(2, p.getFilasSintacticas());
    }

    @Test
    void set_errores_reemplaza_el_contenido_anterior() {
        PanelErrores p = new PanelErrores();
        p.setErrores(List.of(new ErrorCompilacion(Categoria.LEXICO, 1, 1, "a")), List.of());
        p.setErrores(List.of(), List.of());
        assertEquals(0, p.getFilasLexicas());
    }

    @Test
    void on_seleccion_por_defecto_no_falla() {
        PanelErrores p = new PanelErrores();
        AtomicInteger llamadas = new AtomicInteger();
        p.setOnSeleccion((l, c) -> llamadas.incrementAndGet());
        p.setErrores(List.of(new ErrorCompilacion(Categoria.LEXICO, 5, 3, "x")), List.of());
        assertEquals(0, llamadas.get());
    }

    @Test
    void sintactico_omitido_no_cuenta_como_error_pero_avisa() {
        PanelErrores p = new PanelErrores();
        p.setErrores(List.of(new ErrorCompilacion(Categoria.LEXICO, 2, 1, "x")), List.of(), true);
        assertEquals(1, p.getFilasLexicas());
        //  getFilasSintacticas() sigue siendo el conteo de ERRORES sintácticos (0),
        //  aunque la pestaña muestre una fila informativa de aviso.
        assertEquals(0, p.getFilasSintacticas());
    }

    @Test
    void panel_errores_incluye_la_pestania_semantica() {
        PanelErrores p = new PanelErrores();
        p.setErrores(List.of(), List.of(), false,
                List.of(new ErrorCompilacion(Categoria.SEMANTICO, 3, 4, "'X' no está declarado")),
                false);
        assertEquals(1, p.getFilasSemanticas());
    }

    @Test
    void setErrores_de_dos_y_tres_argumentos_dejan_la_pestania_semantica_vacia() {
        PanelErrores p = new PanelErrores();
        p.setErrores(List.of(), List.of());
        assertEquals(0, p.getFilasSemanticas());
    }

    @Test
    void semantico_omitido_no_cuenta_como_error_pero_avisa() {
        PanelErrores p = new PanelErrores();
        p.setErrores(List.of(), List.of(new ErrorCompilacion(Categoria.SINTACTICO, 1, 1, "x")), false,
                List.of(), true);
        //  getFilasSemanticas() sigue siendo el conteo de ERRORES semánticos (0),
        //  aunque la pestaña muestre una fila informativa de aviso.
        assertEquals(0, p.getFilasSemanticas());
    }
}
