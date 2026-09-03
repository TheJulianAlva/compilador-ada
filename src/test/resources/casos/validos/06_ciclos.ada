procedure Ciclos is
   Total : Integer;
begin
   Total := 0;
   for I in reverse 1 .. 10 loop
      Total := Total + I;
   end loop;
   while Total > 0 loop
      Total := Total - 1;
   end loop;
end Ciclos;
