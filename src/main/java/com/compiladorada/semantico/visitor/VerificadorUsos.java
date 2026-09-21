package com.compiladorada.semantico.visitor;

import com.compiladorada.semantico.Ambito;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.Simbolo;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.*;

import java.util.List;
import java.util.Map;

/**
 * Implementación B, pasada 2: reentra los MISMOS ámbitos que
 * {@link RecolectorDeclaraciones} (pasada 1) creó — usando la asociación
 * nodo→Ambito que esa pasada dejó — y verifica cada uso, resolviendo tipos
 * de expresión de abajo hacia arriba mediante despacho recursivo del
 * visitor (cada {@code visit} de un nodo de expresión llama
 * {@code child.jjtAccept(this, data)} sobre sus hijos y castea el resultado
 * a TipoAda). Usa {@code resolverUsoConOrden} en vez de {@code resolverUso}
 * porque la Pasada 1 ya registró TODAS las declaraciones del ámbito antes de
 * que esta pasada revise ningún uso — sin ese chequeo de orden, B aceptaría
 * usos-antes-de-declarar que la Implementación A rechaza.
 */
public final class VerificadorUsos extends AdaParserDefaultVisitor {

    private final VerificadorSemantico verificador;
    private final Map<Node, Ambito> ambitosPorNodo;

    private VerificadorUsos(VerificadorSemantico verificador, Map<Node, Ambito> ambitosPorNodo) {
        this.verificador = verificador;
        this.ambitosPorNodo = ambitosPorNodo;
    }

    /** Corre la pasada 2 sobre la raíz, reutilizando el VerificadorSemantico
     * y la asociación nodo→ámbito que dejó {@link RecolectorDeclaraciones}. */
    public static void verificar(SimpleNode raiz, RecolectorDeclaraciones pasada1) {
        VerificadorUsos v = new VerificadorUsos(pasada1.verificador(), pasada1.ambitosPorNodo());
        raiz.jjtAccept(v, null);
    }

    private TipoAda tipo(Node hijo, Object data) {
        return (TipoAda) hijo.jjtAccept(this, data);
    }

    // ---------------------------------------------------- ámbitos (reentrar)

    @Override
    public Object visit(ASTProcedimiento node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTFuncion node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTPaquete node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTFor node, Object data) {
        verificador.entrarAmbitoExistente(ambitosPorNodo.get(node));
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    // ------------------------------------------------------ sentencias/decl.

    @Override
    public Object visit(ASTDeclaracionVar node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        // El único hijo posible es el inicializador opcional.
        if (node.jjtGetNumChildren() > 0) {
            TipoAda origen = tipo(node.jjtGetChild(0), data);
            verificador.verificarInicializacion(info.tipo(), origen, info.linea(), info.columna());
        }
        return data;
    }

    @Override
    public Object visit(ASTAsignacion node, Object data) {
        // Hijo 0: el Nombre destino. Hijo 1: la expresión origen (si no se
        // colapsó a un Nombre/Literal directo, puede ser Expresion/Simple/...).
        SimpleNode nombreDestino = (SimpleNode) node.jjtGetChild(0);
        NombreAst destinoInfo = (NombreAst) nombreDestino.jjtGetValue();
        Simbolo destino = verificador.resolverUsoConOrden(destinoInfo.base(),
                destinoInfo.linea(), destinoInfo.columna());
        TipoAda tipoDestino = resolverCadena(destino != null ? destino.tipo() : TipoAda.DESCONOCIDO,
                destinoInfo, destino, data, nombreDestino);
        verificador.verificarAsignacionMutabilidad(destino, destinoInfo.linea(), destinoInfo.columna());
        TipoAda origen = tipo(node.jjtGetChild(1), data);
        verificador.verificarInicializacion(tipoDestino, origen, destinoInfo.linea(), destinoInfo.columna());
        return data;
    }

    @Override
    public Object visit(ASTLlamadaProc node, Object data) {
        tipo(node.jjtGetChild(0), data);
        return data;
    }

    @Override
    public Object visit(ASTIf node, Object data) {
        // Cada condición es uno de los primeros hijos "sueltos" antes de que
        // empiecen las sentencias del cuerpo; como el árbol no distingue eso
        // estructuralmente, se recorre igual que childrenAccept: cada hijo
        // que sea un nodo de expresión (no una sentencia) se tipa aquí, y el
        // resto se delega. Dado que las condiciones son las ÚNICAS
        // expresiones sueltas bajo #If (las sentencias de sus ramas cuelgan
        // de sus propios nodos con nombre — Asignacion, If, For, While,
        // LlamadaProc — nunca de un nodo de expresión desnudo), basta con
        // detectar, entre los hijos directos de este nodo, cuáles NO son
        // ninguno de esos cinco tipos de sentencia: esos son condiciones.
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            Node hijo = node.jjtGetChild(i);
            if (esNodoDeExpresion(hijo)) {
                TipoAda t = tipo(hijo, data);
                verificador.verificarCondicion(t, node.jjtGetFirstToken().beginLine,
                        node.jjtGetFirstToken().beginColumn);
            } else {
                hijo.jjtAccept(this, data);
            }
        }
        return data;
    }

