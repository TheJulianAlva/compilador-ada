package com.compiladorada.optimizacion;

import com.compiladorada.Compilador;
import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.intermedio.ArbolExpresion;
import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import com.compiladorada.intermedio.Notaciones;
import com.compiladorada.intermedio.Valor;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ImpresorTest {

    private static ArbolExpresion v(String n) { return new Variable(n); }
    private static ArbolExpresion n(int x) { return new Literal(Integer.toString(x), Valor.entero(x)); }
    private static ArbolExpresion b(String op, ArbolExpresion i, ArbolExpresion d) { return new Binario(op, i, d); }
    private static ArbolExpresion u(String op, ArbolExpresion x) { return new Unario(op, x); }

    private static String texto(ArbolExpresion a) {
        return Compactador.unir(Impresor.imprimir(a, 0, true));
    }

    @Test
    void parentesis_solo_donde_cambiarian_el_arbol() {
        assertEquals("A+(B+C)", texto(b("+", v("A"), b("+", v("B"), v("C")))));
        assertEquals("A+B+C", texto(b("+", b("+", v("A"), v("B")), v("C"))));
        assertEquals("(A+A)*B", texto(b("*", b("+", v("A"), v("A")), v("B"))));
        assertEquals("A-(B+B)", texto(b("-", v("A"), b("+", v("B"), v("B")))));
        assertEquals("A/(B*C)", texto(b("/", v("A"), b("*", v("B"), v("C")))));
        assertEquals("A*B*C", texto(b("*", b("*", v("A"), v("B")), v("C"))));
    }

    @Test
    void menos_unario_solo_encabeza_cadenas_aditivas() {
        assertEquals("-A*B+C", texto(b("+", u("neg", b("*", v("A"), v("B"))), v("C"))));
        assertEquals("(-A)*B", texto(b("*", u("neg", v("A")), v("B"))));
        assertEquals("A*(-3)", texto(b("*", v("A"), u("neg", n(3)))));
        assertEquals("A+(-3)", texto(b("+", v("A"), u("neg", n(3)))));
        assertEquals("A**(-1)", texto(b("**", v("A"), u("neg", n(1)))));
        assertEquals("-(A+B)", texto(u("neg", b("+", v("A"), v("B")))));
    }

    @Test
    void abs_y_potencia() {
        assertEquals("(abs A)**2", texto(b("**", u("abs", v("A")), n(2))));
        assertEquals("abs A", texto(u("abs", v("A"))));
        assertEquals("abs(A+B)", texto(u("abs", b("+", v("A"), v("B")))));
        assertEquals("2**(3**2)", texto(b("**", n(2), b("**", n(3), n(2)))));
        assertEquals("(A+B)**2", texto(b("**", b("+", v("A"), v("B")), n(2))));
    }

    @Test
    void contexto_exigente_parentiza_la_raiz() {
        assertEquals("(A+A)", Compactador.unir(Impresor.imprimir(b("+", v("A"), v("A")), 3, false)));
        assertEquals("(-A)", Compactador.unir(Impresor.imprimir(u("neg", v("A")), 0, false)));
    }

    private static ArbolExpresion aleatorio(Random r, int prof) {
        if (prof == 0 || r.nextInt(4) == 0) {
            return switch (r.nextInt(3)) {
                case 0 -> v("A");
                case 1 -> v("B");
                default -> n(1 + r.nextInt(5));
            };
        }
        return switch (r.nextInt(9)) {
            case 0 -> u("neg", aleatorio(r, prof - 1));
            case 1 -> u("abs", aleatorio(r, prof - 1));
            default -> b(new String[]{"+", "-", "*", "/", "mod", "rem", "**"}[r.nextInt(7)],
                    aleatorio(r, prof - 1), aleatorio(r, prof - 1));
        };
    }

    @Test
    void el_texto_impreso_vuelve_a_analizarse_al_mismo_arbol() {
        Random r = new Random(42);
        int comprobados = 0;
        for (int i = 0; i < 400; i++) {
            ArbolExpresion a = aleatorio(r, 4);
            if (!(a instanceof Binario || a instanceof Unario)) {
                continue;
            }
            String texto = texto(a);
            String fuente = "procedure P is\nA : Integer := 1;\nB : Integer := 2;\nX : Integer;\n"
                    + "begin\nX := " + texto + ";\nend P;\n";
            ResultadoCompilacion res = Compilador.analizar(fuente, "p.ada");
            assertFalse(res.tieneErrores(), texto + " -> " + res.erroresSintacticos() + res.erroresSemanticos());
            assertEquals(Notaciones.prefijo(a), res.intermedio().filas().get(0).prefijo(), texto);
            comprobados++;
        }
        assertTrue(comprobados > 200);
    }
}
