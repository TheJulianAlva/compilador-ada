procedure Tipos is
   type Grado is range 0 .. 100;
   type Color is (Rojo, Verde, Azul);
   type Punto is record
      X : Integer;
      Y : Integer;
   end record;
   type Fila is array (1 .. 8) of Integer;
   subtype Positivo is Integer range 1 .. 100;
begin
   null;
end Tipos;
