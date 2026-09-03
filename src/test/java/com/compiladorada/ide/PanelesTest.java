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
}
