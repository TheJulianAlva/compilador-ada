-- =========================================================================
--  Sistema de gestion de inventario de una ferreteria
-- -------------------------------------------------------------------------
--  Ejemplo EXTENSO (> 200 lineas) del subconjunto de Ada soportado por el
--  compilador. Recorre casi todas las construcciones del subconjunto:
--
--    * package (especificacion) con tipos, subtipos y constantes
--    * tipos del usuario: rango, enumerado, registro, arreglo
--    * procedimientos y funciones con parametros en modos in / out / in out
--    * declaraciones de variables y constantes con inicializacion
--    * sentencias if / elsif / else, for, for reverse, while, null
--    * asignacion a elemento de arreglo  A(i) := ...
--    * asignacion a campo de registro    R.campo := ...
--    * asignacion combinada              A(i).campo := ...
--    * expresiones con toda la precedencia: ** , * / mod rem , + - & ,
--      relacionales , pertenencia (in / not in) , and then / or else , not , abs
--    * literales: enteros con separador '_', reales, con base (16#..#, 2#..#),
--      de caracter, de cadena, True / False , null
--    * bloques de manejo de excepciones con varios manejadores y raise
--    * comentarios de linea
--
--  El archivo se compone de VARIAS unidades de compilacion al hilo (el
--  subconjunto no admite subprogramas anidados), como permite la gramatica.
--  Debe compilar con CERO errores lexicos y CERO errores sintacticos.
-- =========================================================================


-- -------------------------------------------------------------------------
--  1. Especificacion del paquete: tipos y constantes del dominio
-- -------------------------------------------------------------------------
package Inventario is

   --  Categorias de producto de la ferreteria
   type Categoria is (Herramienta, Tornilleria, Pintura, Electrico, Plomeria, Jardin);

   --  Estado de una linea de inventario
   type Estado_Stock is (Agotado, Critico, Bajo, Normal, Excedente);

   --  Rangos con restriccion
   type Codigo_Producto is range 1000 .. 9999;
   type Cantidad        is range 0 .. 100_000;
   type Porcentaje      is range 0 .. 100;

   --  Subtipos derivados
   subtype Indice_Almacen is Integer range 1 .. 200;
   subtype Precio         is Float;

   --  Un producto del catalogo
   type Producto is record
      Codigo      : Codigo_Producto;
      Nombre      : String;
      Clase       : Categoria;
      Existencia  : Cantidad;
      Minimo      : Cantidad;
      Costo       : Precio;
      Activo      : Boolean := True;
   end record;

   --  El almacen completo: arreglo de productos
   type Almacen is array (1 .. 200) of Producto;

   --  Historial de movimientos de un dia
   type Dia_Movimientos is array (1 .. 500) of Integer;

   --  Constantes de negocio
   Capacidad_Maxima   : constant Indice_Almacen := 200;
   Iva                : constant Precio := 0.16;
   Margen_Minimo      : constant Porcentaje := 15;
   Descuento_Mayoreo  : constant Precio := 0.08;
   Umbral_Critico     : constant Cantidad := 5;

end Inventario;


-- -------------------------------------------------------------------------
--  2. Inicializa todas las posiciones del almacen a un producto vacio
-- -------------------------------------------------------------------------
procedure Inicializar_Almacen (Deposito : out Almacen; Total : out Integer) is
   Vacio : Producto;
   I     : Indice_Almacen;
begin
   Vacio.Codigo     := 1000;
   Vacio.Nombre     := "-- libre --";
   Vacio.Clase      := Herramienta;
   Vacio.Existencia := 0;
   Vacio.Minimo     := 0;
   Vacio.Costo      := 0.0;
   Vacio.Activo     := False;

   for Posicion in 1 .. Capacidad_Maxima loop
      Deposito(Posicion) := Vacio;
      Deposito(Posicion).Codigo := 1000;
   end loop;

   Total := 0;
end Inicializar_Almacen;


-- -------------------------------------------------------------------------
--  3. Busca un producto por codigo; deja el indice en Resultado (0 = no)
-- -------------------------------------------------------------------------
function Buscar_Producto (Deposito : in Almacen;
                          Objetivo : in Codigo_Producto) return Integer is
   Resultado   : Integer := 0;
   Encontrado  : Boolean := False;
   Posicion    : Integer := 1;
begin
   while Posicion <= Capacidad_Maxima and then not Encontrado loop
      if Deposito(Posicion).Activo and then Deposito(Posicion).Codigo = Objetivo then
         Resultado  := Posicion;
         Encontrado := True;
      end if;
      Posicion := Posicion + 1;
   end loop;

   if not Encontrado then
      Resultado := 0;
   end if;
end Buscar_Producto;


-- -------------------------------------------------------------------------
--  4. Clasifica el nivel de stock de una linea segun su minimo
-- -------------------------------------------------------------------------
function Clasificar_Stock (Actual : in Cantidad;
                           Minimo : in Cantidad) return Estado_Stock is
   Nivel  : Estado_Stock;
   Holgura : Integer;
begin
   Holgura := Integer(Actual) - Integer(Minimo);

   if Actual = 0 then
      Nivel := Agotado;
   elsif Actual in 1 .. Umbral_Critico then
      Nivel := Critico;
   elsif Holgura < 0 then
      Nivel := Bajo;
   elsif Holgura in 0 .. 50 then
      Nivel := Normal;
   else
      Nivel := Excedente;
   end if;
end Clasificar_Stock;


-- -------------------------------------------------------------------------
--  5. Registra una entrada o salida de mercancia sobre una posicion
-- -------------------------------------------------------------------------
procedure Registrar_Movimiento (Deposito  : in out Almacen;
                                Posicion  : in Indice_Almacen;
                                Cambio    : in Integer;
                                Aceptado  : out Boolean) is
   Existencia_Nueva : Integer;
   Es_Entrada       : Boolean;
begin
   Aceptado   := False;
   Es_Entrada := Cambio > 0;

   if not Deposito(Posicion).Activo then
      Aceptado := False;
   else
      Existencia_Nueva := Integer(Deposito(Posicion).Existencia) + Cambio;

      if Existencia_Nueva < 0 then
         --  no se puede sacar mas de lo que hay
         Aceptado := False;
      elsif Existencia_Nueva > 100_000 then
         --  desbordaria la capacidad del tipo Cantidad
         raise Constraint_Error;
      else
         Deposito(Posicion).Existencia := Cantidad(Existencia_Nueva);
         Aceptado := True;
      end if;
   end if;

exception
   when Constraint_Error =>
      Aceptado := False;
   when others =>
      Aceptado := False;
      raise;
end Registrar_Movimiento;


-- -------------------------------------------------------------------------
--  6. Suma el valor monetario de todo el inventario (con IVA)
-- -------------------------------------------------------------------------
function Calcular_Valor_Total (Deposito : in Almacen) return Float is
   Acumulado : Precio := 0.0;
   Parcial   : Precio;
   Unidades  : Integer;
begin
   for Posicion in 1 .. 200 loop
      if Deposito(Posicion).Activo then
         Unidades  := Integer(Deposito(Posicion).Existencia);
         Parcial   := Deposito(Posicion).Costo * 2.0;
         Acumulado := Acumulado + Parcial + Parcial * Iva;
      end if;
   end loop;
end Calcular_Valor_Total;


-- -------------------------------------------------------------------------
--  7. Genera el reporte diario: recorre el almacen, cuenta por estado,
--     arma las lineas criticas y calcula indicadores del dia.
-- -------------------------------------------------------------------------
procedure Generar_Reporte (Deposito : in Almacen) is

   type Conteo_Estados is array (1 .. 5) of Integer;

   Conteos       : Conteo_Estados;
   Movimientos   : Dia_Movimientos;
   Total_Activos : Integer := 0;
   Total_Piezas  : Integer := 0;
   Alertas       : Integer := 0;
   Indice_Salud  : Integer;
   Separador     : constant String := "----------------------------------------";
   Marca         : constant Character := '*';
   Bandera_Hex   : constant Integer := 16#00FF#;
   Mascara_Bin   : constant Integer := 2#1010_1010#;
   Nivel         : Estado_Stock;
   Hay_Criticos  : Boolean := False;

begin

   --  Inicializa los contadores por estado
   for E in 1 .. 5 loop
      Conteos(E) := 0;
   end loop;

   --  Limpia el buffer de movimientos del dia en orden inverso
   for K in reverse 1 .. 500 loop
      Movimientos(K) := 0;
   end loop;

   --  Recorrido principal del almacen
   for Posicion in 1 .. 200 loop
      if Deposito(Posicion).Activo then
         Total_Activos := Total_Activos + 1;
         Total_Piezas  := Total_Piezas + Integer(Deposito(Posicion).Existencia);

         Nivel := Clasificar_Stock(Deposito(Posicion).Existencia,
                                   Deposito(Posicion).Minimo);

         if Nivel = Agotado or else Nivel = Critico then
            Hay_Criticos := True;
            Alertas      := Alertas + 1;
            if Posicion <= 500 then
               Movimientos(Posicion) := Posicion * 10 + 1;
            end if;
         end if;

         if Deposito(Posicion).Existencia = 0 then
            Conteos(1) := Conteos(1) + 1;
         elsif Deposito(Posicion).Existencia in 1 .. 5 then
            Conteos(2) := Conteos(2) + 1;
         elsif Integer(Deposito(Posicion).Existencia) < Integer(Deposito(Posicion).Minimo) then
            Conteos(3) := Conteos(3) + 1;
         elsif Integer(Deposito(Posicion).Existencia) not in 0 .. 1000 then
            Conteos(5) := Conteos(5) + 1;
         else
            Conteos(4) := Conteos(4) + 1;
         end if;
      end if;
   end loop;

   --  Indicador de salud del inventario: entre 0 y 100
   if Total_Activos > 0 then
      Indice_Salud := 100 - (Alertas * 100) / Total_Activos;
   else
      Indice_Salud := 0;
   end if;

   if Indice_Salud < 0 then
      Indice_Salud := abs Indice_Salud;
   end if;

   --  Ajuste ludico usando mod, rem y exponenciacion (no asociativa)
   Indice_Salud := (Indice_Salud + Bandera_Hex mod 7) rem 101;
   Indice_Salud := Indice_Salud + 2 ** 3 - Mascara_Bin mod 5;

   --  Marca de cierre del reporte
   if Hay_Criticos and then Alertas > 0 then
      Total_Piezas := Total_Piezas + 0;
   end if;

   while Alertas > 0 loop
      Alertas := Alertas - 1;
   end loop;

exception
   when Constraint_Error | Program_Error =>
      Total_Activos := 0;
      Total_Piezas  := 0;
   when others =>
      raise;
end Generar_Reporte;
