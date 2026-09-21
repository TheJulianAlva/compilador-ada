function Suma (A, B : Integer) return Integer is
begin
   null;
end Suma;

procedure Ejemplo2 is
   type Vector is array (1 .. 5) of Integer;
   type Punto is record
      X : Integer;
      Y : Integer;
   end record;

   V : Vector;
   P : Punto;
begin
   V(1) := 10;
   P.X := 1;
   P.Y := Suma(P.X, V(1));
end Ejemplo2;
