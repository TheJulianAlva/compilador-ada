package com.compiladorada.intermedio;

import com.compiladorada.Compilador;
import com.compiladorada.ResultadoCompilacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneradorIntermedioTest {

    private static ResultadoIntermedio generar(String cuerpoDeclaraciones, String sentencias) {
        String fuente = "procedure P is\n" + cuerpoDeclaraciones + "\nbegin\n" + sentencias + "\nend P;\n";
        ResultadoCompilacion r = Compilador.analizar(fuente, "p.ada");
        assertFalse(r.tieneErrores(), "el programa de prueba debe ser válido: " + r.erroresSemanticos()
                + r.erroresSintacticos());
        assertNotNull(r.intermedio());
        return r.intermedio();
    }

    private static String valorFinal(ResultadoIntermedio r, String nombre) {
        return r.valoresFinales().stream().filter(v -> v.nombre().equalsIgnoreCase(nombre))
                .findFirst().orElseThrow().texto();
    }

    @Test
    void memoria_acumulativa_y_notaciones() {
        ResultadoIntermedio r = generar("A : Integer := 5;\nB : Integer;\nC : Integer;",
                "B := A * 2 + 1;\nC := (A + B) * (B - 3);");
        assertEquals(3, r.filas().size());

        FilaIntermedia decl = r.filas().get(0);
        assertEquals("5", decl.valor());
        assertEquals(List.of("(:=, 5, _, A)"), decl.cuadruplos().stream().map(Object::toString).toList());

        FilaIntermedia f1 = r.filas().get(1);
        assertEquals("B := A * 2 + 1;", f1.fuente());
        assertEquals("+ * A 2 1", f1.prefijo());
        assertEquals("A 2 * 1 +", f1.posfijo());
        assertEquals("11", f1.valor());
        assertEquals("Integer", f1.tipo());
        assertEquals(List.of("(*, A, 2, t1)", "(+, t1, 1, t2)", "(:=, t2, _, B)"),
                f1.cuadruplos().stream().map(Object::toString).toList());

        FilaIntermedia f2 = r.filas().get(2);
        assertEquals("* + A B - B 3", f2.prefijo());
        assertEquals("A B + B 3 - *", f2.posfijo());
        assertEquals("128", f2.valor());

        assertEquals("5", valorFinal(r, "A"));
        assertEquals("11", valorFinal(r, "B"));
        assertEquals("128", valorFinal(r, "C"));
    }

    @Test
    void constantes_float_y_operadores_de_ada() {
        ResultadoIntermedio r = generar(
                "K : constant Integer := 3 * 4;\nF : Float := 1.5;\nG : Float;\nM : Integer;\nN : Integer;",
                "G := F * 2.0 + 0.25;\nM := (-7) mod 3;\nN := abs (-K) ** 2;");
        assertEquals("12", valorFinal(r, "K"));
        assertEquals("3.25", valorFinal(r, "G"));
        assertEquals("2", valorFinal(r, "M"));
        assertEquals("144", valorFinal(r, "N"));
        assertEquals("Float", r.filas().get(2).tipo());
    }

    private static final String MOSTRAR = """
            procedure Mostrar (V : Integer) is
            begin
               null;
            end Mostrar;

            """;

    private static ResultadoIntermedio conProcedimientoAuxiliar(String declaraciones, String sentencias) {
        String fuente = MOSTRAR + "procedure P is\n" + declaraciones + "\nbegin\n" + sentencias + "\nend P;\n";
        ResultadoCompilacion r = Compilador.analizar(fuente, "p.ada");
        assertFalse(r.tieneErrores(), r.erroresSemanticos().toString() + r.erroresSintacticos());
        return r.intermedio();
    }

    @Test
    void argumento_aritmetico_de_llamada_se_evalua_sin_escribir_memoria() {
        ResultadoIntermedio r = conProcedimientoAuxiliar("A : Integer := 2;\nB : Integer := 3;",
                "Mostrar(A + B * 2);");
        assertEquals(3, r.filas().size());
        FilaIntermedia f = r.filas().get(2);
        assertEquals("8", f.valor());
        assertEquals("A + B * 2", f.fuente());
        assertEquals(List.of("(*, B, 2, t1)", "(+, A, t1, t2)"),
                f.cuadruplos().stream().map(Object::toString).toList());
        assertEquals("2", valorFinal(r, "A"));
    }

    @Test
    void argumento_que_no_es_operacion_no_genera_fila() {
        ResultadoIntermedio r = conProcedimientoAuxiliar("A : Integer := 2;", "Mostrar(A);");
        assertEquals(1, r.filas().size());
    }

    @Test
    void division_entre_cero_es_error_de_fila() {
        ResultadoIntermedio r = generar("A : Integer := 0;\nB : Integer;\nC : Integer;",
                "B := 10 / A;\nC := B + 1;");
        FilaIntermedia f = r.filas().get(1);
        assertNull(f.valor());
        assertTrue(f.error().contains("división entre cero"));
        FilaIntermedia dependiente = r.filas().get(2);
        assertNotNull(dependiente.error());
        assertTrue(dependiente.error().contains("'B' no tiene valor"));
        assertTrue(valorFinal(r, "B").startsWith("(sin valor"));
    }

    @Test
    void variable_sin_inicializar_no_se_asume_cero() {
        ResultadoIntermedio r = generar("A : Integer;\nB : Integer;", "B := A + 1;");
        FilaIntermedia f = r.filas().get(0);
        assertTrue(f.error().contains("no inicializada"));
    }

    @Test
    void desbordamiento_de_integer() {
        ResultadoIntermedio r = generar("A : Integer := 2147483647;\nB : Integer;", "B := A + 1;");
        assertTrue(r.filas().get(1).error().contains("desbordamiento"));
    }

    @Test
    void contenido_de_if_while_for_se_ignora_y_sus_asignaciones_invalidan() {
        ResultadoIntermedio r = generar("A : Integer := 1;\nB : Integer := 2;\nC : Integer;",
                "if A = 1 then\n  B := 99 + 1;\nend if;\nC := B + 1;");
        // declaraciones A y B, y C := B + 1 (con error: B quedó indeterminado). La asignación del if no genera fila.
        assertEquals(3, r.filas().size());
        FilaIntermedia f = r.filas().get(2);
        assertTrue(f.error().contains("if/while/for"), f.error());
        assertFalse(r.filas().stream().anyMatch(x -> x.fuente().contains("99")));
    }

    @Test
    void solo_cuenta_el_ultimo_procedimiento_no_las_funciones() {
        String fuente = """
                function Doble (X : Integer) return Integer is
                   Resultado : Integer;
                begin
                   Resultado := X * 2;
                end Doble;

                procedure Principal is
                   A : Integer := 4;
                begin
                   A := A + 1;
                end Principal;
                """;
        ResultadoCompilacion r = Compilador.analizar(fuente, "p.ada");
        assertFalse(r.tieneErrores(), r.erroresSemanticos().toString() + r.erroresSintacticos());
        assertEquals(2, r.intermedio().filas().size());
        assertEquals("5", valorFinal(r.intermedio(), "A"));
    }

    @Test
    void hexadecimal_y_guiones_bajos() {
        ResultadoIntermedio r = generar("A : Integer;\nB : Integer;", "A := 16#FF#;\nB := 1_000 + A;");
        assertEquals("255", r.filas().get(0).valor());
        assertEquals("1255", r.filas().get(1).valor());
    }

    @Test
    void reporte_en_texto_incluye_metodos_y_valores_finales() {
        ResultadoIntermedio r = generar("A : Integer := 2;", "A := A * 3;");
        String t = r.aTexto();
        assertTrue(t.contains("Prefijo"));
        assertTrue(t.contains("preorden"));
        assertTrue(t.contains("postorden"));
        assertTrue(t.contains("A : Integer = 6"), t);
    }
}
