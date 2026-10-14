package com.compiladorada.intermedio;

import com.compiladorada.intermedio.ArbolExpresion.Binario;
import com.compiladorada.intermedio.ArbolExpresion.Literal;
import com.compiladorada.intermedio.ArbolExpresion.Unario;
import com.compiladorada.intermedio.ArbolExpresion.Variable;

import java.util.Locale;
import java.util.Map;

/**
 * Evalúa un {@link ArbolExpresion} en postorden contra una memoria
 * {@code variable → valor} (claves en minúsculas, Ada no distingue
 * mayúsculas). Integer se acota a 32 bits; Float es {@code double}. Los
 * errores que Ada reportaría como {@code Constraint_Error} (división entre
 * cero, desbordamiento, exponente negativo) y la mezcla de tipos salen como
 * {@link ErrorEvaluacion}, no como excepciones de Java.
 */
public final class Evaluador {

    public static final class ErrorEvaluacion extends Exception {
        public ErrorEvaluacion(String mensaje) {
            super(mensaje);
        }
    }

    private final Map<String, Valor> memoria;
    /** Variables declaradas que hoy no tienen valor, con el motivo. */
    private final Map<String, String> sinValor;

    public Evaluador(Map<String, Valor> memoria, Map<String, String> sinValor) {
        this.memoria = memoria;
        this.sinValor = sinValor;
    }

    public Valor evaluar(ArbolExpresion a) throws ErrorEvaluacion {
        if (a instanceof Literal l) {
            return l.valor();
        }
        if (a instanceof Variable v) {
            String clave = v.nombre().toLowerCase(Locale.ROOT);
            Valor valor = memoria.get(clave);
            if (valor == null) {
                String motivo = sinValor.getOrDefault(clave, "no inicializada");
                throw new ErrorEvaluacion("'" + v.nombre() + "' no tiene valor: " + motivo);
            }
            return valor;
        }
        if (a instanceof Unario u) {
            return unario(u.op(), evaluar(u.operando()));
        }
        Binario b = (Binario) a;
        return binario(b.op(), evaluar(b.izq()), evaluar(b.der()));
    }

    private static Valor unario(String op, Valor x) throws ErrorEvaluacion {
        if (x.esReal()) {
            return Valor.real("neg".equals(op) ? -x.real() : Math.abs(x.real()));
        }
        return Valor.entero(acotar("neg".equals(op) ? -x.entero() : Math.abs(x.entero())));
    }

    private static Valor binario(String op, Valor izq, Valor der) throws ErrorEvaluacion {
        if ("**".equals(op)) {
            return potencia(izq, der);
        }
        if (izq.esReal() != der.esReal()) {
            throw new ErrorEvaluacion("tipos incompatibles en '" + op + "': "
                    + izq.tipo() + " y " + der.tipo());
        }
        if (izq.esReal()) {
            return real(op, izq.real(), der.real());
        }
        return entero(op, izq.entero(), der.entero());
    }

    private static Valor real(String op, double a, double b) throws ErrorEvaluacion {
        double r;
        switch (op) {
            case "+" -> r = a + b;
            case "-" -> r = a - b;
            case "*" -> r = a * b;
            case "/" -> {
                if (b == 0.0) {
                    throw new ErrorEvaluacion("división entre cero (Constraint_Error)");
                }
                r = a / b;
            }
            default -> throw new ErrorEvaluacion("'" + op + "' solo aplica a Integer");
        }
        if (Double.isInfinite(r) || Double.isNaN(r)) {
            throw new ErrorEvaluacion("desbordamiento de Float (Constraint_Error)");
        }
        return Valor.real(r);
    }

    private static Valor entero(String op, long a, long b) throws ErrorEvaluacion {
        try {
            long r;
            switch (op) {
                case "+" -> r = Math.addExact(a, b);
                case "-" -> r = Math.subtractExact(a, b);
                case "*" -> r = Math.multiplyExact(a, b);
                case "/" -> r = a / divisor(b);
                case "mod" -> r = Math.floorMod(a, divisor(b));
                case "rem" -> r = a % divisor(b);
                default -> throw new ErrorEvaluacion("operador desconocido '" + op + "'");
            }
            return Valor.entero(acotar(r));
        } catch (ArithmeticException e) {
            throw desbordamiento();
        }
    }

    private static Valor potencia(Valor base, Valor exp) throws ErrorEvaluacion {
        if (exp.esReal()) {
            throw new ErrorEvaluacion("el exponente de ** debe ser Integer, no Float");
        }
        long n = exp.entero();
        if (base.esReal()) {
            return real("*", Math.pow(base.real(), n), 1.0);
        }
        if (n < 0) {
            throw new ErrorEvaluacion("exponente negativo en ** con base Integer (Constraint_Error)");
        }
        long b = base.entero();
        if (b == 0 || b == 1) {
            return Valor.entero(n == 0 ? 1 : b);
        }
        if (b == -1) {
            return Valor.entero(n % 2 == 0 ? 1 : -1);
        }
        long r = 1;
        try {
            for (long i = 0; i < n; i++) {
                r = Math.multiplyExact(r, b);
                acotar(r);
            }
        } catch (ArithmeticException e) {
            throw desbordamiento();
        }
        return Valor.entero(r);
    }

    private static long divisor(long b) throws ErrorEvaluacion {
        if (b == 0) {
            throw new ErrorEvaluacion("división entre cero (Constraint_Error)");
        }
        return b;
    }

    private static long acotar(long r) throws ErrorEvaluacion {
        if (r < Integer.MIN_VALUE || r > Integer.MAX_VALUE) {
            throw desbordamiento();
        }
        return r;
    }

    private static ErrorEvaluacion desbordamiento() {
        return new ErrorEvaluacion("desbordamiento de Integer (Constraint_Error)");
    }
}
