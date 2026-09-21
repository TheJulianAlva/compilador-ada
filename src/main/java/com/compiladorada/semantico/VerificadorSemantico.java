package com.compiladorada.semantico;

import com.compiladorada.errores.ErrorCompilacion;
import com.compiladorada.errores.ErrorCompilacion.Categoria;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Motor de reglas semánticas compartido por las dos implementaciones (ver
 * docs/superpowers/specs/2026-09-20-analisis-semantico-design.md):
 * acciones embebidas en Ada.jjt durante el parseo (Implementación A) y un
 * visitor de dos pasadas sobre el AST (Implementación B). Cada
 * implementación crea su PROPIA instancia — con su propio árbol de
 * Ambito —, pero ambas invocan exactamente estos métodos, así que las dos
 * reportan los mismos errores para el mismo programa.
 */
public final class VerificadorSemantico {

    private Ambito actual;
    private final List<ErrorCompilacion> errores = new ArrayList<>();

    public VerificadorSemantico() {
        actual = new Ambito(null);
        sembrarStandard(actual);
    }

    private static void sembrarStandard(Ambito global) {
        global.declarar(new Simbolo("Integer", Simbolo.Categoria.TIPO, TipoAda.INTEGER, false, 0, 0));
        global.declarar(new Simbolo("Float", Simbolo.Categoria.TIPO, TipoAda.FLOAT, false, 0, 0));
        global.declarar(new Simbolo("Boolean", Simbolo.Categoria.TIPO, TipoAda.BOOLEAN, false, 0, 0));
        global.declarar(new Simbolo("Character", Simbolo.Categoria.TIPO, TipoAda.CHARACTER, false, 0, 0));
        global.declarar(new Simbolo("String", Simbolo.Categoria.TIPO, TipoAda.STRING, false, 0, 0));
        global.declarar(new Simbolo("True", Simbolo.Categoria.VARIABLE, TipoAda.BOOLEAN, true, 0, 0));
        global.declarar(new Simbolo("False", Simbolo.Categoria.VARIABLE, TipoAda.BOOLEAN, true, 0, 0));
    }

    public List<ErrorCompilacion> errores() {
        return errores;
    }

    private void reportar(int linea, int columna, String mensaje) {
        errores.add(new ErrorCompilacion(Categoria.SEMANTICO, linea, columna, mensaje));
    }

    // ---------------------------------------------------------------- ámbitos

    public Ambito ambitoActual() {
        return actual;
    }

    public Ambito entrarAmbito() {
        actual = new Ambito(actual);
        return actual;
    }

    /** Solo para la segunda pasada de la Implementación B: reentra a un
     * ámbito que la primera pasada ya creó y pobló, en vez de crear uno
     * vacío nuevo. */
    public void entrarAmbitoExistente(Ambito ambito) {
        actual = ambito;
    }

    public void salirAmbito() {
        actual = actual.padre();
    }

    public int profundidad() {
        int n = 0;
        for (Ambito a = actual; a.padre() != null; a = a.padre()) {
            n++;
        }
        return n;
    }

    /** Restaura el ámbito actual a la profundidad dada, saliendo de los
     * ámbitos intermedios. Usado en la recuperación de errores: si el
     * parseo de un procedimiento/función/paquete se abortó a mitad de
     * camino, esto evita dejar "actual" apuntando a un ámbito huérfano. */
    public void salirHasta(int profundidad) {
        while (profundidad() > profundidad) {
            salirAmbito();
        }
    }

    // ------------------------------------------------------------ declarar

