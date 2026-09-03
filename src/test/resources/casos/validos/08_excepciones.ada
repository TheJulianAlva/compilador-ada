procedure Exc is
   X : Integer;
begin
   X := 1;
exception
   when Constraint_Error | Program_Error =>
      X := 0;
   when others =>
      raise;
end Exc;
