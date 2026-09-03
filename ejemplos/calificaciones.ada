--  Ejemplo del subconjunto de Ada soportado por el compilador.
--  Cubre: tipos del usuario (range, enumerado, record, array), constantes,
--  if/elsif/else, for (con reverse), while, expresiones con toda la
--  precedencia, pertenencia (in), concatenacion, asignacion a elemento de
--  arreglo y a campo de registro, y manejo de excepciones a nivel del
--  procedimiento.

procedure Calificaciones is

   type Nota is range 0 .. 100;
   type Letra is (F, D, C, B, A);

   type Alumno is record
      Promedio : Nota;
      Grado    : Letra;
      Aprobado : Boolean;
   end record;

   type Grupo is array (1 .. 5) of Alumno;

   Minimo_Aprobatorio : constant Nota := 60;

   Salon : Grupo;
   Suma  : Integer;
   Doble : Integer;
   Prom  : Nota;
   I     : Integer;
   Texto : String := "promedio " & "del salon";

begin

   --  Registro de calificaciones: asignacion a campos de un elemento de arreglo.
   Salon(1).Promedio := 95;
   Salon(2).Promedio := 82;
   Salon(3).Promedio := 58;
   Salon(4).Promedio := 71;
   Salon(5).Promedio := 100;

   for K in 1 .. 5 loop
      Prom := Salon(K).Promedio;

      if Prom >= 90 then
         Salon(K).Grado := A;
      elsif Prom >= 80 then
         Salon(K).Grado := B;
      elsif Prom >= 70 then
         Salon(K).Grado := C;
      elsif Prom >= Minimo_Aprobatorio then
         Salon(K).Grado := D;
      else
         Salon(K).Grado := F;
      end if;

      Salon(K).Aprobado := Prom in Minimo_Aprobatorio .. 100;
   end loop;

   --  Suma de promedios recorriendo el arreglo en orden inverso.
   Suma := 0;
   for K in reverse 1 .. 5 loop
      Suma := Suma + Integer(Salon(K).Promedio);
   end loop;

   --  Descuenta hasta cero con un ciclo while.
   I := 5;
   while I >= 1 loop
      I := I - 1;
   end loop;

   --  Expresion con toda la precedencia: ** , * / , + - , and then , not.
   Doble := 2 ** 3 + Suma * (Suma - 1) / 2;
   if Doble > 0 and then not (Suma = 0) then
      Suma := Doble mod 100;
   end if;

exception
   when Constraint_Error | Program_Error =>
      Suma := 0;
   when others =>
      raise;

end Calificaciones;
