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
    void memoria_acumulativa_cuadruplos_y_notaciones() {
        ResultadoIntermedio r = generar("A : Integer := 5;\nB : Integer;\nC : Integer;",
                "B := A * 2 + 1;\nC := (A + B) * (B - 3);");
        // 'A : Integer := 5' no tiene operador: no genera bloque, pero A queda en memoria
        assertEquals(2, r.filas().size());

        FilaIntermedia f1 = r.filas().get(0);
        assertEquals("B := A * 2 + 1;", f1.fuente());
        assertEquals("+ * A 2 1", f1.prefijo());
        assertEquals("A 2 * 1 +", f1.posfijo());
        assertEquals("11", f1.valor());
        assertEquals("Integer", f1.tipo());
        assertEquals(List.of("1 | * | A | 2 | T1", "2 | + | T1 | 1 | T2"),
                f1.cuadruplos().stream().map(Object::toString).toList());

        FilaIntermedia f2 = r.filas().get(1);
        assertEquals("* + A B - B 3", f2.prefijo());
        assertEquals("A B + B 3 - *", f2.posfijo());
        assertEquals("128", f2.valor());
        // numeración y temporales se reinician en cada expresión
        assertEquals("1 | + | A | B | T1", f2.cuadruplos().get(0).toString());

        assertEquals("5", valorFinal(r, "A"));
        assertEquals("11", valorFinal(r, "B"));
        assertEquals("128", valorFinal(r, "C"));
    }

    @Test
    void asignaciones_simples_no_generan_bloque_pero_actualizan_la_memoria() {
        ResultadoIntermedio r = generar("A : Integer;\nB : Integer;", "A := 4;\nB := A;");
        assertTrue(r.filas().isEmpty());
        assertEquals("4", valorFinal(r, "A"));
        assertEquals("4", valorFinal(r, "B"));
        assertFalse(r.aTexto().contains(":="), r.aTexto());
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
        assertEquals("Float", r.filas().get(1).tipo());
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
        assertEquals(1, r.filas().size());
        FilaIntermedia f = r.filas().get(0);
        assertEquals("8", f.valor());
        assertEquals("A + B * 2", f.fuente());
        assertEquals(List.of("1 | * | B | 2 | T1", "2 | + | A | T1 | T2"),
                f.cuadruplos().stream().map(Object::toString).toList());
        assertEquals("2", valorFinal(r, "A"));
    }

    @Test
    void argumento_que_no_es_operacion_no_genera_fila() {
        ResultadoIntermedio r = conProcedimientoAuxiliar("A : Integer := 2;", "Mostrar(A);");
        assertEquals(0, r.filas().size());
    }

    @Test
    void division_entre_cero_es_error_de_fila() {
        ResultadoIntermedio r = generar("A : Integer := 0;\nB : Integer;\nC : Integer;",
                "B := 10 / A;\nC := B + 1;");
        FilaIntermedia f = r.filas().get(0);
        assertNull(f.valor());
        assertTrue(f.error().contains("división entre cero"));
        assertEquals(1, f.cuadruplos().size()); // los pasos se muestran aunque la evaluación falle
        FilaIntermedia dependiente = r.filas().get(1);
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
        assertTrue(r.filas().get(0).error().contains("desbordamiento"));
    }

    @Test
    void contenido_de_if_while_for_se_ignora_y_sus_asignaciones_invalidan() {
        ResultadoIntermedio r = generar("A : Integer := 1;\nB : Integer := 2;\nC : Integer;",
                "if A = 1 then\n  B := 99 + 1;\nend if;\nC := B + 1;");
        // solo C := B + 1 (con error: B quedó indeterminado). Lo del if no genera bloques.
        assertEquals(1, r.filas().size());
        FilaIntermedia f = r.filas().get(0);
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
        assertEquals(1, r.intermedio().filas().size());
        assertEquals("5", valorFinal(r.intermedio(), "A"));
    }

    @Test
    void hexadecimal_y_guiones_bajos() {
        ResultadoIntermedio r = generar("A : Integer;\nB : Integer;", "A := 16#FF#;\nB := 1_000 + A;");
        assertEquals(1, r.filas().size());
        assertEquals("1255", r.filas().get(0).valor());
        assertEquals("255", valorFinal(r, "A"));
    }

    @Test
    void reporte_en_texto_incluye_metodos_y_valores_finales() {
        ResultadoIntermedio r = generar("A : Integer := 2;", "A := A * 3;");
        String t = r.aTexto();
        assertFalse(t.contains("CÓDIGO INTERMEDIO"), t);
        assertFalse(t.contains("Expresiones procesadas"), t);
        assertTrue(t.startsWith("Línea "), t);
        assertTrue(t.contains("# | Operador | Arg 1 | Arg 2 | Resultado"), t);
        assertTrue(t.contains("1 | *        | A     | 3     | T1"), t);
        assertTrue(t.indexOf("Prefijo") > t.indexOf("Operador"), "primero los cuádruplos, luego prefijo");
        assertTrue(t.indexOf("Posfijo") > t.indexOf("Prefijo"));
        assertTrue(t.indexOf("Resultado:") > t.indexOf("Posfijo"));
        assertFalse(t.substring(t.indexOf("Operador"), t.indexOf("Prefijo")).contains(":="), t);
        assertTrue(t.contains("preorden"));
        assertTrue(t.contains("postorden"));
        assertTrue(t.contains("A : Integer = 6"), t);
    }
}
