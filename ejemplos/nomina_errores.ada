-- =========================================================================
--  Sistema de nomina de una empresa (CON ERRORES)
-- -------------------------------------------------------------------------
--  Mismo programa que nomina_completo.ada con errores agregados.
--  Comente las lineas marcadas (--) por etapa y vuelva a compilar:
--    [L1]        1 error lexico
--    [P1] [P2]   2 errores sintacticos
--    [S1]..[S5]  5 errores semanticos
--    [I1]..[I4]  4 errores de codigo intermedio (no se comentan)
-- =========================================================================

-- -------------------------------------------------------------------------
--  1. Especificacion del paquete: tipos y constantes del dominio
-- -------------------------------------------------------------------------
package Empresa is

   type Departamento is (Ventas, Almacen, Sistemas, Contabilidad, Direccion);

   type Nivel_Puesto is range 1 .. 5;
   type Antiguedad   is range 0 .. 40;

   subtype Indice_Empleado is Integer range 1 .. 50;
   subtype Monto           is Float;

   type Empleado is record
      Numero       : Integer;
      Nombre       : String;
      Area         : Departamento;
      Nivel        : Nivel_Puesto;
      Anios        : Antiguedad;
      Sueldo_Diario : Monto;
      Activo       : Boolean := True;
   end record;

   type Plantilla is array (1 .. 50) of Empleado;

   Total_Plazas   : constant Indice_Empleado := 50;
   Tasa_Isr       : constant Monto := 0.10;
   Tasa_Imss      : constant Monto := 0.025;
   Prima_Vacacional : constant Monto := 0.25;
   Mascara_Activos : constant Integer := 2#1111_0000#;
   Clave_Empresa  : constant Integer := 16#A2F#;

end Empresa;


-- -------------------------------------------------------------------------
--  2. Da de alta la plantilla vacia
-- -------------------------------------------------------------------------
procedure Inicializar_Plantilla (Lista : out Plantilla; Ocupadas : out Integer) is
   Vacante : Empleado;
begin
   Vacante.Numero        := 0;
   Vacante.Nombre        := "-- vacante --";
   Vacante.Area          := Ventas;
   Vacante.Nivel         := 1;
   Vacante.Anios         := 0;
   Vacante.Sueldo_Diario := 0.0;
   Vacante.Activo        := False;
   Vacante.Salario       := 0.0;      --  [S1] "Salario" no es campo de Empleado.

   for Posicion in 1 .. Total_Plazas loop
      Lista(Posicion) := Vacante;
      Lista(Posicion).Numero := Posicion;
   end loop;

   Ocupadas := 0;
end Inicializar_Plantilla;


-- -------------------------------------------------------------------------
--  3. Dias de vacaciones segun la antiguedad (Ley Federal del Trabajo)
-- -------------------------------------------------------------------------
function Dias_Vacaciones (Anios : in Antiguedad) return Integer is
   Dias : Integer;
begin
   if Anios = 0 then
      Dias := 0;
      Dias := Dias_Extra;             --  [S2] "Dias_Extra" no esta declarado.
   elsif Anios in 1 .. 5 then
      Dias := 10 + Integer(Anios) * 2;
   elsif Anios in 6 .. 10 then
      Dias := 22;
      Dias := (Dias + );              --  [P1] falta un operando en la suma.
   else
      Dias := 22 + (Integer(Anios) - 10) / 5 * 2;
   end if;
end Dias_Vacaciones;


-- -------------------------------------------------------------------------
--  4. Aplica un aumento a todos los empleados activos de un departamento
-- -------------------------------------------------------------------------
procedure Aplicar_Aumento (Lista      : in out Plantilla;
                           Area       : in Departamento;
                           Porcentaje : in Monto;
                           Afectados  : out Integer) is
   Posicion : Integer := 1;
begin
   Afectados := 0;
   Afectados := Afectados ? 1;        --  [L1] '?' no pertenece al lenguaje.
   Total_Plazas := 60;                --  [S3] "Total_Plazas" es constant.

   while Posicion <= Total_Plazas loop
      if Lista(Posicion).Activo and then Lista(Posicion).Area = Area then
         Lista(Posicion).Sueldo_Diario :=
            Lista(Posicion).Sueldo_Diario + Lista(Posicion).Sueldo_Diario * Porcentaje;
         Afectados := Afectados + 1;
      end if;
      Posicion := Posicion + 1;
   end loop;

   if Porcentaje < 0.0 then
      raise Constraint_Error;
   end if;

exception
   when Constraint_Error =>
      Afectados := 0;
   when others =>
      raise;
end Aplicar_Aumento;


-- -------------------------------------------------------------------------
--  5. Revisa la plantilla de atras hacia adelante y cuenta por nivel
-- -------------------------------------------------------------------------
procedure Revisar_Plantilla (Lista : in Plantilla; Senior : out Integer) is
   type Conteo_Niveles is array (1 .. 5) of Integer;
   Conteos   : Conteo_Niveles;
   Inicial   : constant Character := 'S';
   Separador : constant String := "----------------------------";
