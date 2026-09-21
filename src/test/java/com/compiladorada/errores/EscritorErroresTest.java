package com.compiladorada.errores;

import com.compiladorada.ResultadoCompilacion;
import com.compiladorada.lexico.TipoToken;
import com.compiladorada.lexico.TokenLexico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EscritorErroresTest {

    @Test
    void escribe_los_tres_archivos_con_formato_esperado(@TempDir Path dir) throws Exception {
        //  Fase léxica limpia: el archivo sintáctico lista sus errores.
        ResultadoCompilacion r = new ResultadoCompilacion(
                List.of(new TokenLexico("Hola", TipoToken.IDENTIFICADOR, 1, 1)),
                List.of(),
                List.of(new ErrorCompilacion(ErrorCompilacion.Categoria.SINTACTICO, 3, 1, "se esperaba ';'")),
                List.of(),
                null);

        EscritorErrores.volcar(r, dir, "prog.ada");

        String lex = Files.readString(dir.resolve("errores_lexicos.txt"));
        String sin = Files.readString(dir.resolve("errores_sintacticos.txt"));
        String tok = Files.readString(dir.resolve("tokens.txt"));

        assertTrue(lex.contains("Total: 0 errores") || lex.contains("Sin errores"));
        assertTrue(sin.contains("prog.ada:3:1: error sintáctico: se esperaba ';'"));
        assertTrue(tok.contains("Hola"));
        assertTrue(tok.contains("IDENTIFICADOR"));
    }

    @Test
    void con_errores_lexicos_el_archivo_sintactico_dice_omitido(@TempDir Path dir) throws Exception {
        ResultadoCompilacion r = new ResultadoCompilacion(
                List.of(),
                List.of(new ErrorCompilacion(ErrorCompilacion.Categoria.LEXICO, 2, 8, "carácter no válido '$'")),
                List.of(),   // vacía: el parser no corrió
                List.of(),
                null);

        EscritorErrores.volcar(r, dir, "prog.ada");

        String lex = Files.readString(dir.resolve("errores_lexicos.txt"));
        String sin = Files.readString(dir.resolve("errores_sintacticos.txt"));
        assertTrue(lex.contains("prog.ada:2:8: error léxico: carácter no válido '$'"));
        assertTrue(sin.contains("OMITIDO"));
        assertFalse(sin.contains("Sin errores"), "no debe decir 'Sin errores' cuando en realidad no se analizó");
    }

    @Test
    void sobrescribe_en_cada_llamada(@TempDir Path dir) throws Exception {
        ResultadoCompilacion conError = new ResultadoCompilacion(List.of(),
                List.of(new ErrorCompilacion(ErrorCompilacion.Categoria.LEXICO, 1, 1, "x")),
                List.of(), List.of(), null);
        ResultadoCompilacion limpio = new ResultadoCompilacion(List.of(), List.of(), List.of(), List.of(), null);

        EscritorErrores.volcar(conError, dir, "p.ada");
        EscritorErrores.volcar(limpio, dir, "p.ada");

        String lex = Files.readString(dir.resolve("errores_lexicos.txt"));
        // el error previo (línea 'p.ada:1:1: error léxico: x') debe haberse sobrescrito;
        // se comprueba la línea formateada, no el fragmento '1:1' que la cabecera ISO
        // de fecha/hora puede contener por casualidad.
        assertFalse(lex.contains("p.ada:1:1:"), "el error previo debe haberse sobrescrito");
        assertFalse(lex.contains("error léxico:"), "no debe quedar ningún error");
        assertTrue(lex.contains("Total: 0 errores") || lex.contains("Sin errores"));
    }

    @Test
    void crea_la_carpeta_si_no_existe(@TempDir Path base) throws Exception {
        Path dir = base.resolve("output");
        EscritorErrores.volcar(new ResultadoCompilacion(List.of(), List.of(), List.of(), List.of(), null),
                dir, "p.ada");
        assertTrue(Files.isDirectory(dir));
    }
}
