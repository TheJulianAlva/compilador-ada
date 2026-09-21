function Doble (N : Integer) return Integer is
begin
   null;
end Doble;

procedure Ejemplo3 is
   X : Integer := 5;
   Y : Integer;
begin
   if X in 1 .. 10 then
      Y := Doble(X);
   end if;
   for I in 1 .. 10 loop
      Y := Y + I;
   end loop;
end Ejemplo3;
