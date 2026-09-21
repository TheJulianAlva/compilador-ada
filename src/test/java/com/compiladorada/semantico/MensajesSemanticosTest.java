package com.compiladorada.semantico;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MensajesSemanticosTest {

    @Test
    void mensajes_incluyen_los_nombres_relevantes_entre_comillas() {
        assertEquals("'X' ya está declarado en este ámbito", MensajesSemanticos.redeclarado("X"));
        assertEquals("'Integer' no es un tipo declarado", MensajesSemanticos.tipoNoDeclarado("Integer"));
        assertEquals("'X' no está declarado", MensajesSemanticos.noDeclarado("X"));
        assertEquals("'X' no es un arreglo", MensajesSemanticos.noEsArreglo("X"));
        assertEquals("el índice debe ser de tipo entero, se encontró 'Boolean'",
                MensajesSemanticos.indiceNoEntero("Boolean"));
        assertEquals("'X' no es un registro", MensajesSemanticos.noEsRegistro("X"));
        assertEquals("'Punto' no tiene el campo 'Z'", MensajesSemanticos.campoNoExiste("Z", "Punto"));
        assertEquals("no se puede asignar a la constante 'X'", MensajesSemanticos.asignacionAConstante("X"));
        assertEquals("tipos incompatibles: 'Integer' y 'Boolean'",
                MensajesSemanticos.tiposIncompatibles("Integer", "Boolean"));
        assertEquals("el operador '+' requiere operandos de tipo numérico",
                MensajesSemanticos.operadorRequiereTipo("+", "numérico"));
        assertEquals("'Suma' espera 2 argumento(s), se encontraron 1",
                MensajesSemanticos.aridadIncorrecta("Suma", 2, 1));
        assertEquals("el argumento 1 de 'Suma' debe ser 'Integer', se encontró 'Boolean'",
                MensajesSemanticos.argumentoIncompatible("Suma", 1, "Integer", "Boolean"));
    }
}
