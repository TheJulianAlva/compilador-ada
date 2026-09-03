--  Ejemplo con errores deliberados para ver los paneles del IDE.
--  El compilador reporta TODOS los errores de una pasada (recuperandose
--  en cada punto de fallo) y separa los lexicos de los sintacticos.

procedure Con_Errores is
   Contador : Integer := 0;
   Total    : Integer := 0;
   Texto    : String  := "cadena sin cerrar;   --  error lexico: falta la comilla final
begin

   Contador := 10 $ 2;         --  error lexico: caracter '$' no valido

   Total := Contador * 2       --  error sintactico: falta ';'

   if Contador > 0             --  error sintactico: falta 'then'
      Total := Total + 1;
   end if;

   Total := ;                  --  error sintactico: expresion invalida

end Con_Errores;
