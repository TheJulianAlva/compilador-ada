--  Error lexico: literal de cadena sin la comilla de cierre antes de
--  que termine la linea. Lo detecta el token trampa <CADENA_SIN_CERRAR>.

procedure Cadena_Sin_Cerrar is
   Saludo : String := "hola mundo;      --  falta la comilla final aqui
   Nombre : String := "Ada";            --  esta si esta bien cerrada
begin
   Nombre := "otra cadena sin cerrar    --  y aqui otra vez
end Cadena_Sin_Cerrar;
