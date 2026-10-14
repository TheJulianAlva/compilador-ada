package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvaluadorTest {

    private final Map<String, Valor> memoria = new HashMap<>();
    private final Map<String, String> sinValor = new HashMap<>();
    private final Evaluador ev = new Evaluador(memoria, sinValor);

    private static ArbolExpresion i(long v) {
        return new Literal(Long.toString(v), Valor.entero(v));
    }

    private static ArbolExpresion f(double v) {
        return new Literal(Double.toString(v), Valor.real(v));
    }

    private static ArbolExpresion bin(String op, ArbolExpresion a, ArbolExpresion b) {
        return new Binario(op, a, b);
    }

    private String val(ArbolExpresion a) throws Exception {
        return ev.evaluar(a).toString();
    }

    private String err(ArbolExpresion a) {
        return assertThrows(Evaluador.ErrorEvaluacion.class, () -> ev.evaluar(a)).getMessage();
    }

    @Test
    void aritmetica_entera_al_estilo_ada() throws Exception {
        assertEquals("3", val(bin("/", i(7), i(2))));
        assertEquals("-3", val(bin("/", i(-7), i(2))));
        assertEquals("2", val(bin("mod", i(-7), i(3))));
        assertEquals("-1", val(bin("rem", i(-7), i(3))));
        assertEquals("1024", val(bin("**", i(2), i(10))));
        assertEquals("1", val(bin("**", i(5), i(0))));
        assertEquals("-5", val(new Unario("neg", i(5))));
        assertEquals("5", val(new Unario("abs", i(-5))));
    }

    @Test
    void aritmetica_real() throws Exception {
        assertEquals("8.0", val(bin("**", f(2.0), i(3))));
        assertEquals("2.5", val(bin("/", f(5.0), f(2.0))));
    }

    @Test
    void errores_de_evaluacion_son_mensajes_no_excepciones_de_java() {
        assertTrue(err(bin("/", i(1), i(0))).contains("división entre cero"));
        assertTrue(err(bin("mod", i(5), i(0))).contains("división entre cero"));
        assertTrue(err(bin("/", f(1.0), f(0.0))).contains("división entre cero"));
        assertTrue(err(bin("**", i(2), i(-1))).contains("exponente negativo"));
        assertTrue(err(bin("+", i(Integer.MAX_VALUE), i(1))).contains("desbordamiento"));
        assertTrue(err(bin("**", i(2), i(1_000_000_000))).contains("desbordamiento"));
        assertTrue(err(bin("+", i(1), f(2.0))).contains("tipos incompatibles"));
        assertTrue(err(bin("mod", f(1.0), f(2.0))).contains("solo aplica a Integer"));
    }

    @Test
    void variables_usan_la_memoria_sin_distinguir_mayusculas() throws Exception {
        memoria.put("a", Valor.entero(4));
        assertEquals("9", val(bin("+", new Variable("A"), i(5))));
    }

    @Test
    void variable_sin_valor_informa_el_motivo() {
        sinValor.put("x", "se modifica dentro de if/while/for");
        assertTrue(err(new Variable("X")).contains("se modifica dentro"));
        assertTrue(err(new Variable("Y")).contains("no inicializada"));
    }
}
