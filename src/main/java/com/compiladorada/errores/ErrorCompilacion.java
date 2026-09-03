package com.compiladorada.errores;

public record ErrorCompilacion(Categoria categoria, int linea, int columna, String mensaje) {

    public enum Categoria {
        LEXICO("error léxico"),
        SINTACTICO("error sintáctico");

        private final String etiqueta;

        Categoria(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public String formatear(String nombreArchivo) {
        return nombreArchivo + ":" + linea + ":" + columna + ": "
                + categoria.etiqueta() + ": " + mensaje;
    }
}
