package com.compiladorada.intermedio;

import com.compiladorada.generado.Token;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.sintactico.nodos.ASTAsignacion;
import com.compiladorada.sintactico.nodos.ASTDeclaracionVar;
import com.compiladorada.sintactico.nodos.ASTFor;
import com.compiladorada.sintactico.nodos.ASTIf;
import com.compiladorada.sintactico.nodos.ASTLlamadaProc;
import com.compiladorada.sintactico.nodos.ASTNombre;
import com.compiladorada.sintactico.nodos.ASTProcedimiento;
import com.compiladorada.sintactico.nodos.ASTWhile;
import com.compiladorada.sintactico.nodos.Node;
import com.compiladorada.sintactico.nodos.SimpleNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recorre el cuerpo principal del programa (el último procedimiento de nivel
 * superior) en orden y, para cada declaración con valor inicial, asignación o
 * argumento aritmético de una llamada, produce una {@link FilaIntermedia}:
 * prefijo, posfijo, cuádruplos y valor. Mantiene una memoria de variables que
 * se actualiza en orden de aparición.
 *
 * <p>No entra a {@code if}/{@code while}/{@code for}: solo marca como "sin
 * valor" las variables que esas construcciones asignan, para no mostrar un
 * valor que depende del flujo de ejecución. Debe invocarse solo con un AST sin
 * errores léxicos, sintácticos ni semánticos.
 */
public final class GeneradorIntermedio {

    private enum Clase { ENTERO, REAL }

    private final Map<String, Clase> clases = new HashMap<>();
    private final Map<String, Valor> memoria = new HashMap<>();
    private final Map<String, String> sinValor = new HashMap<>();
    /** Variables numéricas en orden de declaración: clave en minúsculas → nombre original. */
    private final Map<String, String> declaradas = new LinkedHashMap<>();
    private final Map<String, String> tiposDeclarados = new HashMap<>();
    private final List<FilaIntermedia> filas = new ArrayList<>();
    private final ConstructorArbol constructor = new ConstructorArbol(clases::containsKey);
    private final Evaluador evaluador = new Evaluador(memoria, sinValor);

    private GeneradorIntermedio() {
    }

    public static ResultadoIntermedio generar(SimpleNode raiz) {
        GeneradorIntermedio g = new GeneradorIntermedio();
        ASTProcedimiento principal = null;
        for (int i = 0; raiz != null && i < raiz.jjtGetNumChildren(); i++) {
            if (raiz.jjtGetChild(i) instanceof ASTProcedimiento p) {
                principal = p;
            }
        }
        if (principal != null) {
            g.recorrer(principal);
        }
        return g.resultado();
    }

    private void recorrer(ASTProcedimiento principal) {
        for (int i = 0; i < principal.jjtGetNumChildren(); i++) {
            Node hijo = principal.jjtGetChild(i);
            if (hijo instanceof ASTDeclaracionVar d) {
                declaracion(d);
            } else if (hijo instanceof ASTAsignacion a) {
                asignacion(a);
            } else if (hijo instanceof ASTLlamadaProc c) {
                llamada(c);
            } else if (hijo instanceof ASTIf || hijo instanceof ASTFor || hijo instanceof ASTWhile) {
                invalidarAsignadas(hijo);
            }
        }
    }

    // ------------------------------------------------------------ sentencias

    private void declaracion(ASTDeclaracionVar nodo) {
        DeclaracionVarAst d = (DeclaracionVarAst) nodo.jjtGetValue();
        Clase clase = clasificar(d.tipo());
        if (clase == null) {
            return;
        }
        for (String nombre : d.nombres()) {
            String clave = nombre.toLowerCase(Locale.ROOT);
            clases.put(clave, clase);
            declaradas.put(clave, nombre);
            tiposDeclarados.put(clave, d.tipo().nombre());
            memoria.remove(clave);
            sinValor.put(clave, "no inicializada");
        }
        if (nodo.jjtGetNumChildren() == 1) {
            procesar(nodo, nodo.jjtGetChild(0), d.nombres(), true);
        }
    }

    private void asignacion(ASTAsignacion nodo) {
        if (nodo.jjtGetNumChildren() != 2 || !(nodo.jjtGetChild(0) instanceof ASTNombre n)) {
            return;
        }
        NombreAst destino = (NombreAst) n.jjtGetValue();
        String clave = destino.base().toLowerCase(Locale.ROOT);
        if (!destino.segmentos().isEmpty() || !clases.containsKey(clave)) {
            return;
        }
        boolean fila = procesar(nodo, nodo.jjtGetChild(1), List.of(destino.base()), true);
        if (!fila) {
            // expresión fuera del alcance (p. ej. llamada a función): el valor queda desconocido
            memoria.remove(clave);
            sinValor.put(clave, "se asigna con una expresión no aritmética");
        }
    }

