package com.compiladorada.semantico.visitor;

import com.compiladorada.semantico.Ambito;
import com.compiladorada.semantico.DeclaracionTipoAst;
import com.compiladorada.semantico.DeclaracionVarAst;
import com.compiladorada.semantico.FuncionAst;
import com.compiladorada.semantico.NombreAst;
import com.compiladorada.semantico.PaqueteAst;
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
        // node.jjtGetValue() puede ser null: Ada.jjt#unidadNoReconocida()
        // (recuperación de errores sintácticos) reutiliza el nodo #Procedimiento
        // pero nunca llama jjtSetValue sobre él. Sin esta guarda, un archivo con
        // ese tipo de error sintáctico provoca un NullPointerException aquí y
        // pierde TODAS las declaraciones del resto del programa, no solo las de
        // esta unidad mal formada.
        NombreAst info = (NombreAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        declararSubprogramaYAbrirAmbito(node, info, null, data);
        return data;
    }

    @Override
    public Object visit(ASTFuncion node, Object data) {
        // El nodo #Funcion guarda un FuncionAst (Ada.jjt#funcion(), Step 3a
        // ampliado): a diferencia de #Procedimiento/#Paquete (NombreAst,
        // sin tipo de retorno), aquí sí hace falta el tipo de retorno real
        // para que las expresiones que consumen el RESULTADO de la función
        // (asignaciones, condiciones, argumentos de otra llamada) se tipen
        // correctamente en la Pasada 2 — usar TipoAda.DESCONOCIDO como
        // marcador habría vuelto compatible cualquier consumo del resultado.
        FuncionAst info = (FuncionAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        NombreAst nombre = new NombreAst(info.nombre(), info.linea(), info.columna(), List.of());
        declararSubprogramaYAbrirAmbito(node, nombre, info.retorno(), data);
        return data;
    }

    @Override
    public Object visit(ASTPaquete node, Object data) {
        PaqueteAst info = (PaqueteAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        verificador.declararPaquete(info.nombre(), info.linea(), info.columna());
        // El spec de un package (esBody() == false) es transparente: su
        // contenido se declara en el ámbito vigente, sin ámbito propio — ver
        // Ada.jjt#paquete() y docs/unidad-0-diseno-ide.md §9.4. Solo el body
        // abre y registra un ámbito propio para que VerificadorUsos lo reentre.
        if (info.esBody()) {
            Ambito ambito = verificador.entrarAmbito();
            ambitosPorNodo.put(node, ambito);
            node.childrenAccept(this, data);
            verificador.salirAmbito();
        } else {
            node.childrenAccept(this, data);
        }
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionVar node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        verificador.declararVariables(info.nombres(), info.tipo(), info.esConstante(),
                info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionTipo node, Object data) {
        DeclaracionTipoAst info = (DeclaracionTipoAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        verificador.declararTipo(info.nombre(), info.tipo(), info.linea(), info.columna());
        // Los literales de un tipo enumerado (Ada.jjt#definicionTipo(), rama
        // "(" ... ")") son constantes de ese tipo. Implementación A los
        // declara ahí mismo, durante el parseo; Implementación B usa su
        // PROPIO VerificadorSemantico (no el de A), así que tiene que
        // repetir esa declaración aquí, en la Pasada 1, para que la Pasada 2
        // pueda resolver usos como "C := Rojo;".
        if (info.tipo() instanceof TipoAda.TipoEnumerado enumerado) {
            verificador.declararVariables(enumerado.literales(), enumerado, true,
                    info.linea(), info.columna());
        }
        return data;
    }

    @Override
    public Object visit(ASTDeclaracionSubtipo node, Object data) {
        DeclaracionTipoAst info = (DeclaracionTipoAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        verificador.declararTipo(info.nombre(), info.tipo(), info.linea(), info.columna());
        return data;
    }

    @Override
    public Object visit(ASTFor node, Object data) {
        DeclaracionVarAst info = (DeclaracionVarAst) node.jjtGetValue();
        if (info == null) {
            node.childrenAccept(this, data);
            return data;
        }
        Ambito ambito = verificador.entrarAmbito();
        ambitosPorNodo.put(node, ambito);
        verificador.declararVariables(info.nombres(), info.tipo(), info.esConstante(),
                info.linea(), info.columna());
        node.childrenAccept(this, data);
        verificador.salirAmbito();
        return data;
    }
}
