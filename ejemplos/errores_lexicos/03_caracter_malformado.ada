--  Error lexico: literal de caracter con mas de un caracter dentro de
--  las comillas simples. Lo detecta el token trampa <CARACTER_MALFORMADO>.
--  Un literal valido tiene exactamente un caracter: 'a', 'Z', '9'.

procedure Caracter_Malformado is
   C1 : Character := 'a';     --  valido
   C2 : Character := 'ab';    --  malformado: dos caracteres
   C3 : Character := 'xyz';   --  malformado: tres caracteres
begin
   C1 := 'Q';
end Caracter_Malformado;
