package com.compiladorada.optimizacion;

import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Une lexemas en el texto más corto que sigue siendo Ada equivalente: sin
 * comentarios, sin líneas en blanco ni indentación, con un espacio solo donde
 * pegar dos lexemas cambiaría cómo se separan en tokens, y un salto de línea
 * tras cada sentencia y tras las palabras que abren un bloque.
 */
public final class Compactador {

    /** Sustituye el rango de tokens que empieza donde se registra y termina en {@code fin} (inclusive). */
    public record Reemplazo(List<String> lexemas, long fin) {
    }

    private static final Set<String> PARES_QUE_SE_FUNDEN =
            Set.of("--", ":=", "=>", "..", "**", "/=", ">=", "<=", "<<", ">>", "<>");

    private Compactador() {
    }

    public static long clave(int linea, int columna) {
        return (long) linea * 1_000_000L + columna;
    }

    /** Compacta la lista de tokens léxicos aplicando los reemplazos (clave del primer token → reemplazo). */
    public static String compactar(List<TokenLexico> tokens, Map<Long, Reemplazo> reemplazos) {
        List<TokenLexico> utiles = new ArrayList<>();
        for (TokenLexico t : tokens) {
            if (t.tipo() != TipoToken.COMENTARIO) {
                utiles.add(t);
            }
        }
        List<String> lexemas = new ArrayList<>();
        for (int i = 0; i < utiles.size(); i++) {
            TokenLexico t = utiles.get(i);
            Reemplazo r = reemplazos.get(clave(t.linea(), t.columna()));
            if (r == null) {
                lexemas.add(t.lexema());
                continue;
            }
            lexemas.addAll(r.lexemas());
            while (i < utiles.size()
                    && clave(utiles.get(i).linea(), utiles.get(i).columna()) != r.fin()) {
                i++;
            }
        }
        return unir(lexemas);
    }

    public static String unir(List<String> lexemas) {
        StringBuilder sb = new StringBuilder();
        int profundidad = 0;
        boolean esperaIs = false;
        boolean esperaFlecha = false;
        String anterior = "";
        boolean saltoPendiente = false;
        for (String lexema : lexemas) {
            String l = lexema.toLowerCase(Locale.ROOT);
            if (sb.length() > 0) {
                if (saltoPendiente) {
                    sb.append('\n');
                } else if (hayQueSeparar(sb.charAt(sb.length() - 1), lexema.charAt(0))) {
                    sb.append(' ');
                }
            }
            sb.append(lexema);
            switch (l) {
                case "(" -> profundidad++;
                case ")" -> profundidad = Math.max(0, profundidad - 1);
                case "procedure", "function", "package" -> esperaIs = true;
                case "when" -> esperaFlecha = true;
                default -> { }
            }
            saltoPendiente = switch (l) {
                case ";" -> {
                    esperaIs = false;
                    yield profundidad == 0;
                }
                case "is" -> {
                    boolean salta = esperaIs;
                    esperaIs = false;
                    yield salta;
                }
                case "=>" -> {
                    boolean salta = esperaFlecha;
                    esperaFlecha = false;
                    yield salta;
                }
                case "then" -> !anterior.equals("and");
                case "else" -> !anterior.equals("or");
                case "loop", "record" -> !anterior.equals("end");
                case "begin", "declare", "exception", "private" -> true;
                default -> false;
            };
            anterior = l;
        }
        return sb.toString();
    }

    private static boolean hayQueSeparar(char fin, char inicio) {
        if (palabra(fin) && palabra(inicio)) {
            return true;
        }
        if ((fin == '\'' && inicio == '\'') || (fin == '"' && inicio == '"')) {
            return true;
        }
        return PARES_QUE_SE_FUNDEN.contains("" + fin + inicio);
    }

    private static boolean palabra(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
