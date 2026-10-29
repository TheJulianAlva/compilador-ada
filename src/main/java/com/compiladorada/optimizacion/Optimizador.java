package com.compiladorada.optimizacion;

import com.compiladorada.generado.Token;
import com.compiladorada.intermedio.ArbolExpresion;
import com.compiladorada.intermedio.ConstructorArbol;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import com.compiladorada.optimizacion.Compactador.Reemplazo;
import com.compiladorada.optimizacion.ReductorPotencia.ClaseNumerica;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.FuncionAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.PaqueteAst;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico.ParametroInfo;
import com.compiladorada.semantico.VerificadorSemantico.SimpleOp;
import com.compiladorada.sintactico.nodos.ASTDeclaracionVar;
import com.compiladorada.sintactico.nodos.ASTFactor;
import com.compiladorada.sintactico.nodos.ASTFor;
import com.compiladorada.sintactico.nodos.ASTFuncion;
import com.compiladorada.sintactico.nodos.ASTPaquete;
import com.compiladorada.sintactico.nodos.ASTParametro;
import com.compiladorada.sintactico.nodos.ASTProcedimiento;
import com.compiladorada.sintactico.nodos.ASTSimple;
import com.compiladorada.sintactico.nodos.ASTTermino;
import com.compiladorada.sintactico.nodos.Node;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Etapa de optimización sobre el mismo fuente: recorre el AST completo
 * buscando subárboles aritméticos maximales, los pliega y reduce, y vuelve a
 * escribir el programa compactado con esos subárboles sustituidos.
 *
 * <p>Debe invocarse solo con un AST sin errores léxicos, sintácticos ni
 * semánticos.
 */
public final class Optimizador {

    private final List<TokenLexico> tokens;
    private final List<TokenLexico> utiles = new ArrayList<>();
    private final Map<Long, Integer> indicePorPosicion = new HashMap<>();
    private final Map<Long, Reemplazo> reemplazos = new HashMap<>();
    private final Deque<Map<String, ClaseNumerica>> ambitos = new ArrayDeque<>();
    private final ConstructorArbol constructor = new ConstructorArbol(n -> clase(n) != ClaseNumerica.OTRA);

    private Optimizador(List<TokenLexico> tokens) {
        this.tokens = tokens;
        for (TokenLexico t : tokens) {
            if (t.tipo() != TipoToken.COMENTARIO) {
                indicePorPosicion.put(Compactador.clave(t.linea(), t.columna()), utiles.size());
                utiles.add(t);
            }
        }
    }

    public static ResultadoOptimizacion optimizar(String fuente, List<TokenLexico> tokens, SimpleNode ast) {
        Optimizador o = new Optimizador(tokens);
        o.ambitos.push(new HashMap<>());
        if (ast != null) {
            o.visitar(ast, null, 0);
        }
        return new ResultadoOptimizacion(fuente, Compactador.compactar(tokens, o.reemplazos));
    }

    // ---------------------------------------------------------------- ámbitos

    private ClaseNumerica clase(String nombre) {
        String clave = nombre.toLowerCase(Locale.ROOT);
        for (Map<String, ClaseNumerica> ambito : ambitos) {
            ClaseNumerica c = ambito.get(clave);
            if (c != null) {
                return c;
            }
        }
        return ClaseNumerica.OTRA;
    }

    private void declarar(String nombre, ClaseNumerica clase) {
        ambitos.peek().put(nombre.toLowerCase(Locale.ROOT), clase);
    }

    private static ClaseNumerica claseDe(TipoAda t) {
        if (TipoAda.INTEGER.equals(t)) {
            return ClaseNumerica.ENTERO;
        }
        return TipoAda.FLOAT.equals(t) ? ClaseNumerica.REAL : ClaseNumerica.OTRA;
    }

    // -------------------------------------------------------------- recorrido

