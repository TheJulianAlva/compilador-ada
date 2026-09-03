package com.compiladorada.sintactico;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.generado.AdaParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecuperacionSintacticaTest {

    private List<ErrorCompilacion> errores(String fuente) throws Exception {
        AdaParser p = new AdaParser(new StringReader(fuente));
        try {
            p.programa();
        } catch (Exception ignorada) {
            // la recuperación puede rendirse; los errores acumulados siguen valiendo
        }
        return p.getErroresSintacticos();
    }

    @Test
    void reporta_un_error_por_punto_como_minimo() throws Exception {
        List<ErrorCompilacion> e = errores("procedure P is begin null end;");
        assertFalse(e.isEmpty());
        assertEquals(ErrorCompilacion.Categoria.SINTACTICO, e.get(0).categoria());
    }

    @Test
    void recupera_y_encuentra_multiples_errores_en_distintas_lineas() throws Exception {
        String fuente = ""
                + "procedure P is\n"
                + "  X : Integer\n"      // falta ';'
                + "begin\n"
                + "  Y := ;\n"           // expresión inválida
                + "  Z := 1\n"           // falta ';'
                + "end;\n";
        List<ErrorCompilacion> e = errores(fuente);
        assertTrue(e.size() >= 2, "se esperaban >=2 errores, hubo " + e.size());
        long lineasDistintas = e.stream().map(ErrorCompilacion::linea).distinct().count();
        assertTrue(lineasDistintas >= 2);
    }

    @Test
    void los_mensajes_estan_en_espaniol() throws Exception {
        List<ErrorCompilacion> e = errores("procedure P is begin null end;");
        assertTrue(e.get(0).mensaje().toLowerCase().contains("se esperaba")
                || e.get(0).mensaje().toLowerCase().contains("encontr"));
    }

    @Test
    void programa_no_propaga_ParseException_en_entrada_recuperable() {
        // entrada inválida pero recuperable: falta ';'
        AdaParser p = new AdaParser(new StringReader("procedure P is begin null end;"));
        assertDoesNotThrow(() -> p.programa());
        assertFalse(p.getErroresSintacticos().isEmpty());
    }

    @Test
    void programa_valido_no_produce_errores() throws Exception {
        assertTrue(errores("procedure P is begin null; end;").isEmpty());
    }

    @Test
    void programa_valido_con_basura_final_conserva_el_arbol() throws Exception {
        String fuente = "procedure P is begin null; end P;\n@@@\n";
        AdaParser p = new AdaParser(new StringReader(fuente));
        Object nodo = p.programa();
        List<ErrorCompilacion> e = p.getErroresSintacticos();
        assertNotNull(nodo, "el procedimiento parseado debe conservarse pese a la basura final");
        assertFalse(e.isEmpty(), "la basura final debe producir un error");
        assertEquals(2, e.get(0).linea(), "el error debe apuntar a la basura, no a 1:1");
    }

    @Test
    void el_parser_se_rinde_ante_entrada_irrecuperable() throws Exception {
        // Cientos de sentencias basura: se supera el tope de 200 y el análisis se interrumpe.
        StringBuilder sb = new StringBuilder("procedure P is begin\n");
        for (int i = 0; i < 500; i++) {
            sb.append("A B;\n");
        }
        sb.append("end;\n");
        List<ErrorCompilacion> e = errores(sb.toString());
        assertTrue(e.size() > 200, "debería haber acumulado más de 200 errores, hubo " + e.size());
        assertTrue(e.size() < 500, "el análisis debió interrumpirse, no recorrer todo");
    }
}
