--  Este archivo tiene errores lexicos Y errores de sintaxis a la vez.
--  Al compilar veras SOLO los dos errores lexicos: el analisis sintactico
--  no se ejecuta mientras haya errores lexicos (fases secuenciales).
--  Corrige la cadena de la linea 8 y el '$' de la linea 11, recompila,
--  y entonces apareceran los errores sintacticos de las lineas 13, 15 y 19.

procedure Con_Errores is
   Contador : Integer := 0;
   Total    : Integer := 0;
   Texto    : String  := "cadena sin cerrar;   --  error lexico: falta la comilla final
begin

   Contador := 10 $ 2;         --  error lexico: caracter '$' no valido

   Total := Contador * 2       --  (error sintactico latente: falta ';')

   if Contador > 0             --  (error sintactico latente: falta 'then')
      Total := Total + 1;
   end if;

   Total := ;                  --  (error sintactico latente: expresion invalida)

end Con_Errores;
