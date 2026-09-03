package com.compiladorada.lexico;

import java.util.Locale;
import java.util.Set;

public final class PalabrasReservadas {

    public static final Set<String> TODAS = Set.of(
            "abort", "abs", "abstract", "accept", "access", "aliased", "all", "and",
            "array", "at", "begin", "body", "case", "constant", "declare", "delay",
            "delta", "digits", "do", "else", "elsif", "end", "entry", "exception",
            "exit", "for", "function", "generic", "goto", "if", "in", "interface",
            "is", "limited", "loop", "mod", "new", "not", "null", "of", "or", "others",
            "out", "overriding", "package", "pragma", "private", "procedure",
            "protected", "raise", "range", "record", "rem", "renames", "requeue",
            "return", "reverse", "select", "separate", "some", "subtype",
            "synchronized", "tagged", "task", "terminate", "then", "type", "until",
            "use", "when", "while", "with", "xor");

    private PalabrasReservadas() {
    }

    public static boolean esReservada(String lexema) {
        return TODAS.contains(lexema.toLowerCase(Locale.ROOT));
    }
}
