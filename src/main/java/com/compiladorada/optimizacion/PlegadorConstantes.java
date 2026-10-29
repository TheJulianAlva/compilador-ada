package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ArbolExpresion;
import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;
import com.compiladorada.intermedio.Evaluador;
import com.compiladorada.intermedio.Valor;

import java.util.Map;

/**
 * Plegado de constantes: todo subárbol cuyas hojas son literales se sustituye
 * por su valor, calculado con el mismo {@link Evaluador} de la fase
 * intermedia. Si la evaluación fallaría en ejecución (división entre cero,
 * desbordamiento, exponente negativo) el subárbol se deja intacto para
 * conservar el error. Devuelve la misma instancia si nada cambió.
 */
final class PlegadorConstantes {

    private static final Evaluador EVALUADOR = new Evaluador(Map.of(), Map.of());

    private PlegadorConstantes() {
    }

    static ArbolExpresion plegar(ArbolExpresion a) {
        ArbolExpresion nuevo;
        if (a instanceof Unario u) {
            ArbolExpresion op = plegar(u.operando());
            nuevo = op == u.operando() ? u : new Unario(u.op(), op);
        } else if (a instanceof Binario b) {
            ArbolExpresion izq = plegar(b.izq());
            ArbolExpresion der = plegar(b.der());
            nuevo = izq == b.izq() && der == b.der() ? b : new Binario(b.op(), izq, der);
        } else {
            return a;
        }
        if (esMenosDeLiteral(nuevo) || !soloLiterales(nuevo)) {
            return nuevo;
        }
        try {
            return comoArbol(EVALUADOR.evaluar(nuevo));
        } catch (Evaluador.ErrorEvaluacion e) {
            return nuevo;
        }
    }

    /** {@code -3} ya es la forma canónica de un negativo: no se vuelve a plegar. */
    private static boolean esMenosDeLiteral(ArbolExpresion a) {
        return a instanceof Unario u && "neg".equals(u.op()) && u.operando() instanceof Literal;
    }

    private static boolean soloLiterales(ArbolExpresion a) {
        if (a instanceof Literal) {
            return true;
        }
        if (a instanceof Variable) {
            return false;
        }
        if (a instanceof Unario u) {
            return soloLiterales(u.operando());
        }
        Binario b = (Binario) a;
        return soloLiterales(b.izq()) && soloLiterales(b.der());
    }

    /** Un valor negativo se representa como menos unario sobre su magnitud. */
    private static ArbolExpresion comoArbol(Valor v) {
        if (v.esReal()) {
            double d = v.real();
            boolean negativo = Math.copySign(1.0, d) < 0;
            double magnitud = Math.abs(d);
            Literal l = new Literal(Double.toString(magnitud), Valor.real(magnitud));
            return negativo ? new Unario("neg", l) : l;
        }
        long x = v.entero();
        Literal l = new Literal(Long.toString(Math.abs(x)), Valor.entero(Math.abs(x)));
        return x < 0 ? new Unario("neg", l) : l;
    }
}
