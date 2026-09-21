package com.compiladorada.errores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCompilacionTest {

    @Test
    void categoria_semantico_tiene_etiqueta_propia() {
        ErrorCompilacion e = new ErrorCompilacion(
                ErrorCompilacion.Categoria.SEMANTICO, 3, 7, "prueba");
        assertEquals("error semántico", e.categoria().etiqueta());
        assertEquals("archivo.ada:3:7: error semántico: prueba", e.formatear("archivo.ada"));
    }
}