    /** Argumentos aritméticos de una llamada tipo {@code Put_Line(A + B)}: se generan, no escriben en memoria. */
    private void llamada(ASTLlamadaProc nodo) {
        if (nodo.jjtGetNumChildren() != 1 || !(nodo.jjtGetChild(0) instanceof ASTNombre n)) {
            return;
        }
        NombreAst nombre = (NombreAst) n.jjtGetValue();
        List<NombreAst.Segmento> segs = nombre.segmentos();
        if (segs.isEmpty() || !(segs.get(segs.size() - 1) instanceof NombreAst.Segmento.Indexacion)) {
            return;
        }
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            Node arg = n.jjtGetChild(i);
            ArbolExpresion arbol = constructor.construir(arg);
            if (arbol instanceof ArbolExpresion.Binario || arbol instanceof ArbolExpresion.Unario) {
                procesar(arg, arg, List.of(), false);
            }
        }
    }

    /** Marca como sin valor toda variable asignada dentro de un if/while/for. */
    private void invalidarAsignadas(Node nodo) {
        if (nodo instanceof ASTAsignacion && nodo.jjtGetNumChildren() > 0
                && nodo.jjtGetChild(0) instanceof ASTNombre n) {
            String clave = ((NombreAst) n.jjtGetValue()).base().toLowerCase(Locale.ROOT);
            if (clases.containsKey(clave)) {
                memoria.remove(clave);
                sinValor.put(clave, "se modifica dentro de if/while/for (valor indeterminado)");
            }
        }
        for (int i = 0; i < nodo.jjtGetNumChildren(); i++) {
            invalidarAsignadas(nodo.jjtGetChild(i));
        }
    }

    // --------------------------------------------------------------- núcleo

    /**
     * @param sentencia nodo cuyo texto fuente se muestra
     * @param guardar   si {@code true}, el valor se almacena en cada destino
     * @return {@code true} si la expresión era aritmética y se generó una fila
     */
    private boolean procesar(Node sentencia, Node expresion, List<String> destinos, boolean guardar) {
        ArbolExpresion arbol = constructor.construir(expresion);
        if (arbol == null) {
            return false;
        }
        int linea = ((SimpleNode) sentencia).jjtGetFirstToken().beginLine;
        String valor = null;
        String tipo = null;
        String error = null;
        try {
            Valor v = evaluador.evaluar(arbol);
            valor = v.toString();
            tipo = v.tipo();
            if (guardar) {
                for (String d : destinos) {
                    Clase esperada = clases.get(d.toLowerCase(Locale.ROOT));
                    if (esperada != null && (esperada == Clase.REAL) != v.esReal()) {
                        throw new Evaluador.ErrorEvaluacion("no se puede asignar " + v.tipo()
                                + " a '" + d + "' (" + tiposDeclarados.get(d.toLowerCase(Locale.ROOT)) + ")");
                    }
                }
                for (String d : destinos) {
                    String clave = d.toLowerCase(Locale.ROOT);
                    memoria.put(clave, v);
                    sinValor.remove(clave);
                }
            }
        } catch (Evaluador.ErrorEvaluacion e) {
            valor = null;
            tipo = null;
            error = e.getMessage();
            if (guardar) {
                for (String d : destinos) {
                    String clave = d.toLowerCase(Locale.ROOT);
                    memoria.remove(clave);
                    sinValor.put(clave, "falló la expresión de la línea " + linea);
                }
            }
        }
        filas.add(new FilaIntermedia(linea, textoFuente((SimpleNode) sentencia), tipo,
                Notaciones.prefijo(arbol), Notaciones.posfijo(arbol),
                GeneradorCuadruplos.generar(arbol, guardar ? destinos : List.of()), valor, error));
        return true;
    }

    private ResultadoIntermedio resultado() {
        List<ValorFinal> finales = new ArrayList<>();
        for (Map.Entry<String, String> e : declaradas.entrySet()) {
            Valor v = memoria.get(e.getKey());
            finales.add(new ValorFinal(e.getValue(), tiposDeclarados.get(e.getKey()),
                    v != null ? v.toString() : null, sinValor.get(e.getKey())));
        }
        return new ResultadoIntermedio(List.copyOf(filas), List.copyOf(finales), ResultadoIntermedio.METODOS);
    }

    // ------------------------------------------------------------- utilidades

    private static Clase clasificar(TipoAda t) {
        if (t instanceof TipoAda.TipoSubtipo s) {
            return clasificar(s.base());
        }
        if (t.equals(TipoAda.INTEGER) || t instanceof TipoAda.TipoRango) {
            return Clase.ENTERO;
        }
        return t.equals(TipoAda.FLOAT) ? Clase.REAL : null;
    }

    /** Reconstruye el texto original del nodo uniendo sus tokens (espacio solo donde lo había). */
    private static String textoFuente(SimpleNode nodo) {
        Token t = nodo.jjtGetFirstToken();
        Token ultimo = nodo.jjtGetLastToken();
        StringBuilder sb = new StringBuilder(t.image);
        while (t != ultimo && t.next != null) {
            Token sig = t.next;
            if (sig.beginLine != t.endLine || sig.beginColumn > t.endColumn + 1) {
                sb.append(' ');
            }
            sb.append(sig.image);
            t = sig;
        }
        return sb.toString();
    }
}
