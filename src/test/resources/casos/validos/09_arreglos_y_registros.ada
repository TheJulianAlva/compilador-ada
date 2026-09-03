procedure Arreglos_Y_Registros is
   type Vec is array (1 .. 10) of Integer;
   type Punto is record
      X : Integer;
      Y : Integer;
   end record;
   A : Vec;
   R : Punto;
   I : Integer := 1;
begin
   A(I) := 0;
   A(1) := 42;
   R.X := 0;
   R.Y := A(I);
end Arreglos_Y_Registros;
