--  Ejemplo para la generacion de codigo intermedio (solo aritmetica).
--  Compila (F5) y abre la pestana "Codigo intermedio": por cada expresion
--  se muestra prefijo, posfijo, cuadruplos y el valor calculado.
--  Para ver el error de tipos del semantico (y que NO se genere codigo
--  intermedio), descomenta la linea marcada con [ERROR DE TIPOS].

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

   --  Precedencia: A + (B * 2)
   Total := A + B * 2;

   --  Parentesis: (A + B) * (Base - 2)
   Total := (A + B) * (Base - 2);

   --  Division entera, mod, rem y exponente
   Residuo  := Total mod 7 + Total rem 7 + A / B;
   Potencia := 2 ** 5 + abs (-A);

   --  Aritmetica en Float
   Area := Promedio * Promedio * 3.0 + 0.5;

   --  Signo unario y basado
   Resultado := -A + 16#FF#;

   --  Variable sin valor: se reporta en la fila, no se asume 0
   Resultado := Sin_Valor + 1;

   --  Division entre cero: error de evaluacion en la fila
   Resultado := A / (B - 3);

   --  Dentro de if/while/for no se genera codigo intermedio
   if A > B then
      Total := 0;
   end if;

   --  [ERROR DE TIPOS]  Area := A + 1;

end Codigo_Intermedio;
