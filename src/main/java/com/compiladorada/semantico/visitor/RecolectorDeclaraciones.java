package com.compiladorada.semantico.visitor;

import com.compiladorada.semantico.Ambito;
import com.compiladorada.semantico.DeclaracionTipoAst;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.TipoAda;
import com.compiladorada.semantico.VerificadorSemantico;
import com.compiladorada.sintactico.nodos.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación B, pasada 1: recorre el árbol una vez y registra cada
 * declaración (variables, tipos, parámetros, subprogramas, paquetes) en el
 * ámbito correspondiente — sin verificar ningún uso todavía (eso lo hace
 * {@link VerificadorUsos} en la pasada 2, reutilizando el MISMO
 * VerificadorSemantico y el MISMO árbol de Ambito que esta pasada construyó).
 *
 * <p>Para que la pasada 2 pueda reentrar exactamente los mismos ámbitos que
 * esta pasada abrió (en vez de crear unos vacíos nuevos), esta clase guarda
 * la asociación nodo-que-abre-ámbito → Ambito en {@link #ambitosPorNodo}. La
 * pasada 2 la recibe a través de este objeto ({@link #ambitosPorNodo()}).
 */
public final class RecolectorDeclaraciones extends AdaParserDefaultVisitor {

    private final VerificadorSemantico verificador = new VerificadorSemantico();
    private final Map<Node, Ambito> ambitosPorNodo = new IdentityHashMap<>();

    /** Pasada 1 completa: puebla el árbol de ámbitos a partir de la raíz y
     * devuelve el RecolectorDeclaraciones ya poblado (el VerificadorSemantico
     * y la asociación nodo→ámbito, recuperables con {@link #verificador()} y
     * {@link #ambitosPorNodo()}, para pasarlos a la Pasada 2). */
    public static RecolectorDeclaraciones recolectar(SimpleNode raiz) {
        RecolectorDeclaraciones r = new RecolectorDeclaraciones();
        raiz.jjtAccept(r, null);
        return r;
    }

    public VerificadorSemantico verificador() {
        return verificador;
    }

    public Map<Node, Ambito> ambitosPorNodo() {
        return ambitosPorNodo;
    }

    private List<VerificadorSemantico.ParametroInfo> extraerParametros(SimpleNode node) {
        List<VerificadorSemantico.ParametroInfo> lista = new ArrayList<>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            if (node.jjtGetChild(i) instanceof ASTParametro p) {
                lista.add((VerificadorSemantico.ParametroInfo) p.jjtGetValue());
            }
        }
        return lista;
    }

    private void declararSubprogramaYAbrirAmbito(SimpleNode node, NombreAst info,
                                                  TipoAda retorno, Object data) {
        List<VerificadorSemantico.ParametroInfo> parametros = extraerParametros(node);
        List<TipoAda> tiposParam = new ArrayList<>();
        for (VerificadorSemantico.ParametroInfo p : parametros) {
            for (String n : p.nombres()) {
                tiposParam.add(p.tipo());
            }
        }
        verificador.declararSubprograma(info.base(), tiposParam, retorno, info.linea(), info.columna());
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        for (VerificadorSemantico.ParametroInfo p : parametros) {
            for (String n : p.nombres()) {
                verificador.declararParametro(n, p.tipo(), p.modoOut(), info.linea(), info.columna());
            }
        }
        node.childrenAccept(this, data);
        verificador.salirAmbito();
    }

    @Override
    public Object visit(ASTProcedimiento node, Object data) {
        declararSubprogramaYAbrirAmbito(node, (NombreAst) node.jjtGetValue(), null, data);
        return data;
    }

    @Override
    public Object visit(ASTFuncion node, Object data) {
        // El tipo de retorno no se guardó aparte en el nodo (Task 7 no lo
        // necesitaba: lo consumía en línea). Para la Pasada 1 basta con
        // registrar la firma con retorno = TipoAda.DESCONOCIDO como marcador
        // de "función" (distinto de null = procedimiento); la Pasada 2 no
        // depende de este valor exacto, solo de que retorno != null para las
        // comprobaciones de aridad/tipo de argumentos en llamadas — que sí
        // exigen los tipos de PARÁMETRO correctos, no el de retorno.
        declararSubprogramaYAbrirAmbito(node, (NombreAst) node.jjtGetValue(), TipoAda.DESCONOCIDO, data);
        return data;
    }

    @Override
    public Object visit(ASTPaquete node, Object data) {
        NombreAst info = (NombreAst) node.jjtGetValue();
        verificador.declararPaquete(info.base(), info.linea(), info.columna());
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionVar node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        verificador.declararVariables(info.nombres(), info.tipo(), info.esConstante(),
                info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionTipo node, Object data) {
        DeclaracionTipoAst info = (DeclaracionTipoAst) node.jjtGetValue();
        verificador.declararTipo(info.nombre(), info.tipo(), info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionSubtipo node, Object data) {
        DeclaracionTipoAst info = (DeclaracionTipoAst) node.jjtGetValue();
        verificador.declararTipo(info.nombre(), info.tipo(), info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTFor node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        verificador.declararVariables(info.nombres(), info.tipo(), info.esConstante(),
                info.linea(), info.columna());
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }
}
