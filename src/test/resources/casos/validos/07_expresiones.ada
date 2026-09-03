procedure Expr is
   A, B, C : Integer;
   R : Boolean;
begin
   A := 1 + 2 * 3 - 4 / 2;
   B := 2 ** 3;
   C := A mod B rem 2;
   R := (A > 0 and then B < 100) or else not (C = 0);
end Expr;
