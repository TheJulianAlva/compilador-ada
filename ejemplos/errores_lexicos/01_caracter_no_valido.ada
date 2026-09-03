--  Error lexico: caracteres que ningun token del lenguaje acepta.
--  El lexer los captura con la regla catch-all <ERROR_LEXICO: ~[]>,
--  los marca como TipoToken.ERROR en la tabla y sigue analizando.

procedure Caracter_No_Valido is
   X : Integer := 0;
begin
   X := X $ 1;        --  '$' no es un caracter valido de Ada
   X := X ? 2;        --  '?' tampoco
   X := X @ 3;        --  '@' tampoco
end Caracter_No_Valido;
