package com.compiladorada.intermedio;

import com.compiladorada.semantico.LiteralAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico.FactorOp;
import com.compiladorada.semantico.VerificadorSemantico.SimpleOp;
import com.compiladorada.sintactico.nodos.ASTFactor;
import com.compiladorada.sintactico.nodos.ASTLiteral;
import com.compiladorada.sintactico.nodos.ASTNombre;
import com.compiladorada.sintactico.nodos.ASTSimple;
import com.compiladorada.sintactico.nodos.ASTTermino;
import com.compiladorada.sintactico.nodos.Node;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Convierte un subárbol de expresión del AST de JJTree en un
 * {@link ArbolExpresion}. Devuelve {@code null} si la expresión no es
 * puramente aritmética sobre literales y variables numéricas conocidas
 * (relacionales, lógicas, concatenación, llamadas, indexación, etc.).
 *
 * <p>Ada.jjt solo crea un nodo {@code Simple/Termino/Factor} cuando hay
 * operador y los paréntesis no generan nodo, así que cada operando de un
 * nodo es exactamente un hijo y la estructura del árbol ya refleja la
 * precedencia.
 */
final class ConstructorArbol {

    private final Predicate<String> variableNumerica;

    /** @param variableNumerica nombre (en minúsculas) → ¿es una variable/constante Integer o Float? */
    ConstructorArbol(Predicate<String> variableNumerica) {
        this.variableNumerica = variableNumerica;
    }

    ArbolExpresion construir(Node n) {
        if (n instanceof ASTLiteral) {
            return literal((LiteralAst) ((ASTLiteral) n).jjtGetValue());
        }
        if (n instanceof ASTNombre) {
            NombreAst nombre = (NombreAst) ((ASTNombre) n).jjtGetValue();
            boolean simple = nombre.segmentos().isEmpty();
            return simple && variableNumerica.test(nombre.base().toLowerCase(Locale.ROOT))
                    ? new ArbolExpresion.Variable(nombre.base())
                    : null;
        }
        if (n instanceof ASTSimple s) {
            SimpleOp op = (SimpleOp) s.jjtGetValue();
            return plegar(s, op.operadores(), op.signo());
        }
        if (n instanceof ASTTermino t) {
            @SuppressWarnings("unchecked")
            List<String> ops = (List<String>) t.jjtGetValue();
            return plegar(t, ops, null);
        }
        if (n instanceof ASTFactor f) {
            return factor(f, (FactorOp) f.jjtGetValue());
        }
        return null;
    }

    /** a op1 b op2 c … → asociatividad a la izquierda; el signo afecta al primer término. */
    private ArbolExpresion plegar(Node nodo, List<String> operadores, String signo) {
        if (nodo.jjtGetNumChildren() != operadores.size() + 1) {
            return null;
        }
        ArbolExpresion acc = construir(nodo.jjtGetChild(0));
        if (acc == null) {
            return null;
        }
        if ("-".equals(signo)) {
            acc = new ArbolExpresion.Unario("neg", acc);
        }
        for (int i = 0; i < operadores.size(); i++) {
            String op = operadores.get(i).toLowerCase(Locale.ROOT);
            ArbolExpresion der = construir(nodo.jjtGetChild(i + 1));
            if (der == null || "&".equals(op)) {
                return null;
            }
            acc = new ArbolExpresion.Binario(op, acc, der);
        }
        return acc;
    }

    private ArbolExpresion factor(Node nodo, FactorOp info) {
        if (nodo.jjtGetNumChildren() != (info.tienePotencia() ? 2 : 1)) {
            return null;
        }
        ArbolExpresion base = construir(nodo.jjtGetChild(0));
        if (base == null) {
            return null;
        }
        if ("abs".equals(info.unario())) {
            base = new ArbolExpresion.Unario("abs", base);
        } else if (info.unario() != null) {
            return null; // "not" no es aritmético
        }
        if (!info.tienePotencia()) {
            return base;
        }
        ArbolExpresion exp = construir(nodo.jjtGetChild(1));
        return exp == null ? null : new ArbolExpresion.Binario("**", base, exp);
    }

    private static ArbolExpresion literal(LiteralAst l) {
        boolean entero = l.tipo() == TipoAda.LITERAL_ENTERO;
        boolean real = l.tipo() == TipoAda.LITERAL_REAL;
        if (!entero && !real) {
            return null;
        }
        String t = l.texto().replace("_", "");
        try {
            if (real) {
                return new ArbolExpresion.Literal(l.texto(), Valor.real(Double.parseDouble(t)));
            }
            if (t.indexOf('#') >= 0) {
                return basado(l.texto(), t);
            }
            return new ArbolExpresion.Literal(l.texto(),
                    Valor.entero(new BigDecimal(t).longValueExact()));
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** {@code 16#FF#} o {@code 2#101#E3}; los basados con parte fraccionaria no se soportan. */
    private static ArbolExpresion basado(String textoOriginal, String t) {
        String[] partes = t.split("#", -1);
        if (partes.length != 3 || partes[1].indexOf('.') >= 0) {
            return null;
        }
        int base = Integer.parseInt(partes[0]);
        long valor = Long.parseLong(partes[1], base);
        if (!partes[2].isEmpty()) {
            long exp = Long.parseLong(partes[2].substring(1).replace("+", ""));
            if (exp < 0) {
                return null;
            }
            for (long i = 0; i < exp; i++) {
                valor = Math.multiplyExact(valor, (long) base);
            }
        }
        return new ArbolExpresion.Literal(textoOriginal, Valor.entero(valor));
    }
}
