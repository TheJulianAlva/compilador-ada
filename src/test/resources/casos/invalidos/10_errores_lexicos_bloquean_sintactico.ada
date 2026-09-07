--  Este archivo tiene errores lexicos (contador_ y @) Y errores de
--  sintaxis (faltan ';'). El .expected solo lista los DOS lexicos:
--  al haber errores lexicos, el analisis sintactico no se ejecuta.
procedure P is
   contador_ : Integer := 1 @ 2
begin
   null
end P;
