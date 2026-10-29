package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ArbolExpresion;
import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import com.compiladorada.intermedio.Notaciones;
import com.compiladorada.intermedio.Valor;
import com.compiladorada.optimizacion.ReductorPotencia.ClaseNumerica;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TransformacionesTest {

    private static ArbolExpresion v(String n) { return new Variable(n); }
    private static ArbolExpresion n(long x) { return new Literal(Long.toString(x), Valor.entero(x)); }
    private static ArbolExpresion r(double x) { return new Literal(Double.toString(x), Valor.real(x)); }
    private static ArbolExpresion b(String op, ArbolExpresion i, ArbolExpresion d) { return new Binario(op, i, d); }
    private static ArbolExpresion u(String op, ArbolExpresion x) { return new Unario(op, x); }

    private static String pre(ArbolExpresion a) { return Notaciones.prefijo(a); }

    private static final Function<String, ClaseNumerica> CLASES = nombre -> switch (nombre) {
        case "A", "B" -> ClaseNumerica.ENTERO;
        case "F" -> ClaseNumerica.REAL;
        default -> ClaseNumerica.OTRA;
    };

    @Test
    void pliega_literales() {
        assertEquals("35", pre(PlegadorConstantes.plegar(b("+", b("**", n(2), n(5)), n(3)))));
        assertEquals("3", pre(PlegadorConstantes.plegar(b("/", n(7), n(2)))));
        assertEquals("1", pre(PlegadorConstantes.plegar(b("mod", n(7), n(3)))));
        assertEquals("neg 1", pre(PlegadorConstantes.plegar(b("rem", u("neg", n(7)), n(2)))));
        assertEquals("5.0", pre(PlegadorConstantes.plegar(b("*", r(2.5), r(2.0)))));
        assertEquals("4", pre(PlegadorConstantes.plegar(u("abs", u("neg", n(4))))));
        assertEquals("neg 3", pre(PlegadorConstantes.plegar(b("-", n(0), n(3)))));
        assertEquals("+ A 6", pre(PlegadorConstantes.plegar(b("+", v("A"), b("*", n(2), n(3))))));
    }

    @Test
    void no_pliega_lo_que_fallaria_en_ejecucion_ni_lo_que_no_cambia() {
        ArbolExpresion[] intactos = {
                b("/", n(1), n(0)),
                b("+", n(2147483647), n(1)),
                b("**", n(2), u("neg", n(1))),
                b("+", v("A"), v("B")),
                u("neg", n(3)),
        };
        for (ArbolExpresion a : intactos) {
            assertSame(a, PlegadorConstantes.plegar(a), pre(a));
        }
    }

    @Test
    void reduce_potencia() {
        assertEquals("* A A", pre(ReductorPotencia.reducir(b("**", v("A"), n(2)), CLASES)));
        assertEquals("+ A A", pre(ReductorPotencia.reducir(b("*", v("A"), n(2)), CLASES)));
        assertEquals("+ A A", pre(ReductorPotencia.reducir(b("*", n(2), v("A")), CLASES)));
        assertEquals("+ F F", pre(ReductorPotencia.reducir(b("*", v("F"), r(2.0)), CLASES)));
        assertEquals("* F 0.5", pre(ReductorPotencia.reducir(b("/", v("F"), r(2.0)), CLASES)));
        assertEquals("* + A A B", pre(ReductorPotencia.reducir(b("*", b("*", v("A"), n(2)), v("B")), CLASES)));
    }

    @Test
    void no_reduce_lo_que_no_cumple_las_condiciones() {
        ArbolExpresion[] intactos = {
                b("*", v("R"), n(2)),
                b("*", v("A"), n(3)),
                b("**", v("A"), n(3)),
                b("/", v("A"), n(2)),
                b("*", b("+", v("A"), v("B")), n(2)),
                b("*", v("A"), r(2.0)),
                b("**", v("R"), n(2)),
        };
        for (ArbolExpresion a : intactos) {
            assertSame(a, ReductorPotencia.reducir(a, CLASES), pre(a));
        }
    }
}
