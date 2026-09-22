package com.compiladorada.semantico;

/**
 * Mensajes de error en español para el análisis semántico, mismo estilo que
 * com.compiladorada.sintactico.TraductorMensajes.
 */
public final class MensajesSemanticos {

    private MensajesSemanticos() {
    }

    public static String redeclarado(String nombre) {
        return "'" + nombre + "' ya está declarado en este ámbito";
    }

    public static String tipoNoDeclarado(String nombre) {
        return "'" + nombre + "' no es un tipo declarado";
    }

    public static String noDeclarado(String nombre) {
        return "'" + nombre + "' no está declarado";
    }

    public static String noEsArreglo(String nombre) {
        return "'" + nombre + "' no es un arreglo";
    }

    public static String indiceNoEntero(String tipoEncontrado) {
        return "el índice debe ser de tipo entero, se encontró '" + tipoEncontrado + "'";
    }

    public static String noEsRegistro(String nombre) {
        return "'" + nombre + "' no es un registro";
    }

    public static String campoNoExiste(String campo, String nombreRegistro) {
        return "'" + nombreRegistro + "' no tiene el campo '" + campo + "'";
    }

    public static String asignacionAConstante(String nombre) {
        return "no se puede asignar a la constante '" + nombre + "'";
    }

    public static String tiposIncompatibles(String a, String b) {
        return "tipos incompatibles: '" + a + "' y '" + b + "'";
    }

    public static String operadorRequiereTipo(String operador, String tipoEsperado) {
        return "el operador '" + operador + "' requiere operandos de tipo " + tipoEsperado;
    }

    public static String aridadIncorrecta(String nombre, int esperados, int recibidos) {
        return "'" + nombre + "' espera " + esperados + " argumento(s), se encontraron " + recibidos;
    }

    public static String argumentoIncompatible(String nombre, int posicion, String esperado, String recibido) {
        return "el argumento " + posicion + " de '" + nombre + "' debe ser '" + esperado
                + "', se encontró '" + recibido + "'";
    }

    public static String conversionRequiereUnArgumento(String nombreTipo, int recibidos) {
        return "la conversión a '" + nombreTipo + "' espera 1 argumento, se encontraron " + recibidos;
    }

    public static String conversionInvalida(String origen, String destino) {
        return "no se puede convertir '" + origen + "' a '" + destino + "'";
    }
}