begin
   for N in 1 .. 5 loop
      Conteos(N) := 0;
   end loop;

   Senior := 0;
   Senior := True;                    --  [S4] tipos incompatibles: Integer <- Boolean.

   for Posicion in reverse 1 .. Total_Plazas loop
      if Lista(Posicion).Activo then
         if Lista(Posicion).Nivel >= 4 or else Lista(Posicion).Anios > 15 then
            Senior := Senior + 1;
         end if;
         if Integer(Lista(Posicion).Anios) not in 0 .. 1 then
            Conteos(2) := Conteos(2) + 1;
         else
            Conteos(1) := Conteos(1) + 1;
         end if;
      end if;
   end loop;
end Revisar_Plantilla;


-- -------------------------------------------------------------------------
--  6. Procedimiento PRINCIPAL (el ultimo): calculo de la nomina del mes.
-- -------------------------------------------------------------------------
procedure Calcular_Nomina is

   --  Constantes que se pliegan en la optimizacion
   Dias_Mes      : constant Integer := 6 * 5;        -- 30
   Horas_Jornada : constant Integer := 2 ** 3;       -- 8
   Tope_Horas    : constant Integer := 3 * 4 * 10;   -- 120
   Isr           : constant Float := 0.10;
   Imss          : constant Float := 0.025;

   --  Datos de entrada
   Empleados     : Integer := 12;
   Horas_Extra   : Integer := 7;
   Faltas        : Integer := 3;
   Sueldo_Diario : Float := 450.0 * 2.0;              -- 900.0
   Bono_Fijo     : Float := 1_500.0;

   --  Resultados enteros
   Horas_Mes     : Integer;
   Total_Horas   : Integer;
   Extra_Doble   : Integer;
   Cuadrado      : Integer;
   Residuo       : Integer;
   Indice        : Integer;
   Dias_Pagados  : Integer;
   Revisados     : Integer;

   --  Resultados reales
   Bruto         : Float;
   Impuestos     : Float;
   Neto          : Float;
   Promedio      : Float;

   Hay_Extras    : Boolean := False;
   Plantilla_Mes : Plantilla;
   Ocupadas      : Integer;

   --  Datos para los errores de codigo intermedio
   Cero          : Integer := 0;
   Grande        : Integer := 2_000_000_000;
   Sin_Dato      : Integer;
   Prueba        : Integer;

begin

   --  Horas trabajadas en el mes
   Horas_Mes   := Dias_Mes * Horas_Jornada;
   Total_Horas := Horas_Mes * Empleados + Horas_Extra * 2;      -- * 2 -> suma
   Extra_Doble := (Horas_Extra + 2 * 3) * (Dias_Mes - 10 + 4);  -- se pliega 2 * 3
   Cuadrado    := Horas_Extra ** 2 + Faltas ** 2;                -- ** 2 -> producto

   --  Aritmetica entera: mod, rem, abs y division
   Residuo      := Total_Horas mod 7 + Total_Horas rem 5 - abs (-Faltas);
   Dias_Pagados := Dias_Mes - Faltas + Horas_Extra / Horas_Jornada;
   Indice       := (Empleados - Faltas) * 100 / Empleados;
   Indice       := Indice * ;                    --  [P2] falta el operando derecho.
   Dias_Pagados := Dias_Vacaciones(True);        --  [S5] se esperaba Antiguedad, no Boolean.

   --  Aritmetica real: sueldo bruto, impuestos y neto
   Bruto     := Sueldo_Diario * 30.0 + Bono_Fijo;
   Impuestos := Bruto * Isr + Bruto * Imss;
   Neto      := Bruto - Impuestos;
   Promedio  := Neto / 2.0 + 1.5 * 2.0;                          -- se pliega 1.5 * 2.0

   --  Errores de codigo intermedio (aparecen al quitar los anteriores)
   Prueba := Empleados / Cero;                   --  [I1] division entre cero.
   Prueba := Grande * Empleados;                 --  [I2] desbordamiento de Integer.
   Prueba := Sin_Dato + Faltas;                  --  [I3] 'Sin_Dato' no tiene valor.
   Prueba := Horas_Extra ** (Faltas - 5);        --  [I4] exponente negativo.

   --  Las variables asignadas dentro de if / for / while quedan "sin valor"
   --  en el codigo intermedio porque dependen del flujo de ejecucion.
   if Total_Horas > Tope_Horas * Empleados then
      Hay_Extras := True;
   elsif Horas_Extra = 0 then
      Hay_Extras := False;
   else
      Hay_Extras := Horas_Extra > 5;
   end if;

   --  Llamadas a procedimientos con argumentos
   Inicializar_Plantilla(Plantilla_Mes, Ocupadas);
   Aplicar_Aumento(Plantilla_Mes, Sistemas, 0.05, Ocupadas);
   Revisar_Plantilla(Plantilla_Mes, Revisados);

   while Faltas > 0 loop
      Faltas := Faltas - 1;
   end loop;

end Calcular_Nomina;
