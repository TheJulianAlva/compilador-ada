package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void cuadruplos_paso_a_paso_con_temporales_y_sin_asignacion() {
        ArbolExpresion a = bin("+", v("A"), bin("*", v("B"), n("2")));
        List<Cuadruplo> q = GeneradorCuadruplos.generar(a);
        assertEquals(List.of("1 | * | B | 2 | T1", "2 | + | A | T1 | T2"),
                q.stream().map(Cuadruplo::toString).toList());
    }

    @Test
    void cuadruplos_de_hoja_vacios_y_unario_con_arg2_vacio() {
        assertTrue(GeneradorCuadruplos.generar(n("5")).isEmpty());
        List<Cuadruplo> q = GeneradorCuadruplos.generar(new Unario("neg", v("A")));
        assertEquals(1, q.size());
        assertEquals(new Cuadruplo(1, "neg", "A", "", "T1"), q.get(0));
    }

    @Test
    void cuadruplos_de_expresion_con_parentesis() {
        ArbolExpresion a = bin("*", bin("+", v("A"), v("B")), bin("-", v("C"), v("D")));
        assertEquals(List.of("1 | + | A | B | T1", "2 | - | C | D | T2", "3 | * | T1 | T2 | T3"),
                GeneradorCuadruplos.generar(a).stream().map(Cuadruplo::toString).toList());
    }
}
