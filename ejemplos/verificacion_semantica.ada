--  Ejemplo minimo para comprobar la verificacion semantica:
--  declaraciones, asignaciones, compatibilidad de tipos y funciones.
--  Las funciones son unidades de nivel superior (no anidadas), como
--  exige el subconjunto de Ada soportado por este compilador.

function Es_Templado (Temp : Integer) return Boolean is
   Resultado : Boolean;
begin
   Resultado := Temp >= 15 and then Temp <= 25;
end Es_Templado;

function A_Fahrenheit (Temp : Integer) return Integer is
   Resultado : Integer;
begin
   Resultado := Temp * 9 / 5 + 32;
end A_Fahrenheit;

procedure Verificacion_Semantica is

   type Celsius is range -50 .. 50;

   Temperatura : Celsius := 20;
   Grados_F    : Integer;
   Agradable   : Boolean;

begin

   --  Asignacion valida: el literal cabe en el rango de Celsius.
   Temperatura := 30;

   --  Conversion explicita (Celsius -> Integer) para pasarla a una
   --  funcion que espera Integer, y asignacion del resultado a una
   --  variable de tipo compatible (Boolean <- Boolean).
   Agradable := Es_Templado(Integer(Temperatura));

   Grados_F := A_Fahrenheit(Integer(Temperatura));

   if Agradable then
      Grados_F := Grados_F + 1;
   end if;

   --  Descomentar para ver el error semantico de tipos incompatibles:
   --  Agradable := Grados_F;

end Verificacion_Semantica;
