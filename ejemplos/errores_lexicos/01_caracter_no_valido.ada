--  Error lexico: caracteres que ningun token del lenguaje acepta.
--  El lexer los captura con el token trampa <LEXEMA_INVALIDO>
--  (o <ERROR_LEXICO> si el caracter esta completamente aislado),
--  los marca como TipoToken.ERROR en la tabla y sigue analizando.
--  Si el caracter ajeno estuviera PEGADO a letras/digitos (p. ej. "i@f"),
--  saldria como un unico token de error, no partido en tres.

procedure Caracter_No_Valido is
   X : Integer := 0;
begin
   X := X $ 1;        --  '$' no es un caracter valido de Ada
   X := X ? 2;        --  '?' tampoco
   X := X @ 3;        --  '@' tampoco
end Caracter_No_Valido;
