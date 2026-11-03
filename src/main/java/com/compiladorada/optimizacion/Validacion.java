package com.compiladorada.optimizacion;

import com.compiladorada.intermedio.ResultadoIntermedio;
import com.compiladorada.intermedio.ValorFinal;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparación de los valores finales que calcula la fase intermedia sobre el
 * código original y sobre el optimizado: si la optimización conserva el
 * comportamiento, deben ser idénticos. De una variable sin valor solo se
 * compara que siga sin valor en ambos (el motivo cita números de línea, que
 * cambian al compactar).
 */
public record Validacion(boolean coincide, List<String> diferencias) {

    public static Validacion comparar(ResultadoIntermedio original, ResultadoIntermedio optimizado) {
        List<String> diferencias = new ArrayList<>();
        List<ValorFinal> a = original.valoresFinales();
        List<ValorFinal> b = optimizado.valoresFinales();
        if (a.size() != b.size()) {
            diferencias.add("el número de variables numéricas es distinto: "
                    + a.size() + " (original) frente a " + b.size() + " (optimizado)");
        }
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            ValorFinal x = a.get(i);
            ValorFinal y = b.get(i);
            if (!x.nombre().equals(y.nombre()) || !x.tipo().equals(y.tipo())
                    || !java.util.Objects.equals(x.valor(), y.valor())
                    || (x.motivo() == null) != (y.motivo() == null)) {
                diferencias.add(x.nombre() + ": " + resumen(x) + " (original) frente a "
                        + resumen(y) + " (optimizado)");
            }
        }
        return new Validacion(diferencias.isEmpty(), List.copyOf(diferencias));
    }

    private static String resumen(ValorFinal v) {
        return v.nombre() + " : " + v.tipo() + " = " + (v.valor() != null ? v.valor() : "(sin valor)");
    }

    /** Texto para la pestaña y para el archivo de salida. */
    public String texto() {
        if (coincide) {
            return "Validación: los valores finales del código original y del optimizado coinciden.";
        }
        StringBuilder sb = new StringBuilder("Validación: los valores finales NO coinciden:\n");
        for (String d : diferencias) {
            sb.append("  * ").append(d).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