    @Override
    public Object visit(ASTWhile node, Object data) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            Node hijo = node.jjtGetChild(i);
            if (esNodoDeExpresion(hijo)) {
                TipoAda t = tipo(hijo, data);
                verificador.verificarCondicion(t, node.jjtGetFirstToken().beginLine,
                        node.jjtGetFirstToken().beginColumn);
            } else {
                hijo.jjtAccept(this, data);
            }
        }
        return data;
    }

    private boolean esNodoDeExpresion(Node n) {
        return n instanceof ASTExpresion || n instanceof ASTRelacion || n instanceof ASTSimple
                || n instanceof ASTTermino || n instanceof ASTFactor || n instanceof ASTLiteral
                || n instanceof ASTNombre;
    }

    // --------------------------------------------------------- expresiones

    @Override
    public Object visit(ASTLiteral node, Object data) {
        // El propio texto no distingue Integer/Float/Character/String/null
        // por sí solo de forma fiable (p. ej. "42" también podría ser texto
        // de un literal con base) — en vez de re-derivar el tipo desde el
        // texto, Ada.jjt#literal() (Task 7 + Step 5a) guarda el TIPO ya
        // calculado en un LiteralAst(texto, TipoAda).
        return ((com.compiladorada.semantico.LiteralAst) node.jjtGetValue()).tipo();
    }

    @Override
    public Object visit(ASTNombre node, Object data) {
        NombreAst info = (NombreAst) node.jjtGetValue();
        Simbolo base = verificador.resolverUsoConOrden(info.base(), info.linea(), info.columna());
        TipoAda tipo = base != null ? base.tipo() : TipoAda.DESCONOCIDO;
        return resolverCadena(tipo, info, base, data, node);
    }

    /** Aplica, en orden, cada segmento (indexación/campo) de NombreAst sobre
     * el tipo actual, consumiendo del nodo los hijos que le correspondan a
     * cada indexación (sus argumentos son hijos reales del árbol, en el
     * mismo orden en que aparecen los segmentos de Indexacion). */
    private TipoAda resolverCadena(TipoAda tipoBase, NombreAst info, Simbolo simboloBase,
                                    Object data, SimpleNode node) {
        TipoAda tipo = tipoBase;
        int siguienteHijo = 0;
        boolean esPrimero = true;
        for (NombreAst.Segmento seg : info.segmentos()) {
            if (seg instanceof NombreAst.Segmento.Indexacion idx) {
                List<TipoAda> argumentos = new java.util.ArrayList<>();
                for (int i = 0; i < idx.cantidadArgumentos(); i++) {
                    argumentos.add(tipo(node.jjtGetChild(siguienteHijo++), data));
                }
                tipo = verificador.tipoDeLlamadaOIndexacion(
                        esPrimero ? simboloBase : null, tipo, argumentos, idx.linea(), idx.columna());
            } else if (seg instanceof NombreAst.Segmento.Campo campo) {
                tipo = verificador.tipoDeCampo(tipo, campo.nombre(), campo.linea(), campo.columna());
            }
            esPrimero = false;
        }
        return tipo;
    }

    @Override
    public Object visit(ASTExpresion node, Object data) {
        @SuppressWarnings("unchecked")
        List<String> ops = (List<String>) node.jjtGetValue();
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        for (int i = 0; i < ops.size(); i++) {
            TipoAda der = tipo(node.jjtGetChild(i + 1), data);
            izq = verificador.tipoOperadorLogico(izq, der, ops.get(i),
                    node.jjtGetFirstToken().beginLine, node.jjtGetFirstToken().beginColumn);
        }
        return izq;
    }

    @Override
    public Object visit(ASTRelacion node, Object data) {
        VerificadorSemantico.RelacionOp op = (VerificadorSemantico.RelacionOp) node.jjtGetValue();
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        TipoAda der = tipo(node.jjtGetChild(1), data);
        int linea = node.jjtGetFirstToken().beginLine;
        int columna = node.jjtGetFirstToken().beginColumn;
        if (op.pertenencia()) {
            return verificador.tipoDePertenencia(izq, der, linea, columna);
        }
        return verificador.tipoOperadorRelacional(izq, der, op.comparador(), linea, columna);
    }

    @Override
    public Object visit(ASTSimple node, Object data) {
        VerificadorSemantico.SimpleOp op = (VerificadorSemantico.SimpleOp) node.jjtGetValue();
        int linea = node.jjtGetFirstToken().beginLine;
        int columna = node.jjtGetFirstToken().beginColumn;
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        if (op.signo() != null) {
            izq = verificador.tipoOperadorUnario(op.signo(), izq, linea, columna);
        }
        for (int i = 0; i < op.operadores().size(); i++) {
            TipoAda der = tipo(node.jjtGetChild(i + 1), data);
            izq = verificador.tipoOperadorAditivo(izq, der, op.operadores().get(i), linea, columna);
        }
        return izq;
    }

    @Override
    public Object visit(ASTTermino node, Object data) {
        @SuppressWarnings("unchecked")
        List<String> ops = (List<String>) node.jjtGetValue();
        TipoAda izq = tipo(node.jjtGetChild(0), data);
        for (int i = 0; i < ops.size(); i++) {
            TipoAda der = tipo(node.jjtGetChild(i + 1), data);
            izq = verificador.tipoOperadorMultiplicativo(izq, der, ops.get(i),
                    node.jjtGetFirstToken().beginLine, node.jjtGetFirstToken().beginColumn);
        }
        return izq;
    }

    @Override
    public Object visit(ASTFactor node, Object data) {
        VerificadorSemantico.FactorOp op = (VerificadorSemantico.FactorOp) node.jjtGetValue();
        int linea = node.jjtGetFirstToken().beginLine;
        int columna = node.jjtGetFirstToken().beginColumn;
        TipoAda base = tipo(node.jjtGetChild(0), data);
        if (op.unario() != null) {
            base = verificador.tipoOperadorUnario(op.unario(), base, linea, columna);
        }
        if (op.tienePotencia()) {
            TipoAda exp = tipo(node.jjtGetChild(1), data);
            base = verificador.tipoOperadorPotencia(base, exp, linea, columna);
        }
        return base;
    }

    /** Cualquier nodo sin manejo explícito (por ejemplo, uno bubbled sin
     * envoltorio cuando no hubo operador) simplemente no debería llegar aquí
     * directamente como top-level de una expresión — pero si un llamador
     * genérico como {@link #tipo(Node, Object)} lo invoca sobre un nodo
     * inesperado, devolver DESCONOCIDO en vez de fallar mantiene la
     * recuperación de errores consistente con el resto del motor. */
    @Override
    public Object visit(SimpleNode node, Object data) {
        return TipoAda.DESCONOCIDO;
    }
}
