package com.compiladorada.optimizacion;

import com.compiladorada.Compilador;
import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.intermedio.ValorFinal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OptimizadorTest {

    private static final String VARS = "A : Integer := 3;\nB : Integer := 4;\nX : Integer;\nF : Float := 1.5;\nY : Float;\n";

    private static String optimizar(String declaraciones, String sentencias) {
        String fuente = "procedure P is\n" + declaraciones + "begin\n" + sentencias + "\nend P;\n";
        ResultadoCompilacion r = Compilador.analizar(fuente, "p.ada");
        assertFalse(r.tieneErrores(), "debe ser válido: " + r.erroresSemanticos() + r.erroresSintacticos());
        assertEquals(fuente, r.optimizacion().original());
        return r.optimizacion().optimizado();
    }

    private static String cuerpo(String optimizado) {
        int i = optimizado.indexOf("begin\n") + "begin\n".length();
        int j = optimizado.lastIndexOf("\nend P;");
        return optimizado.substring(i, j);
    }

    @Test
    void compacta_el_programa_completo() {
        String fuente = "-- encabezado\nprocedure  P   is\n\n   A : Integer := 3 ;   -- var\nbegin\n   A := A  + 1 ;\n\nend P ;\n";
        ResultadoCompilacion r = Compilador.analizar(fuente, "p.ada");
        assertEquals("procedure P is\nA:Integer:=3;\nbegin\nA:=A+1;\nend P;", r.optimizacion().optimizado());
    }

    @Test
    void pliega_constantes_y_reduce_potencia() {
        assertEquals("X:=32+(A+A);", cuerpo(optimizar(VARS, "X := 2 ** 5 + A * 2;")));
        assertEquals("X:=(A+A)*B;", cuerpo(optimizar(VARS, "X := A * 2 * B;")));
        assertEquals("X:=B-(A+A);", cuerpo(optimizar(VARS, "X := B - A * 2;")));
        assertEquals("X:=A*A;", cuerpo(optimizar(VARS, "X := A ** 2;")));
        assertEquals("X:=A+6;", cuerpo(optimizar(VARS, "X := A + 2 * 3;")));
        assertEquals("Y:=F*0.5;", cuerpo(optimizar(VARS, "Y := F / 2.0;")));
        assertEquals("Y:=F+F;", cuerpo(optimizar(VARS, "Y := 2.0 * F;")));
        assertEquals("X:=(A+6)*B;", cuerpo(optimizar(VARS, "X := (A + 2 * 3) * B;")));
        assertEquals("X:=A*(-3);", cuerpo(optimizar(VARS, "X := A * (0 - 3);")));
        assertEquals("X:=A+(-3);", cuerpo(optimizar(VARS, "X := A + (0 - 3);")));
    }

    @Test
    void inicializadores_y_constantes() {
        String opt = optimizar("C : constant Integer := 3 * 4;\n", "null;");
        assertTrue(opt.contains("C:constant Integer:=12;"), opt);
    }

    @Test
    void llega_a_subexpresiones_de_condiciones_e_indices() {
        String opt = optimizar(VARS + "type Vec is array (1 .. 10) of Integer;\nT : Vec;\n",
                "if A > 2 * 3 then\nX := T(A * 2);\nend if;\nwhile B < 2 ** 3 loop\nB := B + 1;\nend loop;");
        assertEquals("if A>6 then\nX:=T(A+A);\nend if;\nwhile B<8 loop\nB:=B+1;\nend loop;", cuerpo(opt));
    }

    @Test
    void deja_intacto_lo_que_no_debe_tocar() {
        String decl = VARS + "type Nota is range 0 .. 100;\nG : Nota := 5;\nZ : Nota;\n";
        assertEquals("Z:=G*2;", cuerpo(optimizar(decl, "Z := G * 2;")));
        assertEquals("X:=A/0;", cuerpo(optimizar(VARS, "X := A / 0;")));
        assertEquals("X:=2147483647+1;", cuerpo(optimizar(VARS, "X := 2147483647 + 1;")));
        assertEquals("X:=A*3;", cuerpo(optimizar(VARS, "X := A * 3;")));
    }

    @Test
    void nombres_sombreados_con_otro_tipo_no_se_reducen() {
        String fuente = "procedure Q is\ntype Nota is range 0 .. 100;\nA : Nota := 5;\nY : Nota;\n"
                + "begin\nY := A * 2;\nend Q;\n"
                + "procedure P is\nA : Integer := 3;\nX : Integer;\nbegin\nX := A * 2;\nend P;\n";
        ResultadoCompilacion r = Compilador.analizar(fuente, "p.ada");
        assertFalse(r.tieneErrores(), "" + r.erroresSemanticos() + r.erroresSintacticos());
        String opt = r.optimizacion().optimizado();
        assertTrue(opt.contains("Y:=A*2;"), opt);
        assertTrue(opt.contains("X:=A+A;"), opt);
    }

    @Test
    void con_errores_no_hay_optimizacion() {
        ResultadoCompilacion r = Compilador.analizar("procedure P is X : Integer := Y; begin null; end P;", "p.ada");
        assertTrue(r.tieneErrores());
        assertNull(r.optimizacion());
        assertTrue(r.optimizacionOmitida());
    }

    // ------------------------------------------------ propiedades sobre ejemplos

    /** Nombre, tipo y valor de cada variable (el motivo cita números de línea, que cambian al compactar). */
    private static List<String> valores(ResultadoCompilacion r) {
        return r.intermedio().valoresFinales().stream()
                .map((ValorFinal v) -> v.nombre() + ":" + v.tipo() + "=" + v.valor() + (v.motivo() != null ? " (sin valor)" : ""))
                .toList();
    }

    static Stream<Path> ejemplos() throws IOException {
        try (Stream<Path> s = Files.list(Path.of("ejemplos"))) {
            return s.filter(p -> p.toString().endsWith(".ada")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("ejemplos")
    void el_optimizado_compila_es_idempotente_y_conserva_los_valores(Path ejemplo) throws IOException {
        String fuente = Files.readString(ejemplo);
        ResultadoCompilacion r = Compilador.analizar(fuente, ejemplo.getFileName().toString());
        if (r.tieneErrores()) {
            assertNull(r.optimizacion());
            return;
        }
        String opt = r.optimizacion().optimizado();

        ResultadoCompilacion r2 = Compilador.analizar(opt, "opt.ada");
        assertFalse(r2.tieneErrores(), ejemplo + " optimizado no compila:\n" + opt + "\n"
                + r2.erroresLexicos() + r2.erroresSintacticos() + r2.erroresSemanticos());
        assertEquals(opt, r2.optimizacion().optimizado(), "no es idempotente: " + ejemplo);

        assertEquals(valores(r), valores(r2), "cambian los valores finales: " + ejemplo);
        assertFalse(opt.replaceAll("\"[^\"\n]*\"", "\"\"").contains("--"), "queda un comentario en " + ejemplo);
        assertFalse(opt.contains("\n\n"), "queda una línea en blanco en " + ejemplo);
    }
}
