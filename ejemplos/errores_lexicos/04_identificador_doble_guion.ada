--  Error lexico: identificador con doble guion bajo. Ada prohibe "__"
--  dentro de un identificador. Lo detecta el token trampa
--  <IDENT_MALFORMADO>. Los identificadores con un solo guion bajo
--  entre letras/digitos si son validos (mi_variable, total_general).

procedure Identificador_Doble_Guion is
   mi_variable   : Integer := 1;   --  valido
   total__parcial : Integer := 2;  --  malformado: contiene "__"
   x__y__z       : Integer := 3;   --  malformado: dos veces "__"
begin
   mi_variable := total__parcial + x__y__z;
end Identificador_Doble_Guion;