    private void visitar(Node n, Node padre, int indice) {
        if (n instanceof ASTSimple || n instanceof ASTTermino || n instanceof ASTFactor) {
            ArbolExpresion arbol = constructor.construir(n);
            if (arbol instanceof ArbolExpresion.Binario || arbol instanceof ArbolExpresion.Unario) {
                sustituir((SimpleNode) n, padre, indice, arbol);
                return;
            }
        }
        if (n instanceof ASTProcedimiento p) {
            declarar(((NombreAst) p.jjtGetValue()).base(), ClaseNumerica.OTRA);
            hijosEnAmbito(n, null);
        } else if (n instanceof ASTFuncion f) {
            declarar(((FuncionAst) f.jjtGetValue()).nombre(), ClaseNumerica.OTRA);
            hijosEnAmbito(n, null);
        } else if (n instanceof ASTPaquete p) {
            PaqueteAst paquete = (PaqueteAst) p.jjtGetValue();
            declarar(paquete.nombre(), ClaseNumerica.OTRA);
            if (paquete.esBody()) {
                hijosEnAmbito(n, null);
            } else {
                visitarHijos(n); // la especificación es transparente: declara en el ámbito envolvente
            }
        } else if (n instanceof ASTFor f) {
            hijosEnAmbito(n, (DeclaracionVarAst) f.jjtGetValue());
        } else if (n instanceof ASTParametro p) {
            ParametroInfo info = (ParametroInfo) p.jjtGetValue();
            for (String nombre : info.nombres()) {
                declarar(nombre, claseDe(info.tipo()));
            }
        } else if (n instanceof ASTDeclaracionVar d) {
            visitarHijos(n);
            DeclaracionVarAst info = (DeclaracionVarAst) d.jjtGetValue();
            for (String nombre : info.nombres()) {
                declarar(nombre, claseDe(info.tipo()));
            }
        } else {
            visitarHijos(n);
        }
    }

    private void hijosEnAmbito(Node n, DeclaracionVarAst variableDeLazo) {
        ambitos.push(new HashMap<>());
        if (variableDeLazo != null) {
            for (String nombre : variableDeLazo.nombres()) {
                declarar(nombre, claseDe(variableDeLazo.tipo()));
            }
        }
        visitarHijos(n);
        ambitos.pop();
    }

    private void visitarHijos(Node n) {
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            visitar(n.jjtGetChild(i), n, i);
        }
    }

    // ------------------------------------------------------------ sustitución

    private void sustituir(SimpleNode nodo, Node padre, int indice, ArbolExpresion arbol) {
        ArbolExpresion nuevo = ReductorPotencia.reducir(PlegadorConstantes.plegar(arbol), this::clase);
        if (nuevo == arbol) {
            return;
        }
        Token ini = nodo.jjtGetFirstToken();
        Token fin = nodo.jjtGetLastToken();
        int requerido = 0;
        boolean cabeza = true;
        if (!entreParentesis(ini, fin)) {
            if (padre instanceof ASTSimple s) {
                SimpleOp op = (SimpleOp) s.jjtGetValue();
                if (indice == 0 && op.signo() == null) {
                    requerido = 2;
                } else {
                    requerido = 3;
                    cabeza = false;
                }
            } else if (padre instanceof ASTTermino) {
                requerido = indice == 0 ? 3 : 4;
                cabeza = false;
            } else if (padre instanceof ASTFactor) {
                requerido = 5;
                cabeza = false;
            }
        }
        reemplazos.put(Compactador.clave(ini.beginLine, ini.beginColumn),
                new Reemplazo(Impresor.imprimir(nuevo, requerido, cabeza),
                        Compactador.clave(fin.beginLine, fin.beginColumn)));
    }

    /** {@code true} si el rango está inmediatamente entre {@code (} y {@code )}. */
    private boolean entreParentesis(Token ini, Token fin) {
        Integer i = indicePorPosicion.get(Compactador.clave(ini.beginLine, ini.beginColumn));
        Integer j = indicePorPosicion.get(Compactador.clave(fin.beginLine, fin.beginColumn));
        return i != null && j != null && i > 0 && j + 1 < utiles.size()
                && utiles.get(i - 1).lexema().equals("(")
                && utiles.get(j + 1).lexema().equals(")");
    }
}
