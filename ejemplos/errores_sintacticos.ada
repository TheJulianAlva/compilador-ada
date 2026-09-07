--  Ejemplo con SOLO errores de sintaxis (nada léxicamente inválido).
--  Como la fase léxica está limpia, el parser sí se ejecuta y reporta
--  TODOS los errores sintácticos de una pasada, recuperándose en cada
--  punto de fallo. Se muestran en el panel "Sintácticos".

procedure Errores_Sintacticos is
   Contador : Integer := 0
   Total    : Integer := 0;
begin

   Total := Contador * 2

   if Contador > 0
      Total := Total + 1;
   end if;

   for I in 1 .. 10 loop
      Total := Total + I;

end Errores_Sintacticos;
