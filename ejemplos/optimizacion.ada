--  Ejemplo de la etapa de optimizacion.
--  Los comentarios, los espacios y las lineas en blanco desaparecen; las
--  expresiones con solo literales se pliegan y la potencia se reduce.

procedure Optimizacion is

   Base     : constant Integer := 3 * 4;      -- se pliega: 12
   A        : Integer := 7;
   B        : Integer := 3;
   Total    : Integer;
   Cuadrado : Integer;
   Doble    : Integer;
   Mixto    : Integer;
   Mitad    : Float;
   Peso     : Float := 2.5;
   Nulo     : Integer;

begin

   Total    := 2 ** 5 + A * 2;        -- 32 + (A + A)
   Cuadrado := A ** 2 + B ** 2;       -- A * A + B * B
   Doble    := 2 * (A + B) * B;       -- no se toca el operando compuesto
   Mixto    := (A + 2 * 3) * (Base - 10 + 4);
   Mitad    := Peso / 2.0 + 1.5 * 2.0;

   if Total > 10 * 10 - 60 then
      Nulo := B * 2;
   end if;

   --  La division entre cero se conserva: sigue fallando en ejecucion igual que antes.
   Nulo := A / (3 - 3);

end Optimizacion;
