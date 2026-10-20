
procedure Codigo_Intermedio is

   Base     : constant Integer := 3 * 4;
   A        : Integer := 7;
   B        : Integer := 3;
   Total    : Integer;
   Residuo  : Integer;
   Potencia : Integer;
   Promedio : Float := 2.5;
   Area     : Float;
   Sin_Valor : Integer;
   Resultado : Integer;

begin

   Total := A + B * 2;

   Total := (A + B) * (Base - 2);

   Residuo  := Total mod 7 + Total - 7 + A / B;
   Potencia := 2 ** 5 + abs (-A);

   Area := Promedio * Promedio * 3.0 + 0.5;

   Resultado := -A + 255;

   Resultado := A / (B - 3);

   -- Area := A + 1;

end Codigo_Intermedio;