    public void declararVariables(List<String> nombres, TipoAda tipo, boolean esConstante,
                                   int linea, int columna) {
        for (String nombre : nombres) {
            Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.VARIABLE, tipo, esConstante, linea, columna);
            if (!actual.declarar(simbolo)) {
                reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
            }
        }
    }

    public void declararTipo(String nombre, TipoAda tipo, int linea, int columna) {
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.TIPO, tipo, false, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    public void declararParametro(String nombre, TipoAda tipo, boolean modoOut, int linea, int columna) {
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.PARAMETRO, tipo, !modoOut, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    public void declararSubprograma(String nombre, List<TipoAda> parametros, TipoAda retorno,
                                     int linea, int columna) {
        TipoAda.TipoSubprograma firma = new TipoAda.TipoSubprograma(nombre, parametros, retorno);
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.SUBPROGRAMA, firma, false, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    public void declararPaquete(String nombre, int linea, int columna) {
        Simbolo simbolo = new Simbolo(nombre, Simbolo.Categoria.PAQUETE, TipoAda.DESCONOCIDO, false, linea, columna);
        if (!actual.declarar(simbolo)) {
            reportar(linea, columna, MensajesSemanticos.redeclarado(nombre));
        }
    }

    // ---------------------------------------------------------- resolución

    public TipoAda tipoDeclarado(String nombreTipo, int linea, int columna) {
        Simbolo s = actual.resolver(nombreTipo);
        if (s == null || s.categoria() != Simbolo.Categoria.TIPO) {
            reportar(linea, columna, MensajesSemanticos.tipoNoDeclarado(nombreTipo));
            return TipoAda.DESCONOCIDO;
        }
        return s.tipo();
    }

    /** Resuelve un identificador en una posición de uso. Usado por la
     * Implementación A: como se dispara durante el parseo, el orden
     * declarado-antes-de-usar ya lo garantiza el propio recorrido
     * izquierda-a-derecha del parser. */
    public Simbolo resolverUso(String nombre, int linea, int columna) {
        Simbolo s = actual.resolver(nombre);
        if (s == null) {
            reportar(linea, columna, MensajesSemanticos.noDeclarado(nombre));
        }
        return s;
    }

    /** Igual que resolverUso, pero para la segunda pasada de la
     * Implementación B: como la primera pasada ya registró TODAS las
     * declaraciones del árbol (incluidas las que aparecen después de este
     * uso en el texto fuente), hay que rechazar explícitamente una
     * resolución cuya declaración esté después del uso — si no, B validaría
     * usos-antes-de-declarar que A sí rechaza, y las dos implementaciones
     * dejarían de reportar los mismos errores. */
    public Simbolo resolverUsoConOrden(String nombre, int lineaUso, int columnaUso) {
        Simbolo s = actual.resolver(nombre);
        if (s == null || esDespues(s, lineaUso, columnaUso)) {
            reportar(lineaUso, columnaUso, MensajesSemanticos.noDeclarado(nombre));
            return null;
        }
        return s;
    }

    private boolean esDespues(Simbolo s, int lineaUso, int columnaUso) {
        if (s.lineaDeclaracion() == 0) {
            return false; // predefinido (Standard): siempre visible.
        }
        if (s.lineaDeclaracion() != lineaUso) {
            return s.lineaDeclaracion() > lineaUso;
        }
        return s.columnaDeclaracion() > columnaUso;
    }

    public TipoAda tipoDeIndexacion(TipoAda base, TipoAda tipoIndice, int linea, int columna) {
        if (base == TipoAda.DESCONOCIDO) {
            return TipoAda.DESCONOCIDO;
        }
        if (!(base instanceof TipoAda.TipoArreglo arreglo)) {
            reportar(linea, columna, MensajesSemanticos.noEsArreglo(base.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        if (!tipoIndice.compatibleCon(TipoAda.INTEGER)) {
            reportar(linea, columna, MensajesSemanticos.indiceNoEntero(tipoIndice.nombre()));
        }
        return arreglo.componente();
    }

    public TipoAda tipoDeCampo(TipoAda base, String campo, int linea, int columna) {
        if (base == TipoAda.DESCONOCIDO) {
            return TipoAda.DESCONOCIDO;
        }
        if (!(base instanceof TipoAda.TipoRegistro registro)) {
            reportar(linea, columna, MensajesSemanticos.noEsRegistro(base.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        TipoAda tipoCampo = registro.campos().get(campo.toLowerCase(Locale.ROOT));
        if (tipoCampo == null) {
            reportar(linea, columna, MensajesSemanticos.campoNoExiste(campo, base.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return tipoCampo;
    }

    /**
     * Resuelve tanto una llamada a subprograma ({@code Foo(1, 2)}) como una
     * indexación de arreglo ({@code A(1)}) — comparten sintaxis en la
     * gramática. {@code base} es el símbolo resuelto para el identificador
     * base SOLO si esta es la primera "(" de la cadena de acceso (null en
     * cualquier otro caso, ver Ada.jjt#nombre()); si es un SUBPROGRAMA se
     * verifica aridad y tipos de argumento, si no se trata como indexación
     * (solo se valida el primer argumento — no hay arreglos
     * multidimensionales en el subconjunto soportado).
     */
    public TipoAda tipoDeLlamadaOIndexacion(Simbolo base, TipoAda tipoActual,
                                             List<TipoAda> argumentos, int linea, int columna) {
        if (base != null && base.categoria() == Simbolo.Categoria.SUBPROGRAMA) {
            TipoAda.TipoSubprograma firma = (TipoAda.TipoSubprograma) base.tipo();
            if (argumentos.size() != firma.parametros().size()) {
                reportar(linea, columna, MensajesSemanticos.aridadIncorrecta(
                        base.nombre(), firma.parametros().size(), argumentos.size()));
            } else {
                for (int i = 0; i < argumentos.size(); i++) {
                    if (!firma.parametros().get(i).compatibleCon(argumentos.get(i))) {
                        reportar(linea, columna, MensajesSemanticos.argumentoIncompatible(
                                base.nombre(), i + 1, firma.parametros().get(i).nombre(),
                                argumentos.get(i).nombre()));
                    }
                }
            }
            return firma.retorno() != null ? firma.retorno() : TipoAda.DESCONOCIDO;
        }
        TipoAda tipoIndice = argumentos.isEmpty() ? TipoAda.DESCONOCIDO : argumentos.get(0);
        return tipoDeIndexacion(tipoActual, tipoIndice, linea, columna);
    }

    // -------------------------------------------------------- verificación

    public void verificarInicializacion(TipoAda destino, TipoAda origen, int linea, int columna) {
        if (!destino.compatibleCon(origen)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(destino.nombre(), origen.nombre()));
        }
    }

    public void verificarAsignacionMutabilidad(Simbolo destino, int linea, int columna) {
        if (destino != null && destino.esConstante()) {
            reportar(linea, columna, MensajesSemanticos.asignacionAConstante(destino.nombre()));
        }
    }

    public void verificarCondicion(TipoAda tipo, int linea, int columna) {
        if (!tipo.compatibleCon(TipoAda.BOOLEAN)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("condición", "Boolean"));
        }
    }

    public TipoAda tipoOperadorLogico(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if (!izq.compatibleCon(TipoAda.BOOLEAN) || !der.compatibleCon(TipoAda.BOOLEAN)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo(operador, "Boolean"));
            return TipoAda.DESCONOCIDO;
        }
        return TipoAda.BOOLEAN;
    }

    public TipoAda tipoOperadorRelacional(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if (!izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
        }
        return TipoAda.BOOLEAN;
    }

    public TipoAda tipoDePertenencia(TipoAda valor, TipoAda tipoRango, int linea, int columna) {
        if (!valor.compatibleCon(tipoRango)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(valor.nombre(), tipoRango.nombre()));
        }
        return TipoAda.BOOLEAN;
    }

    public TipoAda tipoOperadorAditivo(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if ("&".equals(operador)) {
            if (!esConcatenable(izq) || !esConcatenable(der)) {
                reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("&", "String/Character"));
                return TipoAda.DESCONOCIDO;
            }
            return TipoAda.STRING;
        }
        if (!esNumerico(izq) || !esNumerico(der) || !izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return izq;
    }

    public TipoAda tipoOperadorMultiplicativo(TipoAda izq, TipoAda der, String operador, int linea, int columna) {
        if (!esNumerico(izq) || !esNumerico(der) || !izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return izq;
    }

    public TipoAda tipoOperadorUnario(String operador, TipoAda operando, int linea, int columna) {
        if ("not".equals(operador)) {
            if (!operando.compatibleCon(TipoAda.BOOLEAN)) {
                reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("not", "Boolean"));
                return TipoAda.DESCONOCIDO;
            }
            return TipoAda.BOOLEAN;
        }
        if (!esNumerico(operando)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo(operador, "numérico"));
            return TipoAda.DESCONOCIDO;
        }
        return operando;
    }

    public TipoAda tipoOperadorPotencia(TipoAda base, TipoAda exponente, int linea, int columna) {
        if (!esNumerico(base) || !exponente.compatibleCon(TipoAda.INTEGER)) {
            reportar(linea, columna, MensajesSemanticos.operadorRequiereTipo("**", "numérico"));
            return TipoAda.DESCONOCIDO;
        }
        return base;
    }

    public TipoAda tipoDeRango(TipoAda izq, TipoAda der, int linea, int columna) {
        if (!izq.compatibleCon(der)) {
            reportar(linea, columna, MensajesSemanticos.tiposIncompatibles(izq.nombre(), der.nombre()));
            return TipoAda.DESCONOCIDO;
        }
        return izq;
    }

    private boolean esNumerico(TipoAda t) {
        return t == TipoAda.INTEGER || t == TipoAda.FLOAT || t instanceof TipoAda.TipoRango
                || t == TipoAda.DESCONOCIDO;
    }

    private boolean esConcatenable(TipoAda t) {
        return t == TipoAda.STRING || t == TipoAda.CHARACTER || t == TipoAda.DESCONOCIDO;
    }

    // ------------------------------------------- valores de nodo del AST
    // (los guarda la Tarea 7 vía jjtSetValue, los lee la Tarea 9)

    /** Valor del nodo #Nombre. */
    public record NombreResuelto(TipoAda tipo, Simbolo simboloBase) {
    }

    /** Valor del nodo #Factor cuando se crea (unario y/o potencia). */
    public record FactorOp(String unario, boolean tienePotencia) {
    }

    /** Valor del nodo #Simple cuando se crea (signo inicial y/o cadena de
     * operadores aditivos). */
    public record SimpleOp(String signo, List<String> operadores) {
    }

    /** Valor del nodo #Relacion cuando se crea: o bien un comparador, o bien
     * una prueba de pertenencia ({@code in}/{@code not in}). */
    public record RelacionOp(String comparador, boolean pertenencia, boolean negada) {
    }
}
