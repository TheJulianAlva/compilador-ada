package com.compiladorada.lexico;

public record TokenLexico(String lexema, TipoToken tipo, int linea, int columna) {
}
