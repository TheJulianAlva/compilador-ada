package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotacionesTest {

    private static ArbolExpresion n(String t) {
        return new Literal(t, Valor.entero(Long.parseLong(t)));
    }

    private static ArbolExpresion v(String nombre) {
        return new Variable(nombre);
    }

    private static ArbolExpresion bin(String op, ArbolExpresion i, ArbolExpresion d) {
        return new Binario(op, i, d);
    }

    @Test
    void precedencia_suma_y_producto() {
        ArbolExpresion a = bin("+", v("A"), bin("*", v("B"), n("2")));
        assertEquals("+ A * B 2", Notaciones.prefijo(a));
        assertEquals("A B 2 * +", Notaciones.posfijo(a));
    }

    @Test
    void parentesis_cambian_la_estructura() {
        ArbolExpresion a = bin("*", bin("+", v("A"), v("B")), bin("-", v("C"), v("D")));
        assertEquals("* + A B - C D", Notaciones.prefijo(a));
        assertEquals("A B + C D - *", Notaciones.posfijo(a));
    }

    @Test
    void unarios() {
        ArbolExpresion a = new Unario("neg", new Unario("abs", v("A")));
        assertEquals("neg abs A", Notaciones.prefijo(a));
        assertEquals("A abs neg", Notaciones.posfijo(a));
    }

    @Test
    void hoja_sola() {
        assertEquals("5", Notaciones.prefijo(n("5")));
        assertEquals("X", Notaciones.posfijo(v("X")));
    }

    @Test
    void cuadruplos_con_temporales_y_asignacion_final() {
        ArbolExpresion a = bin("+", v("A"), bin("*", v("B"), n("2")));
        List<Cuadruplo> q = GeneradorCuadruplos.generar(a, List.of("X"));
        assertEquals(List.of("(*, B, 2, t1)", "(+, A, t1, t2)", "(:=, t2, _, X)"),
                q.stream().map(Cuadruplo::toString).toList());
    }

    @Test
    void cuadruplos_de_hoja_unario_y_varios_destinos() {
        assertEquals(List.of("(:=, 5, _, Y)"),
                GeneradorCuadruplos.generar(n("5"), List.of("Y")).stream().map(Cuadruplo::toString).toList());
        assertEquals(List.of("(neg, A, _, t1)"),
                GeneradorCuadruplos.generar(new Unario("neg", v("A")), List.of()).stream()
                        .map(Cuadruplo::toString).toList());
        assertEquals(List.of("(:=, 1, _, P)", "(:=, 1, _, Q)"),
                GeneradorCuadruplos.generar(n("1"), List.of("P", "Q")).stream()
                        .map(Cuadruplo::toString).toList());
    }
}
