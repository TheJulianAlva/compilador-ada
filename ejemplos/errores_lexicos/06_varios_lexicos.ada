--  Varios errores lexicos de distinto tipo en un mismo archivo.
--  El compilador debe reportarlos TODOS en una sola pasada, cada uno
--  con su linea y columna, en el panel de errores lexicos (separado
--  del panel de errores sintacticos).

procedure Varios_Lexicos is
   Etiqueta : String    := "sin cerrar          --  cadena sin comilla final
   Inicial  : Character := 'ab';                --  caracter malformado
   dato__x  : Integer   := 0;                   --  identificador con "__"
   n_       : Integer   := 0;                   --  identificador termina en "_"
begin
   dato__x := n_ # 5;                           --  '#' suelto: caracter no valido
   dato__x := dato__x \ 2;                      --  '\' : caracter no valido
end Varios_Lexicos;
