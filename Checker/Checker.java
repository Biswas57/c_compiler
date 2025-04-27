/*
 * checker.java
 *
 * this file does the semantic analysis for a vc program.
 * it checks scopes, type rules, and decorates the ast with type info.
 * all comments below are in a chill, lowercase tone.
 *
 * sun 09 mar 2025 08:44:27 aedt
 *
 */

 package VC.Checker;

 import VC.ASTs.*;
 import VC.Scanner.SourcePosition;
 import VC.ErrorReporter;
 import VC.StdEnvironment;
 
 import java.util.Objects;
 import java.util.Optional;
 
 public final class Checker implements Visitor {
     private enum ErrorMessage {
         MISSING_MAIN("*0: main function is missing"),
         MAIN_RETURN_TYPE_NOT_INT("*1: return type of main is not int"),
         IDENTIFIER_REDECLARED("*2: identifier redeclared: %"),
         IDENTIFIER_DECLARED_VOID("*3: identifier declared void: %"),
         IDENTIFIER_DECLARED_VOID_ARRAY("*4: identifier declared void[]: %"),
         IDENTIFIER_UNDECLARED("*5: identifier undeclared: %"),
         INCOMPATIBLE_TYPE_FOR_ASSIGNMENT("*6: incompatible type for ="),
         INVALID_LVALUE_IN_ASSIGNMENT("*7: invalid lvalue in assignment"),
         INCOMPATIBLE_TYPE_FOR_RETURN("*8: incompatible type for return"),
         INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR("*9: incompatible type for this binary operator: %"),
         INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR("*10: incompatible type for this unary operator: %"),
         ARRAY_FUNCTION_AS_SCALAR("*11: attempt to use an array/function as a scalar: %"),
         SCALAR_FUNCTION_AS_ARRAY("*12: attempt to use a scalar/function as an array"),
         WRONG_TYPE_FOR_ARRAY_INITIALISER("*13: wrong type for element in array initialiser: at position %"),
         INVALID_INITIALISER_ARRAY_FOR_SCALAR("*14: invalid initialiser: array initialiser for scalar"),
         INVALID_INITIALISER_SCALAR_FOR_ARRAY("*15: invalid initialiser: scalar initialiser for array: %"),
         EXCESS_ELEMENTS_IN_ARRAY_INITIALISER("*16: excess elements in array initialiser: %"),
         ARRAY_SUBSCRIPT_NOT_INTEGER("*17: array subscript is not an integer"),
         ARRAY_SIZE_MISSING("*18: array size missing: %"),
         SCALAR_ARRAY_AS_FUNCTION("*19: attempt to reference a scalar/array as a function: %"),
         IF_CONDITIONAL_NOT_BOOLEAN("*20: if conditional is not boolean"),
         FOR_CONDITIONAL_NOT_BOOLEAN("*21: for conditional is not boolean"),
         WHILE_CONDITIONAL_NOT_BOOLEAN("*22: while conditional is not boolean"),
         BREAK_NOT_IN_LOOP("*23: break must be in a while/for"),
         CONTINUE_NOT_IN_LOOP("*24: continue must be in a while/for"),
         TOO_MANY_ACTUAL_PARAMETERS("*25: too many actual parameters"),
         TOO_FEW_ACTUAL_PARAMETERS("*26: too few actual parameters"),
         WRONG_TYPE_FOR_ACTUAL_PARAMETER("*27: wrong type for actual parameter: %"),
         MISC_1("*28: misc 1"),
         MISC_2("*29: misc 2"),
         STATEMENTS_NOT_REACHED("*30: statement(s) not reached"),
         MISSING_RETURN_STATEMENT("*31: missing return statement");
 
         private final String message;
 
         ErrorMessage(String message) {
             this.message = message;
         }
 
         public String getMessage() {
             return message;
         }
     };
 
     // our symbol table for scope checking
     private SymbolTable symbolTable;
     // dummy position used for standard environment functions
     private static SourcePosition defaultPos = new SourcePosition();
     // error reporter to pass errors to
     private ErrorReporter errorReporter;
     // track loop nesting level for break/continue validation
     private int loopDepth = 0;
 
     // constructor - sets up our error reporter and standard environment
     public Checker(ErrorReporter errorRep) {
         this.errorReporter = errorRep;
         this.symbolTable = new SymbolTable();
         establishStdEnvironment();
     }
 
     /*
      * helper function: convert an int expression to a float expression.
      * this is used when we need to force a conversion to maintain type
      * compatibility.
      */
     private Expr i2f(Expr expr) {
         UnaryExpr convExpr = new UnaryExpr(new Operator("i2f", expr.position), expr, expr.position);
         convExpr.type = StdEnvironment.floatType;
         convExpr.parent = (AST) expr;
         return convExpr;
     }
 
     /*
      * check an assignment by comparing the target type and the expression type.
      * if they're not exactly equal, try to convert (i2f) if possible, otherwise
      * flag an error.
      */
     private Expr checkAssignment(Type targetType, Expr expr, String errorMsg, SourcePosition pos) {
         if (!targetType.assignable(expr.type)) {
             // uh oh, types aren't compatible here
             this.errorReporter.reportError(errorMsg, "", pos);
         } else if (!targetType.equals(expr.type)) {
             // auto-conversion from int to float if needed
             return i2f(expr);
         }
         return expr;
     }
 
     // declare a variable and check for redeclaration in the current scope
     private void declareVariable(Ident identifier, Decl decl) {
         if (this.symbolTable.retrieveOneLevel(identifier.spelling).isPresent())
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_REDECLARED.getMessage(), identifier.spelling,
                     identifier.position);
         this.symbolTable.insert(identifier.spelling, decl);
     }
 
     // declare a function and check for redeclaration in the current scope
     private void declareFunction(Ident identifier, Decl decl) {
         if (this.symbolTable.retrieveOneLevel(identifier.spelling).isPresent())
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_REDECLARED.getMessage(), identifier.spelling,
                     identifier.position);
         this.symbolTable.insert(identifier.spelling, decl);
     }
 
     // report error taking into account if there's an error type present
     void reportError(String message, Type foundType, String requiredType, SourcePosition pos) {
         if (foundType == StdEnvironment.errorType) {
             this.errorReporter.reportError(message, "", pos);
         } else {
             this.errorReporter.reportError(message + " (found: " + foundType + ", required: " + requiredType + ")", "",
                     pos);
         }
     }
 
     // starting point: kick off the checking by visiting the whole ast
     public void check(AST ast) {
         ast.visit(this, null);
     }
 
     // program level check: make sure main is declared correctly
     public Object visitProgram(Program program, Object ctx) {
         program.FL.visit(this, null);
         Decl mainDecl = this.symbolTable.retrieve("main").map(e -> e.attr).orElse(null);
         if (mainDecl == null || !(mainDecl instanceof FuncDecl)) {
             this.errorReporter.reportError(ErrorMessage.MISSING_MAIN.getMessage(), "", program.position);
         } else if (!StdEnvironment.intType.equals(((FuncDecl) mainDecl).T)) {
             this.errorReporter.reportError(ErrorMessage.MAIN_RETURN_TYPE_NOT_INT.getMessage(), "", program.position);
         }
         return null;
     }
 
     public Object visitIfStmt(IfStmt ifStmt, Object arg) {
         Type condType = (Type) ifStmt.E.visit(this, null);
         if (!condType.equals(StdEnvironment.booleanType))
             this.errorReporter.reportError(
                     ErrorMessage.IF_CONDITIONAL_NOT_BOOLEAN.getMessage() + " (found: " + condType.toString() + ")",
                     "", ifStmt.E.position);
         ifStmt.S1.visit(this, arg);
         ifStmt.S2.visit(this, arg);
         return null;
     }
 
     public Object visitCompoundStmt(CompoundStmt compStmt, Object ctx) {
         // open a new scope for this compound statement
         this.symbolTable.openScope();
         if (ctx != null && ctx instanceof FuncDecl) {
             FuncDecl fnDecl = (FuncDecl) ctx;
             fnDecl.PL.visit(this, null);
             compStmt.DL.visit(this, null);
             compStmt.SL.visit(this, fnDecl.T.visit(this, null));
         } else {
             compStmt.DL.visit(this, null);
             compStmt.SL.visit(this, ctx);
         }
         this.symbolTable.closeScope();
         return null;
     }
 
     // note: i won't comment every function here, just the ones i feel need some
     // extra notes :)
     public Object visitStmtList(StmtList stmtList, Object ctx) {
         stmtList.S.visit(this, ctx);
         if (stmtList.S instanceof ReturnStmt && stmtList.SL instanceof StmtList)
             this.errorReporter.reportError(ErrorMessage.STATEMENTS_NOT_REACHED.getMessage(), "", stmtList.SL.position);
         stmtList.SL.visit(this, ctx);
         return null;
     }
 
     public Object visitForStmt(ForStmt forStmt, Object ctx) {
         // bump up loop depth since we're in a loop now
         this.loopDepth++;
         forStmt.E1.visit(this, null);
         Type condType = (Type) forStmt.E2.visit(this, null);
         if (!forStmt.E2.isEmptyExpr() && !condType.equals(StdEnvironment.booleanType))
             this.errorReporter.reportError(
                     ErrorMessage.FOR_CONDITIONAL_NOT_BOOLEAN.getMessage() + " (found: " + condType.toString() + ")",
                     "", forStmt.E2.position);
         forStmt.E3.visit(this, null);
         forStmt.S.visit(this, ctx);
         // done with loop scope here
         this.loopDepth--;
         return null;
     }
 
     public Object visitWhileStmt(WhileStmt whileStmt, Object ctx) {
         this.loopDepth++;
         Type condType = (Type) whileStmt.E.visit(this, null);
         if (!condType.equals(StdEnvironment.booleanType))
             this.errorReporter.reportError(
                     ErrorMessage.WHILE_CONDITIONAL_NOT_BOOLEAN.getMessage() + " (found: " + condType.toString() + ")",
                     "", whileStmt.E.position);
         whileStmt.S.visit(this, ctx);
         this.loopDepth--;
         return null;
     }
 
     public Object visitBreakStmt(BreakStmt breakStmt, Object ctx) {
         if (this.loopDepth < 1)
             this.errorReporter.reportError(ErrorMessage.BREAK_NOT_IN_LOOP.getMessage(), "", breakStmt.position);
         return null;
     }
 
     public Object visitContinueStmt(ContinueStmt contStmt, Object ctx) {
         if (this.loopDepth < 1)
             this.errorReporter.reportError(ErrorMessage.CONTINUE_NOT_IN_LOOP.getMessage(), "", contStmt.position);
         return null;
     }
 
     public Object visitReturnStmt(ReturnStmt returnStmt, Object expectedType) {
         Type expType = (Type) expectedType;
         returnStmt.E.visit(this, expectedType);
         // check if the return type matches what we expect
         returnStmt.E = checkAssignment(expType, returnStmt.E,
                 ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN.getMessage(), returnStmt.position);
         return null;
     }
 
     public Object visitExprStmt(ExprStmt exprStmt, Object ctx) {
         exprStmt.E.visit(this, ctx);
         return null;
     }
 
     public Object visitEmptyCompStmt(EmptyCompStmt emptyCompStmt, Object ctx) {
         this.symbolTable.openScope();
         if (ctx != null && ctx instanceof FuncDecl) {
             FuncDecl fnDecl = (FuncDecl) ctx;
             fnDecl.PL.visit(this, null);
         }
         this.symbolTable.closeScope();
         return null;
     }
 
     public Object visitEmptyStmt(EmptyStmt emptyStmt, Object ctx) {
         return null;
     }
 
     public Object visitEmptyStmtList(EmptyStmtList emptyStmtList, Object ctx) {
         return null;
     }
 
     public Object visitAssignExpr(AssignExpr assignExpr, Object ctx) {
         assignExpr.E1.visit(this, ctx);
         assignExpr.E2.visit(this, null);
         if (!(assignExpr.E1 instanceof VarExpr) && !(assignExpr.E1 instanceof ArrayExpr)) {
             this.errorReporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage(), "",
                     assignExpr.position);
         } else if (assignExpr.E1 instanceof VarExpr) {
             SimpleVar simpVar = (SimpleVar) ((VarExpr) assignExpr.E1).V;
             Decl varDecl = (Decl) simpVar.I.decl;
             if (varDecl instanceof FuncDecl)
                 this.errorReporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage() + ": %",
                         simpVar.I.spelling,
                         assignExpr.position);
         }
         assignExpr.E2 = checkAssignment(assignExpr.E1.type, assignExpr.E2,
                 ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(),
                 assignExpr.position);
         assignExpr.type = assignExpr.E2.type;
         return assignExpr.type;
     }
 
     public Object visitBinaryExpr(BinaryExpr binaryExpr, Object ctx) {
         Type leftType = (Type) binaryExpr.E1.visit(this, ctx);
         Type rightType = (Type) binaryExpr.E2.visit(this, ctx);
         Type resultType = leftType;
         String opStr = binaryExpr.O.spelling;
         boolean isError = false;
         boolean isLogical = (opStr.equals("&&") || opStr.equals("||"));
         boolean isEqualOp = (opStr.equals("==") || opStr.equals("!="));
         boolean isRelational = (opStr.equals("<=") || opStr.equals(">=") || opStr.equals("<") || opStr.equals(">"));
         if (leftType.isErrorType() || rightType.isErrorType()) {
             resultType = StdEnvironment.errorType;
         } else if (leftType.isVoidType() || rightType.isVoidType()) {
             isError = true;
         } else if (leftType.isStringType() || rightType.isStringType()) {
             isError = true;
         } else if (leftType.isArrayType() || rightType.isArrayType()) {
             isError = true;
         } else if (leftType.isBooleanType() || rightType.isBooleanType()) {
             if (!leftType.equals(rightType) || (!isLogical && !isEqualOp))
                 isError = true;
             binaryExpr.O.spelling = "i" + opStr;
         } else if (isLogical) {
             isError = true;
         } else if (!leftType.equals(rightType)) {
             resultType = StdEnvironment.floatType;
             binaryExpr.O.spelling = "f" + opStr;
             if (!resultType.equals(leftType)) {
                 binaryExpr.E1 = i2f(binaryExpr.E1);
             } else {
                 binaryExpr.E2 = i2f(binaryExpr.E2);
             }
         } else if (leftType.isFloatType()) {
             binaryExpr.O.spelling = "f" + opStr;
         } else {
             binaryExpr.O.spelling = "i" + opStr;
         }
         if (isError) {
             this.errorReporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), opStr,
                     binaryExpr.position);
             resultType = StdEnvironment.errorType;
         }
         binaryExpr.type = (isEqualOp || isRelational) ? StdEnvironment.booleanType : resultType;
         return binaryExpr.type;
     }
 
     public Object visitUnaryExpr(UnaryExpr unaryExpr, Object ctx) {
         Type type = (Type) unaryExpr.E.visit(this, ctx);
         String opStr = unaryExpr.O.spelling;
         boolean errorFound = false;
         if (type.isErrorType()) {
             type = StdEnvironment.errorType;
         } else if (type.isVoidType() || type.isStringType() || type.isArrayType()) {
             errorFound = true;
         } else if ((opStr.equals("!") && !type.isBooleanType()) || (!opStr.equals("!") && type.isBooleanType())) {
             errorFound = true;
         }
         if (errorFound) {
             this.errorReporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), opStr,
                     unaryExpr.position);
             type = StdEnvironment.errorType;
         } else if (type.isFloatType()) {
             unaryExpr.O.spelling = "f" + opStr;
         } else {
             unaryExpr.O.spelling = "i" + opStr;
         }
         unaryExpr.type = type;
         return unaryExpr.type;
     }
 
     public Object visitCallExpr(CallExpr callExpr, Object ctx) {
         Decl calledDecl = (Decl) callExpr.I.visit(this, null);
         if (calledDecl == null) {
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_UNDECLARED.getMessage(), callExpr.I.spelling,
                     callExpr.position);
             callExpr.type = StdEnvironment.errorType;
         } else if (calledDecl instanceof FuncDecl) {
             callExpr.AL.visit(this, ((FuncDecl) calledDecl).PL);
             callExpr.type = ((FuncDecl) calledDecl).T;
         } else {
             this.errorReporter.reportError(ErrorMessage.SCALAR_ARRAY_AS_FUNCTION.getMessage(), callExpr.I.spelling,
                     callExpr.I.position);
             callExpr.type = StdEnvironment.errorType;
         }
         return callExpr.type;
     }
 
     public Object visitArrayExpr(ArrayExpr arrayExpr, Object ctx) {
         Type varType = (Type) arrayExpr.V.visit(this, ctx);
         if (varType.isArrayType()) {
             varType = ((ArrayType) varType).T;
         } else if (!varType.isErrorType()) {
             this.errorReporter.reportError(ErrorMessage.SCALAR_FUNCTION_AS_ARRAY.getMessage(), "", arrayExpr.position);
             varType = StdEnvironment.errorType;
         }
         Type subscriptType = (Type) arrayExpr.E.visit(this, ctx);
         SourcePosition pos = new SourcePosition(arrayExpr.V.position.lineStart,
                 arrayExpr.E.position.lineFinish,
                 arrayExpr.V.position.charStart, arrayExpr.E.position.charFinish);
         if (!subscriptType.isIntType() && !subscriptType.isErrorType())
             this.errorReporter.reportError(ErrorMessage.ARRAY_SUBSCRIPT_NOT_INTEGER.getMessage(), "",
                     pos);
         arrayExpr.type = varType;
         return varType;
     }
 
     public Object visitArrayExprList(ArrayExprList arrayExList, Object elemType) {
         arrayExList.E.visit(this, elemType);
         arrayExList.E = checkAssignment((Type) elemType, arrayExList.E,
                 ErrorMessage.WRONG_TYPE_FOR_ARRAY_INITIALISER.getMessage() + arrayExList.index,
                 arrayExList.E.position);
         if (arrayExList.EL instanceof ArrayExprList) {
             ((ArrayExprList) arrayExList.EL).index = arrayExList.index + 1;
             return arrayExList.EL.visit(this, elemType);
         }
         return Integer.valueOf(arrayExList.index + 1);
     }
 
     public Object visitArrayInitExpr(ArrayInitExpr arrayInitExpr, Object expectedType) {
         Type expType = (Type) expectedType;
         if (!expType.isArrayType()) {
             this.errorReporter.reportError(ErrorMessage.INVALID_INITIALISER_ARRAY_FOR_SCALAR.getMessage(), " ",
                     arrayInitExpr.position);
             arrayInitExpr.type = StdEnvironment.errorType;
             return arrayInitExpr.type;
         }
         return arrayInitExpr.IL.visit(this, ((ArrayType) expType).T);
     }
 
     public Object visitExprList(ArrayExprList exprList, Object elemType) {
         exprList.E.visit(this, elemType);
         exprList.E = checkAssignment((Type) elemType, exprList.E,
                 ErrorMessage.WRONG_TYPE_FOR_ARRAY_INITIALISER.getMessage() + exprList.index,
                 exprList.E.position);
         if (exprList.EL instanceof ArrayExprList) {
             exprList.index++;
             return exprList.EL.visit(this, elemType);
         }
         return exprList.index + 1;
     }
 
     public Object visitEmptyArrayExprList(EmptyArrayExprList emptyArrayExprList, Object elemType) {
         return null;
     }
 
     public Object visitEmptyExpr(EmptyExpr emptyExpr, Object ctx) {
         if (emptyExpr.parent instanceof ReturnStmt) {
             emptyExpr.type = StdEnvironment.voidType;
         } else {
             emptyExpr.type = StdEnvironment.errorType;
         }
         return emptyExpr.type;
     }
 
     public Object visitBooleanExpr(BooleanExpr boolExpr, Object ctx) {
         boolExpr.type = StdEnvironment.booleanType;
         return boolExpr.type;
     }
 
     public Object visitIntExpr(IntExpr intExpr, Object ctx) {
         intExpr.type = StdEnvironment.intType;
         return intExpr.type;
     }
 
     public Object visitFloatExpr(FloatExpr floatExpr, Object ctx) {
         floatExpr.type = StdEnvironment.floatType;
         return floatExpr.type;
     }
 
     public Object visitVarExpr(VarExpr varExpr, Object ctx) {
         varExpr.type = (Type) varExpr.V.visit(this, null);
         return varExpr.type;
     }
 
     public Object visitStringExpr(StringExpr stringExpr, Object ctx) {
         stringExpr.type = StdEnvironment.stringType;
         return stringExpr.type;
     }
 
     public Object visitFuncDecl(FuncDecl funcDecl, Object ctx) {
         // register the function in the current scope, then check its body
         declareFunction(funcDecl.I, (Decl) funcDecl);
         if (funcDecl.S.isEmptyCompStmt() &&
                 !funcDecl.T.equals(StdEnvironment.voidType))
             this.errorReporter.reportError(ErrorMessage.MISSING_RETURN_STATEMENT.getMessage(), "", funcDecl.position);
         funcDecl.S.visit(this, funcDecl);
         return null;
     }
 
     // helper to get the last statement in a list
     // it's here for completeness
     private Stmt getLastStmt(List listNode) {
         Stmt lastStmt = null;
         while (!(listNode instanceof EmptyStmtList)) {
             if (listNode instanceof StmtList) {
                 StmtList stmtListNode = (StmtList) listNode;
                 lastStmt = stmtListNode.S;
                 listNode = stmtListNode.SL;
             } else {
                 break;
             }
         }
         return lastStmt;
     }
 
     public Object visitDeclList(DeclList declList, Object ctx) {
         declList.D.visit(this, null);
         declList.DL.visit(this, null);
         return null;
     }
 
     public Object visitEmptyDeclList(EmptyDeclList emptyDeclList, Object ctx) {
         return null;
     }
 
     public Object visitGlobalVarDecl(GlobalVarDecl globalVarDecl, Object ctx) {
         declareVariable(globalVarDecl.I, (Decl) globalVarDecl);
         if (globalVarDecl.T.isVoidType()) {
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), globalVarDecl.I.spelling,
                     globalVarDecl.I.position);
         } else if (globalVarDecl.T.isArrayType()) {
             if (((ArrayType) globalVarDecl.T).T.isVoidType())
                 this.errorReporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(),
                         globalVarDecl.I.spelling,
                         globalVarDecl.I.position);
             if (((ArrayType) globalVarDecl.T).E.isEmptyExpr() && !(globalVarDecl.E instanceof ArrayInitExpr))
                 this.errorReporter.reportError(ErrorMessage.ARRAY_SIZE_MISSING.getMessage(), globalVarDecl.I.spelling,
                         globalVarDecl.I.position);
         }
         Object initResult = globalVarDecl.E.visit(this, globalVarDecl.T);
         if (globalVarDecl.T.isArrayType()) {
             if (globalVarDecl.E instanceof ArrayInitExpr) {
                 Integer initCount = (Integer) initResult;
                 ArrayType arrType = (ArrayType) globalVarDecl.T;
                 if (arrType.E.isEmptyExpr()) {
                     arrType.E = (Expr) new IntExpr(new IntLiteral(initCount.toString(), defaultPos), defaultPos);
                 } else {
                     int declaredSize = Integer.parseInt(((IntExpr) arrType.E).IL.spelling);
                     int actualCount = initCount.intValue();
                     if (declaredSize < actualCount)
                         this.errorReporter.reportError(ErrorMessage.EXCESS_ELEMENTS_IN_ARRAY_INITIALISER.getMessage(),
                                 globalVarDecl.I.spelling,
                                 globalVarDecl.position);
                 }
             } else if (!globalVarDecl.E.isEmptyExpr()) {
                 this.errorReporter.reportError(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY.getMessage(),
                         globalVarDecl.I.spelling,
                         globalVarDecl.position);
             }
         } else {
             globalVarDecl.E = checkAssignment(globalVarDecl.T, globalVarDecl.E,
                     ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(),
                     globalVarDecl.position);
         }
         return null;
     }
 
     public Object visitLocalVarDecl(LocalVarDecl localVarDecl, Object ctx) {
         declareVariable(localVarDecl.I, (Decl) localVarDecl);
         if (localVarDecl.T.isVoidType()) {
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), localVarDecl.I.spelling,
                     localVarDecl.I.position);
         } else if (localVarDecl.T.isArrayType()) {
             if (((ArrayType) localVarDecl.T).T.isVoidType())
                 this.errorReporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(),
                         localVarDecl.I.spelling,
                         localVarDecl.I.position);
             if (((ArrayType) localVarDecl.T).E.isEmptyExpr() && !(localVarDecl.E instanceof ArrayInitExpr))
                 this.errorReporter.reportError(ErrorMessage.ARRAY_SIZE_MISSING.getMessage(), localVarDecl.I.spelling,
                         localVarDecl.I.position);
         }
         Object initResult = localVarDecl.E.visit(this, localVarDecl.T);
         if (localVarDecl.T.isArrayType()) {
             if (localVarDecl.E instanceof ArrayInitExpr) {
                 Integer initCount = (Integer) initResult;
                 ArrayType arrType = (ArrayType) localVarDecl.T;
                 if (arrType.E.isEmptyExpr()) {
                     arrType.E = (Expr) new IntExpr(new IntLiteral(initCount.toString(), defaultPos), defaultPos);
                 } else {
                     int declaredSize = Integer.parseInt(((IntExpr) arrType.E).IL.spelling);
                     int actualCount = initCount.intValue();
                     if (declaredSize < actualCount)
                         this.errorReporter.reportError(ErrorMessage.EXCESS_ELEMENTS_IN_ARRAY_INITIALISER.getMessage(),
                                 localVarDecl.I.spelling,
                                 localVarDecl.position);
                 }
             } else if (!localVarDecl.E.isEmptyExpr()) {
                 this.errorReporter.reportError(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY.getMessage(),
                         localVarDecl.I.spelling,
                         localVarDecl.position);
             }
         } else {
             localVarDecl.E = checkAssignment(localVarDecl.T, localVarDecl.E,
                     ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(),
                     localVarDecl.position);
         }
         return null;
     }
 
     public Object visitParaList(ParaList paraList, Object ctx) {
         paraList.P.visit(this, null);
         paraList.PL.visit(this, null);
         return null;
     }
 
     public Object visitParaDecl(ParaDecl paraDecl, Object ctx) {
         declareVariable(paraDecl.I, (Decl) paraDecl);
         if (paraDecl.T.isVoidType()) {
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), paraDecl.I.spelling,
                     paraDecl.I.position);
         } else if (paraDecl.T.isArrayType() &&
                 ((ArrayType) paraDecl.T).T.isVoidType()) {
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(),
                     paraDecl.I.spelling, paraDecl.I.position);
         }
         return null;
     }
 
     public Object visitEmptyParaList(EmptyParaList emptyParaList, Object ctx) {
         return null;
     }
 
     public Object visitEmptyArgList(EmptyArgList emptyArgList, Object paraListCtx) {
         List paraList = (List) paraListCtx;
         if (!paraList.isEmptyParaList())
             this.errorReporter.reportError(ErrorMessage.TOO_FEW_ACTUAL_PARAMETERS.getMessage(), "",
                     emptyArgList.position);
         return null;
     }
 
     public Object visitArgList(ArgList argList, Object paraListCtx) {
         List paraList = (List) paraListCtx;
         if (paraList.isEmptyParaList()) {
             this.errorReporter.reportError(ErrorMessage.TOO_MANY_ACTUAL_PARAMETERS.getMessage(), "",
                     argList.position);
         } else {
             argList.A.visit(this, ((ParaList) paraList).P);
             argList.AL.visit(this, ((ParaList) paraList).PL);
         }
         return null;
     }
 
     public Object visitArg(Arg arg, Object paraDeclCtx) {
         ParaDecl paramDecl = (ParaDecl) paraDeclCtx;
         Type argType = (Type) arg.E.visit(this, null);
         boolean typeMismatch = false;
         Type declType = paramDecl.T;
         if (declType.isArrayType()) {
             if (!argType.isArrayType()) {
                 typeMismatch = true;
             } else {
                 Type arrElemTypeDecl = ((ArrayType) declType).T;
                 Type arrElemTypeArg = ((ArrayType) argType).T;
                 if (!arrElemTypeDecl.assignable(arrElemTypeArg))
                     typeMismatch = true;
             }
         } else if (!paramDecl.T.assignable(argType)) {
             typeMismatch = true;
         }
         if (typeMismatch)
             this.errorReporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(),
                     paramDecl.I.spelling,
                     arg.E.position);
         if (paramDecl.T.equals(StdEnvironment.floatType) && argType.equals(StdEnvironment.intType))
             arg.E = i2f(arg.E);
         return null;
     }
 
     @Override
     public Object visitErrorType(ErrorType errorType, Object ctx) {
         return StdEnvironment.errorType;
     }
 
     public Object visitBooleanType(BooleanType boolType, Object ctx) {
         return StdEnvironment.booleanType;
     }
 
     public Object visitIntType(IntType intType, Object ctx) {
         return StdEnvironment.intType;
     }
 
     public Object visitFloatType(FloatType floatType, Object ctx) {
         return StdEnvironment.floatType;
     }
 
     public Object visitStringType(StringType strType, Object ctx) {
         return StdEnvironment.stringType;
     }
 
     public Object visitVoidType(VoidType voidType, Object ctx) {
         return StdEnvironment.voidType;
     }
 
     public Object visitArrayType(ArrayType arrayType, Object ctx) {
         return arrayType;
     }
 
     public Object visitIdent(Ident ident, Object ctx) {
         Optional<IdEntry> bindingOpt = symbolTable.retrieve(ident.spelling);
         bindingOpt.ifPresent(e -> ident.decl = e.attr);
         return bindingOpt.map(e -> e.attr).orElse(null);
     }
 
     public Object visitBooleanLiteral(BooleanLiteral boolLiteral, Object ctx) {
         return StdEnvironment.booleanType;
     }
 
     public Object visitIntLiteral(IntLiteral intLiteral, Object ctx) {
         return StdEnvironment.intType;
     }
 
     public Object visitFloatLiteral(FloatLiteral floatLiteral, Object ctx) {
         return StdEnvironment.floatType;
     }
 
     public Object visitStringLiteral(StringLiteral strLiteral, Object ctx) {
         return StdEnvironment.stringType;
     }
 
     public Object visitOperator(Operator operator, Object ctx) {
         return null;
     }
 
     public Object visitSimpleVar(SimpleVar simpleVar, Object ctx) {
         simpleVar.type = StdEnvironment.errorType;
         Decl varDecl = (Decl) simpleVar.I.visit(this, null);
         if (varDecl == null) {
             this.errorReporter.reportError(ErrorMessage.IDENTIFIER_UNDECLARED.getMessage(), simpleVar.I.spelling,
                     simpleVar.position);
         } else if (varDecl instanceof FuncDecl) {
             this.errorReporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), simpleVar.I.spelling,
                     simpleVar.I.position);
         } else {
             simpleVar.type = varDecl.T;
         }
         if (simpleVar.type.isArrayType() && simpleVar.parent instanceof VarExpr
                 && !(simpleVar.parent.parent instanceof Arg))
             this.errorReporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), simpleVar.I.spelling,
                     simpleVar.I.position);
         return simpleVar.type;
     }
 
     // helper function to setup the standard environment functions
     private FuncDecl declareStdFunc(Type retType, String name, List params) {
         FuncDecl stdFuncDecl = new FuncDecl(retType, new Ident(name, defaultPos), params,
                 (Stmt) new EmptyStmt(defaultPos), defaultPos);
         this.symbolTable.insert(name, (Decl) stdFuncDecl);
         return stdFuncDecl;
     }
 
     private static final Ident defaultIdent = new Ident("x", defaultPos);
 
     private void establishStdEnvironment() {
         StdEnvironment.booleanType = new BooleanType(defaultPos);
         StdEnvironment.intType = new IntType(defaultPos);
         StdEnvironment.floatType = new FloatType(defaultPos);
         StdEnvironment.stringType = new StringType(defaultPos);
         StdEnvironment.voidType = new VoidType(defaultPos);
         StdEnvironment.errorType = new ErrorType(defaultPos);
         StdEnvironment.getIntDecl = declareStdFunc(StdEnvironment.intType, "getInt",
                 new EmptyParaList(defaultPos));
         StdEnvironment.putIntDecl = declareStdFunc(StdEnvironment.voidType, "putInt",
                 new ParaList(new ParaDecl(StdEnvironment.intType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putIntLnDecl = declareStdFunc(StdEnvironment.voidType, "putIntLn",
                 new ParaList(new ParaDecl(StdEnvironment.intType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.getFloatDecl = declareStdFunc(StdEnvironment.floatType, "getFloat",
                 new EmptyParaList(defaultPos));
         StdEnvironment.putFloatDecl = declareStdFunc(StdEnvironment.voidType, "putFloat",
                 new ParaList(new ParaDecl(StdEnvironment.floatType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putFloatLnDecl = declareStdFunc(StdEnvironment.voidType, "putFloatLn",
                 new ParaList(new ParaDecl(StdEnvironment.floatType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putBoolDecl = declareStdFunc(StdEnvironment.voidType, "putBool",
                 new ParaList(new ParaDecl(StdEnvironment.booleanType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putBoolLnDecl = declareStdFunc(StdEnvironment.voidType, "putBoolLn",
                 new ParaList(new ParaDecl(StdEnvironment.booleanType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putStringLnDecl = declareStdFunc(StdEnvironment.voidType, "putStringLn",
                 new ParaList(new ParaDecl(StdEnvironment.stringType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putStringDecl = declareStdFunc(StdEnvironment.voidType, "putString",
                 new ParaList(new ParaDecl(StdEnvironment.stringType, defaultIdent, defaultPos),
                         new EmptyParaList(defaultPos), defaultPos));
         StdEnvironment.putLnDecl = declareStdFunc(StdEnvironment.voidType, "putLn", new EmptyParaList(defaultPos));
     }
 }
 