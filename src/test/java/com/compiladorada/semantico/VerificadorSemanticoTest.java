package com.compiladorada.semantico;

import com.compiladorada.errores.ErrorCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerificadorSemanticoTest {

    @Test
    void tipos_predefinidos_estan_disponibles_desde_el_inicio() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertEquals(TipoAda.INTEGER, v.tipoDeclarado("Integer", 1, 1));
        assertEquals(TipoAda.BOOLEAN, v.tipoDeclarado("Boolean", 1, 1));
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void declarar_variable_y_usarla_no_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        Simbolo s = v.resolverUso("X", 2, 1);
        assertNotNull(s);
        assertEquals(TipoAda.INTEGER, s.tipo());
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void usar_identificador_no_declarado_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertNull(v.resolverUso("Fantasma", 5, 3));
        assertEquals(1, v.errores().size());
        assertEquals(ErrorCompilacion.Categoria.SEMANTICO, v.errores().get(0).categoria());
        assertEquals("'Fantasma' no está declarado", v.errores().get(0).mensaje());
    }

    @Test
    void redeclarar_en_el_mismo_ambito_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        v.declararVariables(List.of("X"), TipoAda.FLOAT, false, 2, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("ya está declarado"));
    }

    @Test
    void sombrear_en_un_ambito_anidado_no_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        v.entrarAmbito();
        v.declararVariables(List.of("X"), TipoAda.FLOAT, false, 2, 1);
        assertTrue(v.errores().isEmpty());
        v.salirAmbito();
        assertEquals(TipoAda.INTEGER, v.resolverUso("X", 3, 1).tipo());
    }

    @Test
    void salir_de_ambito_hace_visible_de_nuevo_al_externo() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.entrarAmbito();
        v.declararVariables(List.of("Local"), TipoAda.INTEGER, false, 1, 1);
        v.salirAmbito();
        assertNull(v.resolverUso("Local", 2, 1));
    }

    @Test
    void asignar_a_constante_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("Pi"), TipoAda.FLOAT, true, 1, 1);
        Simbolo s = v.resolverUso("Pi", 2, 1);
        v.verificarAsignacionMutabilidad(s, 3, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("constante"));
    }

    @Test
    void inicializar_con_tipo_incompatible_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.verificarInicializacion(TipoAda.INTEGER, TipoAda.BOOLEAN, 1, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("incompatibles"));
    }

    @Test
    void operador_logico_exige_booleanos() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda r = v.tipoOperadorLogico(TipoAda.INTEGER, TipoAda.BOOLEAN, "and", 1, 1);
        assertEquals(TipoAda.DESCONOCIDO, r);
        assertEquals(1, v.errores().size());

        VerificadorSemantico v2 = new VerificadorSemantico();
        assertEquals(TipoAda.BOOLEAN, v2.tipoOperadorLogico(TipoAda.BOOLEAN, TipoAda.BOOLEAN, "and", 1, 1));
        assertTrue(v2.errores().isEmpty());
    }

    @Test
    void operador_aditivo_numerico_devuelve_el_mismo_tipo() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertEquals(TipoAda.INTEGER, v.tipoOperadorAditivo(TipoAda.INTEGER, TipoAda.INTEGER, "+", 1, 1));
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void concatenacion_acepta_string_y_character() {
        VerificadorSemantico v = new VerificadorSemantico();
        assertEquals(TipoAda.STRING, v.tipoOperadorAditivo(TipoAda.STRING, TipoAda.CHARACTER, "&", 1, 1));
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void indexar_algo_que_no_es_arreglo_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda r = v.tipoDeIndexacion(TipoAda.INTEGER, TipoAda.INTEGER, 1, 1);
        assertEquals(TipoAda.DESCONOCIDO, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void indexar_un_arreglo_con_indice_no_entero_reporta_error_pero_devuelve_el_componente() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda.TipoArreglo vector = new TipoAda.TipoArreglo("Vector", TipoAda.FLOAT);
        TipoAda r = v.tipoDeIndexacion(vector, TipoAda.BOOLEAN, 1, 1);
        assertEquals(TipoAda.FLOAT, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void campo_inexistente_en_un_registro_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda.TipoRegistro punto = new TipoAda.TipoRegistro("Punto",
                java.util.Map.of("x", TipoAda.INTEGER, "y", TipoAda.INTEGER));
        TipoAda r = v.tipoDeCampo(punto, "Z", 1, 1);
        assertEquals(TipoAda.DESCONOCIDO, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void llamada_con_aridad_incorrecta_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararSubprograma("Suma", List.of(TipoAda.INTEGER, TipoAda.INTEGER), TipoAda.INTEGER, 1, 1);
        Simbolo suma = v.resolverUso("Suma", 2, 1);
        TipoAda r = v.tipoDeLlamadaOIndexacion(suma, suma.tipo(), List.of(TipoAda.INTEGER), 2, 1);
        assertEquals(TipoAda.INTEGER, r);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("2 argumento"));
    }

    @Test
    void llamada_con_tipo_de_argumento_incorrecto_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararSubprograma("Suma", List.of(TipoAda.INTEGER, TipoAda.INTEGER), TipoAda.INTEGER, 1, 1);
        Simbolo suma = v.resolverUso("Suma", 2, 1);
        TipoAda r = v.tipoDeLlamadaOIndexacion(suma, suma.tipo(),
                List.of(TipoAda.INTEGER, TipoAda.BOOLEAN), 2, 1);
        assertEquals(TipoAda.INTEGER, r);
        assertEquals(1, v.errores().size());
    }

    @Test
    void resolver_con_orden_rechaza_un_uso_antes_de_la_declaracion() {
        VerificadorSemantico v = new VerificadorSemantico();
        // "uso" en la línea 1, "declaración" en la línea 5: no es válido.
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 5, 1);
        Simbolo s = v.resolverUsoConOrden("X", 1, 1);
        assertNull(s);
        assertEquals(1, v.errores().size());
    }

    @Test
    void resolver_con_orden_acepta_un_uso_despues_de_la_declaracion() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.declararVariables(List.of("X"), TipoAda.INTEGER, false, 1, 1);
        Simbolo s = v.resolverUsoConOrden("X", 5, 1);
        assertNotNull(s);
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void profundidad_y_salirHasta_restauran_el_ambito() {
        VerificadorSemantico v = new VerificadorSemantico();
        int prof = v.profundidad();
        v.entrarAmbito();
        v.entrarAmbito();
        v.salirHasta(prof);
        assertEquals(prof, v.profundidad());
    }

    @Test
    void asignar_literal_a_variable_de_tipo_rango_no_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        TipoAda.TipoRango nota = new TipoAda.TipoRango("Nota");
        v.verificarInicializacion(nota, TipoAda.LITERAL_ENTERO, 1, 1);
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void asignar_literal_real_a_variable_entera_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        v.verificarInicializacion(TipoAda.INTEGER, TipoAda.LITERAL_REAL, 1, 1);
        assertEquals(1, v.errores().size());
    }

    @Test
    void conversion_de_tipo_con_argumento_numerico_devuelve_el_tipo_destino() {
        VerificadorSemantico v = new VerificadorSemantico();
        Simbolo tipoIntegerComoSimbolo = new Simbolo(
                "Integer", Simbolo.Categoria.TIPO, TipoAda.INTEGER, false, 0, 0);
        TipoAda r = v.tipoDeLlamadaOIndexacion(
                tipoIntegerComoSimbolo, TipoAda.INTEGER, List.of(TipoAda.FLOAT), 1, 1);
        assertEquals(TipoAda.INTEGER, r);
        assertTrue(v.errores().isEmpty());
    }

    @Test
    void conversion_de_tipo_con_argumento_no_numerico_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        Simbolo tipoIntegerComoSimbolo = new Simbolo(
                "Integer", Simbolo.Categoria.TIPO, TipoAda.INTEGER, false, 0, 0);
        TipoAda r = v.tipoDeLlamadaOIndexacion(
                tipoIntegerComoSimbolo, TipoAda.INTEGER, List.of(TipoAda.BOOLEAN), 1, 1);
        assertEquals(TipoAda.INTEGER, r);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("no se puede convertir"));
    }

    @Test
    void conversion_de_tipo_con_aridad_incorrecta_reporta_error() {
        VerificadorSemantico v = new VerificadorSemantico();
        Simbolo tipoIntegerComoSimbolo = new Simbolo(
                "Integer", Simbolo.Categoria.TIPO, TipoAda.INTEGER, false, 0, 0);
        v.tipoDeLlamadaOIndexacion(
                tipoIntegerComoSimbolo, TipoAda.INTEGER, List.of(TipoAda.INTEGER, TipoAda.INTEGER), 1, 1);
        assertEquals(1, v.errores().size());
        assertTrue(v.errores().get(0).mensaje().contains("espera 1 argumento"));
    }
}
