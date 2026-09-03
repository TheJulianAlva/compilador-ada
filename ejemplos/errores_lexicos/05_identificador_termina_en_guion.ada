--  Error lexico: identificador que termina en guion bajo. Ada exige que
--  un identificador termine en letra o digito, nunca en '_'.
--  Lo detecta el token trampa <IDENT_MALFORMADO>.

procedure Identificador_Termina_En_Guion is
   contador_ : Integer := 0;   --  malformado: termina en '_'
   suma_     : Integer := 0;   --  malformado: termina en '_'
   promedio  : Integer := 0;   --  valido
begin
   contador_ := contador_ + 1;
   promedio  := suma_ / contador_;
end Identificador_Termina_En_Guion;
