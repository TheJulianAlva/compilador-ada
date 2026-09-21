procedure Ejemplo is
   X : Integer := 10;
   Y : constant Float := 3.14;
   Activo : Boolean := True;
   Letra : Character := 'a';
   Nombre : String := "hola";
begin
   X := X + 1;
   Activo := X > 5 and then Y > 0.0;
   if Activo then
      X := X * 2;
   end if;
   while X < 100 loop
      X := X + 1;
   end loop;
   for I in 1 .. 10 loop
      X := X + I;
   end loop;
end Ejemplo;
