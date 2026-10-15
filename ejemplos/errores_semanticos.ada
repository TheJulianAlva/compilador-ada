--  Ejemplo con SOLO errores semanticos (sintaxis y lexico limpios).
--  El parser se ejecuta completo y el verificador semantico reporta
--  TODOS los errores de una pasada. Se muestran en el panel "Semanticos".
--  Cada linea marcada dispara exactamente una regla del verificador.

function Doble (X : Integer) return Integer is
   Resultado : Integer;
begin
   Resultado := X * 2;
end Doble;

procedure Errores_Semanticos is

   type Nota is range 0 .. 100;

   type Persona is record
      Edad : Integer;
   end record;

   type Lista is array (1 .. 3) of Integer;

   Maximo   : constant Integer := 100;
   Total    : Integer;
   Total    : Float;          --  (1) redeclarado: "Total" ya existe en este ambito.
   Valor    : Desconocido;    --  (2) tipo no declarado: "Desconocido" no existe.
   Persona1 : Persona;
   Numeros  : Lista;
   N        : Nota;
   Etiqueta : Boolean;

begin

   Total := Sin_Declarar + 1;   --  (3)  no declarado: "Sin_Declarar" no existe.

   Maximo := 50;                --  (4)  asignacion a constante: "Maximo" es constant.

   Total := True;                --  (5)  tipos incompatibles: Integer <- Boolean.

   if Total then                 --  (6)  la condicion del if debe ser Boolean.
      Total := 0;
   end if;

   Numeros(True) := 1;           --  (7)  indice no entero: se indexa con Boolean.

   Total := Total(1);            --  (8)  no es arreglo: "Total" no es indexable.

   Persona1.Apellido := 10;      --  (9)  campo no existe: "Apellido" no esta en Persona.

   N.Campo := 1;                 --  (10) no es registro: "N" no es un record.

   Total := Doble(1, 2);         --  (11) aridad incorrecta: "Doble" espera 1 argumento.

   Total := Doble(True);         --  (12) argumento incompatible: se esperaba Integer.

   N := Nota(Total, Total);      --  (13) conversion: se espera exactamente un argumento.

   N := Nota(Etiqueta);          --  (14) conversion invalida: Boolean no es numerico.

end Errores_Semanticos;
