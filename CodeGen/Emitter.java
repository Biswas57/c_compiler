 package VC.CodeGen;

 import VC.ASTs.*;
 import VC.ErrorReporter;
 import VC.StdEnvironment;
 import java.util.HashMap;
 
 public final class Emitter implements Visitor {
 
     private ErrorReporter errorReporter;
     private String inputFilename;
     private String classname;
     private String outputFilename;
     // Auxiliary fields
     private boolean func_return_mark;
     private HashMap<String, Integer> localvars;
     private HashMap<String, AST> functions;
 
     public Emitter(String inputFilename, ErrorReporter reporter) {
         this.inputFilename = inputFilename;
         errorReporter = reporter;
         func_return_mark = false;
         localvars = new HashMap<>();
         functions = new HashMap<>();
         int i = inputFilename.lastIndexOf('.');
         if (i > 0)
             classname = inputFilename.substring(0, i);
         else
             classname = inputFilename;
     }
 
     public final void gen(AST ast) {
         ast.visit(this, null);
         JVM.dump(classname + ".j");
     }
 
     // Programs
     public Object visitProgram(Program ast, Object o) {
         // Generate default constructor initializer
         emit(JVM.CLASS, "public", classname);
         emit(JVM.SUPER, "java/lang/Object");
         emit("");
 
         // (1) Generate .field definitions.
         VC.ASTs.List list = ast.FL;
         while (!list.isEmpty()) {
             DeclList dlAST = (DeclList) list;
             if (dlAST.D instanceof GlobalVarDecl) {
                 GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
                 emit(JVM.STATIC_FIELD, vAST.I.spelling, VCtoJavaType(vAST.T));
             }
             list = dlAST.DL;
         }
         emit("");
 
         // (2) Generate <clinit> for global variables.
         emit("; standard class static initializer ");
         emit(JVM.METHOD_START, "static <clinit>()V");
         emit("");
 
         Frame frame = new Frame(false);
         list = ast.FL;
         while (!list.isEmpty()) {
             DeclList dlAST = (DeclList) list;
             if (dlAST.D instanceof GlobalVarDecl) {
                 GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
                 if (vAST.T.isArrayType()) {
                     ArrayType arr_type = (ArrayType) vAST.T;
                     arr_type.visit(this, frame);
                     if (!vAST.E.isEmptyExpr()) {
                         vAST.E.visit(this, frame);
                     }
                     emitPUTSTATIC(VCtoJavaType(arr_type), vAST.I.spelling);
                     frame.pop();
                 } else {
                     if (!vAST.E.isEmptyExpr()) {
                         vAST.E.visit(this, frame);
                     } else {
                         if (vAST.T.equals(StdEnvironment.floatType))
                             emit(JVM.FCONST_0);
                         else
                             emit(JVM.ICONST_0);
                         frame.push();
                     }
                     emitPUTSTATIC(VCtoJavaType(vAST.T), vAST.I.spelling);
                     frame.pop();
                 }
             }
             list = dlAST.DL;
         }
         emit("");
         emit("; set limits used by this method");
         emit(JVM.LIMIT, "locals", frame.getNewIndex());
         emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
         emit(JVM.RETURN);
         emit(JVM.METHOD_END, "method");
         emit("");
 
         // (3) Generate the non-arg constructor initializer.
         emit("; standard constructor initializer ");
         emit(JVM.METHOD_START, "public <init>()V");
         emit(JVM.LIMIT, "stack 1");
         emit(JVM.LIMIT, "locals 1");
         emit(JVM.ALOAD_0);
         emit(JVM.INVOKESPECIAL, "java/lang/Object/<init>()V");
         emit(JVM.RETURN);
         emit(JVM.METHOD_END, "method");
 
         return ast.FL.visit(this, o);
     }
 
     // Declarations
     public Object visitDeclList(DeclList ast, Object o) {
         ast.D.visit(this, o);
         ast.DL.visit(this, o);
         return null;
     }
 
     public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
         return null;
     }
 
     public Object visitFuncDecl(FuncDecl ast, Object o) {
         Frame frame;
         functions.put(ast.I.spelling, ast);
         if (ast.I.spelling.equals("main")) {
             frame = new Frame(true);
             // Reserve index 0 for String array.
             frame.getNewIndex();
             emit(JVM.METHOD_START, "public static main([Ljava/lang/String;)V");
             // Reserve index 1 for the implicit vc$ object.
             frame.getNewIndex();
         } else {
             frame = new Frame(false);
             // Reserve index 0 for "this".
             frame.getNewIndex();
             String retType = VCtoJavaType(ast.T);
             StringBuffer argsTypes = new StringBuffer("");
             VC.ASTs.List fpl = ast.PL;
             while (!fpl.isEmpty()) {
                 ParaList pl = (ParaList) fpl;
                 if (pl.P.T.equals(StdEnvironment.booleanType))
                     argsTypes.append("Z");
                 else if (pl.P.T.equals(StdEnvironment.intType))
                     argsTypes.append("I");
                 else if (pl.P.T.equals(StdEnvironment.floatType))
                     argsTypes.append("F");
                 else if (pl.P.T.isArrayType()) {
                     ArrayType arr_type = (ArrayType) pl.P.T;
                     if (arr_type.T.isIntType())
                         argsTypes.append("[I");
                     else if (arr_type.T.isFloatType())
                         argsTypes.append("[F");
                     else if (arr_type.T.isBooleanType())
                         argsTypes.append("[Z");
                 }
                 fpl = pl.PL;
             }
             emit(JVM.METHOD_START, ast.I.spelling + "(" + argsTypes + ")" + retType);
         }
 
         ast.S.visit(this, frame);
 
         // *** Fix: Instead of emitting NOP, always emit RETURN at function end.
         if (ast.T.equals(StdEnvironment.voidType)) {
             emit("");
             emit("; return may not be present in a VC function returning void");
             emit("; The following return inserted by the VC compiler");
             emit(JVM.RETURN);
         } else if (ast.I.spelling.equals("main") && !func_return_mark) {
             emit(JVM.RETURN);
         } else {
             emit(JVM.RETURN); // Changed from NOP to RETURN.
         }
         func_return_mark = false;
         emit("");
         emit("; set limits used by this method");
         emit(JVM.LIMIT, "locals", frame.getNewIndex());
         emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
         emit(".end method");
         localvars.clear();
         return null;
     }
 
     public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
         return null; // Handled in <clinit>
     }
 
     public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
         Frame frame = (Frame) o;
         ast.index = frame.getNewIndex();
         String T = VCtoJavaType(ast.T);
         emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T + " from " +
                 frame.scopeStart.peek() + " to " + frame.scopeEnd.peek());
         if (ast.T.isArrayType()) {
             localvars.put(ast.I.spelling + "[]", ast.index);
             ArrayType arr_type = (ArrayType) ast.T;
             arr_type.visit(this, o);
         } else {
             localvars.put(ast.I.spelling, ast.index);
         }
         if (!ast.E.isEmptyExpr()) {
             ast.E.visit(this, o);
         } else {
             if (!ast.T.isArrayType()) {
                 if (ast.T.equals(StdEnvironment.floatType))
                     emit(JVM.FCONST_0);
                 else
                     emit(JVM.ICONST_0);
                 frame.push();
             }
         }
         if (ast.T.isArrayType()) {
             if (ast.index >= 0 && ast.index <= 3)
                 emit(JVM.ASTORE + "_" + ast.index);
             else
                 emit(JVM.ASTORE, ast.index);
             frame.pop();
         } else if (ast.T.equals(StdEnvironment.floatType)) {
             if (ast.index >= 0 && ast.index <= 3)
                 emit(JVM.FSTORE + "_" + ast.index);
             else
                 emit(JVM.FSTORE, ast.index);
             frame.pop();
         } else {
             if (ast.index >= 0 && ast.index <= 3)
                 emit(JVM.ISTORE + "_" + ast.index);
             else
                 emit(JVM.ISTORE, ast.index);
             frame.pop();
         }
         return null;
     }
 
     // Statements
     public Object visitStmtList(StmtList ast, Object o) {
         ast.S.visit(this, o);
         ast.SL.visit(this, o);
         return null;
     }
 
     public Object visitCompoundStmt(CompoundStmt ast, Object o) {
         Frame frame = (Frame) o;
         String scopeStart = frame.getNewLabel();
         String scopeEnd = frame.getNewLabel();
         frame.scopeStart.push(scopeStart);
         frame.scopeEnd.push(scopeEnd);
         emit(scopeStart + ":");
         if (ast.parent instanceof FuncDecl) {
             if (((FuncDecl) ast.parent).I.spelling.equals("main")) {
                 emit(JVM.VAR, "0 is argv [Ljava/lang/String; from " + scopeStart + " to " + scopeEnd);
                 emit(JVM.VAR, "1 is vc$ L" + classname + "; from " + scopeStart + " to " + scopeEnd);
                 emit(JVM.NEW, classname);
                 emit(JVM.DUP);
                 frame.push(2);
                 emit("invokenonvirtual", classname + "/<init>()V");
                 frame.pop();
                 emit(JVM.ASTORE_1);
                 frame.pop();
             } else {
                 emit(JVM.VAR, "0 is this L" + classname + "; from " + scopeStart + " to " + scopeEnd);
                 ((FuncDecl) ast.parent).PL.visit(this, o);
             }
         }
         ast.DL.visit(this, o);
         ast.SL.visit(this, o);
         emit(scopeEnd + ":");
         frame.scopeStart.pop();
         frame.scopeEnd.pop();
         return null;
     }
 
     public Object visitExprStmt(ExprStmt ast, Object o) {
         Frame frame = (Frame) o;
         ast.E.visit(this, o);
         if (!((ast.E instanceof CallExpr && ast.E.type.isVoidType()) ||
                 ast.E instanceof EmptyExpr || ast.E instanceof AssignExpr)) {
             emit(JVM.POP);
             frame.pop();
         }
         return null;
     }
 
     public Object visitIfStmt(IfStmt ast, Object o) {
         Frame frame = (Frame) o;
         if (ast.S2.isEmptyStmt()) {
             String L1 = frame.getNewLabel();
             ast.E.visit(this, o);
             emit(JVM.IFEQ, L1);
             frame.pop();
             ast.S1.visit(this, o);
             emit(L1 + ":");
         } else {
             String L1 = frame.getNewLabel();
             String L2 = frame.getNewLabel();
             ast.E.visit(this, o);
             emit(JVM.IFEQ, L1);
             frame.pop();
             ast.S1.visit(this, o);
             emit(JVM.GOTO, L2);
             emit(L1 + ":");
             ast.S2.visit(this, o);
             emit(L2 + ":");
         }
         return null;
     }
 
     public Object visitWhileStmt(WhileStmt ast, Object o) {
         Frame frame = (Frame) o;
         String L1 = frame.getNewLabel();
         String L2 = frame.getNewLabel();
         frame.conStack.push(L1);
         frame.brkStack.push(L2);
         emit(L1 + ":");
         ast.E.visit(this, o);
         emit(JVM.IFEQ, L2);
         frame.pop();
         ast.S.visit(this, o);
         emit(JVM.GOTO, L1);
         emit(L2 + ":");
         frame.conStack.pop();
         frame.brkStack.pop();
         return null;
     }
 
     public Object visitForStmt(ForStmt ast, Object o) {
         Frame frame = (Frame) o;
         String L1 = frame.getNewLabel();
         String L2 = frame.getNewLabel();
         String L3 = frame.getNewLabel();
         frame.brkStack.push(L2);
         frame.conStack.push(L3);
         ast.E1.visit(this, o);
        if (!((ast.E1 instanceof CallExpr && ast.E1.type.isVoidType()) ||
            ast.E1 instanceof EmptyExpr || ast.E1 instanceof AssignExpr)) {
            emit(JVM.POP);
            frame.pop();
        }
         emit(L1 + ":");
         ast.E2.visit(this, o);
         if (!ast.E2.isEmptyExpr()) {
             emit(JVM.IFEQ, L2);
             frame.pop();
         }
         ast.S.visit(this, o);
         emit(L3 + ":");
         ast.E3.visit(this, o);
         if (!((ast.E3 instanceof CallExpr && ast.E3.type.isVoidType()) ||
                 ast.E3 instanceof EmptyExpr || ast.E3 instanceof AssignExpr)) {
             emit(JVM.POP);
             frame.pop();
         }
         emit(JVM.GOTO, L1);
         emit(L2 + ":");
         frame.conStack.pop();
         frame.brkStack.pop();
         return null;
     }
 
     public Object visitBreakStmt(BreakStmt ast, Object o) {
         Frame frame = (Frame) o;
         emit(JVM.GOTO, frame.brkStack.peek());
         return null;
     }
 
     public Object visitContinueStmt(ContinueStmt ast, Object o) {
         Frame frame = (Frame) o;
         emit(JVM.GOTO, frame.conStack.peek());
         return null;
     }
 
     public Object visitReturnStmt(ReturnStmt ast, Object o) {
         Frame frame = (Frame) o;
         if (func_return_mark)
             frame.pop();
         func_return_mark = true;
         if (frame.isMain()) {
             emit(JVM.RETURN);
             return null;
         }
         if (ast.E.isEmptyExpr()) {
             emit(JVM.RETURN);
         } else {
             ast.E.visit(this, o);
             if (ast.E.type.isFloatType())
                 emit(JVM.FRETURN);
             else
                 emit(JVM.IRETURN);
         }
         return null;
     }
 
     public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
         return null;
     }
 
     public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
         return null;
     }
 
     public Object visitEmptyStmt(EmptyStmt ast, Object o) {
         return null;
     }
 
     // Expressions
     public Object visitCallExpr(CallExpr ast, Object o) {
         Frame frame = (Frame) o;
         String fname = ast.I.spelling;
         if (fname.equals("getInt")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System.getInt()I");
             frame.push();
         } else if (fname.equals("putInt")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System.putInt(I)V");
             frame.pop();
         } else if (fname.equals("putIntLn")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/putIntLn(I)V");
             frame.pop();
         } else if (fname.equals("getFloat")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/getFloat()F");
             frame.push();
         } else if (fname.equals("putFloat")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/putFloat(F)V");
             frame.pop();
         } else if (fname.equals("putFloatLn")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/putFloatLn(F)V");
             frame.pop();
         } else if (fname.equals("putBool")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/putBool(Z)V");
             frame.pop();
         } else if (fname.equals("putBoolLn")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/putBoolLn(Z)V");
             frame.pop();
         } else if (fname.equals("putString")) {
             ast.AL.visit(this, o);
             emit(JVM.INVOKESTATIC, "VC/lang/System/putString(Ljava/lang/String;)V");
             frame.pop();
         } else if (fname.equals("putStringLn")) {
             ast.AL.visit(this, o);
             emit(JVM.INVOKESTATIC, "VC/lang/System/putStringLn(Ljava/lang/String;)V");
             frame.pop();
         } else if (fname.equals("putLn")) {
             ast.AL.visit(this, o);
             emit("invokestatic VC/lang/System/putLn()V");
         } else {
             FuncDecl fAST = (FuncDecl) functions.get(ast.I.spelling);
             if (frame.isMain())
                 emit("aload_1");
             else
                 emit("aload_0");
             frame.push();
             ast.AL.visit(this, o);
             String retType = VCtoJavaType(fAST.T);
             StringBuffer argsTypes = new StringBuffer("");
             int numArgs = 0;
             VC.ASTs.List fpl = fAST.PL;
             while (!fpl.isEmpty()) {
                 ParaList pl = (ParaList) fpl;
                 if (pl.P.T.equals(StdEnvironment.booleanType))
                     argsTypes.append("Z");
                 else if (pl.P.T.equals(StdEnvironment.intType))
                     argsTypes.append("I");
                 else if (pl.P.T.equals(StdEnvironment.floatType))
                     argsTypes.append("F");
                 else if (pl.P.T.isArrayType()) {
                     ArrayType arr_type = (ArrayType) pl.P.T;
                     if (arr_type.T.isIntType())
                         argsTypes.append("[I");
                     else if (arr_type.T.isFloatType())
                         argsTypes.append("[F");
                     else if (arr_type.T.isBooleanType())
                         argsTypes.append("[Z");
                 }
                 fpl = pl.PL;
                 numArgs++;  
             }
             emit("invokevirtual", classname + "/" + fname + "(" + argsTypes + ")" + retType);
             frame.pop(numArgs + 1);
             if (!retType.equals("V"))
                 frame.push();
         }
         return null;
     }
 
     public Object visitEmptyExpr(EmptyExpr ast, Object o) {
         return null;
     }
 
     public Object visitIntExpr(IntExpr ast, Object o) {
         ast.IL.visit(this, o);
         return null;
     }
 
     public Object visitFloatExpr(FloatExpr ast, Object o) {
         ast.FL.visit(this, o);
         return null;
     }
 
     public Object visitBooleanExpr(BooleanExpr ast, Object o) {
         ast.BL.visit(this, o);
         return null;
     }
 
     public Object visitStringExpr(StringExpr ast, Object o) {
         ast.SL.visit(this, o);
         return null;
     }
 
     public Object visitEmptyArrayExprList(EmptyArrayExprList ast, Object o) {
         return null;
     }
 
     public Object visitArrayExprList(ArrayExprList ast, Object o) {
         return null;
     }
 
     public Object visitUnaryExpr(UnaryExpr ast, Object o) {
         Frame frame = (Frame) o;
         String L1 = frame.getNewLabel();
         String L2 = frame.getNewLabel();
         String op = ast.O.spelling;
         ast.E.visit(this, o);
         if (op.equals("i!")) {
             emit(JVM.IFNE, L1);
             emitBCONST(true);
             emit(JVM.GOTO, L2);
             emit(L1 + ":");
             emitBCONST(false);
             emit(L2 + ":");
         } else if (op.equals("i-")) {
             emit(JVM.INEG);
         } else if (op.equals("f-")) {
             emit(JVM.FNEG);
         } else if (op.equals("i2f")) {
             emit(JVM.I2F);
         }
         return null;
     }
 
     public Object visitBinaryExpr(BinaryExpr ast, Object o) {
         Frame frame = (Frame) o;
         String op = ast.O.spelling;
         if (op.equals("i&&")) {
             String L1 = frame.getNewLabel();
             String L2 = frame.getNewLabel();
             ast.E1.visit(this, o);
             emit(JVM.IFEQ, L1);
             ast.E2.visit(this, o);
             emit(JVM.IFEQ, L1);
             emit(JVM.ICONST_1);
             emit(JVM.GOTO, L2);
             emit(L1 + ":");
             emit(JVM.ICONST_0);
             emit(L2 + ":");
             frame.pop(2);
             frame.push();
         } else if (op.equals("i||")) {
             String L1 = frame.getNewLabel();
             String L2 = frame.getNewLabel();
             ast.E1.visit(this, o);
             emit(JVM.IFNE, L1);
             ast.E2.visit(this, o);
             emit(JVM.IFNE, L1);
             emit(JVM.ICONST_0);
             emit(JVM.GOTO, L2);
             emit(L1 + ":");
             emit(JVM.ICONST_1);
             emit(L2 + ":");
             frame.pop(2);
             frame.push();
         } else {
             ast.E1.visit(this, o);
             ast.E2.visit(this, o);
             if (op.equals("i+")) {
                 emit(JVM.IADD);
                 frame.pop();
             } else if (op.equals("i-")) {
                 emit(JVM.ISUB);
                 frame.pop();
             } else if (op.equals("i*")) {
                 emit(JVM.IMUL);
                 frame.pop();
             } else if (op.equals("i/")) {
                 emit(JVM.IDIV);
                 frame.pop();
             } else if (op.equals("f+")) {
                 emit(JVM.FADD);
                 frame.pop();
             } else if (op.equals("f-")) {
                 emit(JVM.FSUB);
                 frame.pop();
             } else if (op.equals("f*")) {
                 emit(JVM.FMUL);
                 frame.pop();
             } else if (op.equals("f/")) {
                 emit(JVM.FDIV);
                 frame.pop();
             } else if (op.equals("i>") || op.equals("i<") || op.equals("i>=") ||
                     op.equals("i<=") || op.equals("i==") || op.equals("i!=")) {
                 emitIF_ICMPCOND(op, frame);
             } else if (op.equals("f>") || op.equals("f<") || op.equals("f>=") ||
                     op.equals("f<=") || op.equals("f==") || op.equals("f!=")) {
                 emitFCMP(op, frame);
             }
         }
         return null;
     }
 
     public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) {
         Frame frame = (Frame) o;
         VC.ASTs.List list = ast.IL;
         int index = 0;
         while (!list.isEmpty()) {
             ArrayExprList exprList = (ArrayExprList) list;
             emit(JVM.DUP);
             frame.push();
             emitICONST(index);
             frame.push();
             exprList.E.visit(this, o);
             if (exprList.E.type.isBooleanType())
                 emit(JVM.BASTORE);
             else if (exprList.E.type.isFloatType())
                 emit(JVM.FASTORE);
             else
                 emit(JVM.IASTORE);
             frame.pop(3);
             index++;
             list = exprList.EL;
         }
         return null;
     }
 
     public Object visitArrayExpr(ArrayExpr ast, Object o) {
         Frame frame = (Frame) o;
         ast.V.visit(this, o);
         ast.E.visit(this, o);
         if (ast.type.isFloatType())
             emit(JVM.FALOAD);
         else if (ast.type.isBooleanType())
             emit(JVM.BALOAD);
         else
             emit(JVM.IALOAD);
         frame.pop(2);
         frame.push();
         return null;
     }
 
     public Object visitVarExpr(VarExpr ast, Object o) {
         ast.V.visit(this, o);
         return null;
     }
 
     public Object visitAssignExpr(AssignExpr ast, Object o) {
         Frame frame = (Frame) o;
         if (ast.E1 instanceof ArrayExpr) {
             ArrayExpr arr = (ArrayExpr) ast.E1;
             arr.V.visit(this, o);
             arr.E.visit(this, o);
             ast.E2.visit(this, o);
             if (localvars.containsKey(((SimpleVar) arr.V).I.spelling + "[]")) {
                 if (((ArrayType) arr.V.type).T.isFloatType())
                     emit(JVM.FASTORE);
                 else if (((ArrayType) arr.V.type).T.isBooleanType())
                     emit(JVM.BASTORE);
                 else
                     emit(JVM.IASTORE);
             } else {
                 emitPUTSTATIC(VCtoJavaType(arr.V.type), ((SimpleVar) arr.V).I.spelling + "[]");
             }
             frame.pop(3);
             if (!(ast.parent instanceof ExprStmt || ast.parent instanceof ForStmt))
                 ast.E1.visit(this, o);
         } else if (ast.E1 instanceof VarExpr) {
             ast.E2.visit(this, o);
             SimpleVar var = (SimpleVar) ((VarExpr) ast.E1).V;
             if (!(ast.parent instanceof ExprStmt || ast.parent instanceof ForStmt)) {
                 emit(JVM.DUP);
                 frame.push();
             }
             if (localvars.containsKey(var.I.spelling)) {
                 int index = localvars.get(var.I.spelling);
                 if (ast.type.isFloatType()) {
                     if (index >= 0 && index <= 3)
                         emit(JVM.FSTORE + "_" + index);
                     else
                         emit(JVM.FSTORE, index);
                 } else {
                     if (index >= 0 && index <= 3)
                         emit(JVM.ISTORE + "_" + index);
                     else
                         emit(JVM.ISTORE, index);
                 }
             } else {
                 emitPUTSTATIC(VCtoJavaType(var.type), var.I.spelling);
             }
             frame.pop();
         }
         return null;
     }
 
     // Parameters
     public Object visitParaList(ParaList ast, Object o) {
         ast.P.visit(this, o);
         ast.PL.visit(this, o);
         return null;
     }
 
     public Object visitParaDecl(ParaDecl ast, Object o) {
         Frame frame = (Frame) o;
         ast.index = frame.getNewIndex();
         String T = VCtoJavaType(ast.T);
         if (ast.T.isArrayType())
             localvars.put(ast.I.spelling + "[]", ast.index);
         else
             localvars.put(ast.I.spelling, ast.index);
         emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T + " from " +
                 frame.scopeStart.peek() + " to " + frame.scopeEnd.peek());
         return null;
     }
 
     public Object visitEmptyParaList(EmptyParaList ast, Object o) {
         return null;
     }
 
     // Arguments
     public Object visitArgList(ArgList ast, Object o) {
         ast.A.visit(this, o);
         ast.AL.visit(this, o);
         return null;
     }
 
     public Object visitArg(Arg ast, Object o) {
         ast.E.visit(this, o);
         return null;
     }
 
     public Object visitEmptyArgList(EmptyArgList ast, Object o) {
         return null;
     }
 
     // Types
     public Object visitIntType(IntType ast, Object o) {
         return null;
     }
 
     public Object visitFloatType(FloatType ast, Object o) {
         return null;
     }
 
     public Object visitBooleanType(BooleanType ast, Object o) {
         return null;
     }
 
     public Object visitVoidType(VoidType ast, Object o) {
         return null;
     }
 
     public Object visitErrorType(ErrorType ast, Object o) {
         return null;
     }
 
     public Object visitStringType(StringType ast, Object o) {
         return null;
     }
 
     public Object visitArrayType(ArrayType ast, Object o) {
         // Emit code to allocate a new array.
         Frame frame = (Frame) o;
         int size = Integer.parseInt(((IntExpr) ast.E).IL.spelling);
         emitICONST(size);
         frame.push();
         emit(JVM.NEWARRAY, ast.T.toString());
         frame.pop();
         frame.push();
         return null;
     }
 
     // Literals, Identifiers and Operators
     public Object visitIdent(Ident ast, Object o) {
         return null;
     }
 
     public Object visitIntLiteral(IntLiteral ast, Object o) {
         Frame frame = (Frame) o;
         emitICONST(Integer.parseInt(ast.spelling));
         frame.push();
         return null;
     }
 
     public Object visitFloatLiteral(FloatLiteral ast, Object o) {
         Frame frame = (Frame) o;
         emitFCONST(Float.parseFloat(ast.spelling));
         frame.push();
         return null;
     }
 
     public Object visitBooleanLiteral(BooleanLiteral ast, Object o) {
         Frame frame = (Frame) o;
         emitBCONST(ast.spelling.equals("true"));
         frame.push();
         return null;
     }
 
     public Object visitStringLiteral(StringLiteral ast, Object o) {
         Frame frame = (Frame) o;
         emit(JVM.LDC, "\"" + ast.spelling + "\"");
         frame.push();
         return null;
     }
 
     public Object visitOperator(Operator ast, Object o) {
         return null;
     }
 
     public Object visitSimpleVar(SimpleVar ast, Object o) {
         Frame frame = (Frame) o;
         if (ast.type.isArrayType()) {
             if (localvars.containsKey(ast.I.spelling + "[]")) {
                 int index = localvars.get(ast.I.spelling + "[]");
                 emitALOAD(index);
             } else {
                 emitGETSTATIC(VCtoJavaType(ast.type), ast.I.spelling);
             }
         } else if (ast.type.isFloatType()) {
             if (localvars.containsKey(ast.I.spelling)) {
                 int index = localvars.get(ast.I.spelling);
                 emitFLOAD(index);
             } else {
                 emitGETSTATIC(VCtoJavaType(ast.type), ast.I.spelling);
             }
         } else {
             if (localvars.containsKey(ast.I.spelling)) {
                 int index = localvars.get(ast.I.spelling);
                 emitILOAD(index);
             } else {
                 emitGETSTATIC(VCtoJavaType(ast.type), ast.I.spelling);
             }
         }
         frame.push();
         return null;
     }
 
     // Auxiliary methods for byte code generation
     private void emit(String s) {
         JVM.append(new Instruction(s));
     }
 
     private void emit(String s1, String s2) {
         emit(s1 + " " + s2);
     }
 
     private void emit(String s1, int i) {
         emit(s1 + " " + i);
     }
 
     private void emit(String s1, float f) {
         emit(s1 + " " + f);
     }
 
     private void emit(String s1, String s2, int i) {
         emit(s1 + " " + s2 + " " + i);
     }
 
     private void emit(String s1, String s2, String s3) {
         emit(s1 + " " + s2 + " " + s3);
     }
 
     private void emitIF_ICMPCOND(String op, Frame frame) {
         String opcode;
         if (op.equals("i!="))
             opcode = JVM.IF_ICMPNE;
         else if (op.equals("i=="))
             opcode = JVM.IF_ICMPEQ;
         else if (op.equals("i<"))
             opcode = JVM.IF_ICMPLT;
         else if (op.equals("i<="))
             opcode = JVM.IF_ICMPLE;
         else if (op.equals("i>"))
             opcode = JVM.IF_ICMPGT;
         else
             opcode = JVM.IF_ICMPGE;
         String falseLabel = frame.getNewLabel();
         String nextLabel = frame.getNewLabel();
         emit(opcode, falseLabel);
         frame.pop(2);
         emit("iconst_0");
         emit("goto", nextLabel);
         emit(falseLabel + ":");
         emit(JVM.ICONST_1);
         frame.push();
         emit(nextLabel + ":");
     }
 
     private void emitFCMP(String op, Frame frame) {
         String opcode;
         if (op.equals("f!="))
             opcode = JVM.IFNE;
         else if (op.equals("f=="))
             opcode = JVM.IFEQ;
         else if (op.equals("f<"))
             opcode = JVM.IFLT;
         else if (op.equals("f<="))
             opcode = JVM.IFLE;
         else if (op.equals("f>"))
             opcode = JVM.IFGT;
         else
             opcode = JVM.IFGE;
         String falseLabel = frame.getNewLabel();
         String nextLabel = frame.getNewLabel();
         emit(JVM.FCMPG);
         frame.pop(2);
         emit(opcode, falseLabel);
         emit(JVM.ICONST_0);
         emit("goto", nextLabel);
         emit(falseLabel + ":");
         emit(JVM.ICONST_1);
         frame.push();
         emit(nextLabel + ":");
     }
 
     private void emitILOAD(int index) {
         if (index >= 0 && index <= 3)
             emit(JVM.ILOAD + "_" + index);
         else
             emit(JVM.ILOAD, index);
     }
 
     private void emitFLOAD(int index) {
         if (index >= 0 && index <= 3)
             emit(JVM.FLOAD + "_" + index);
         else
             emit(JVM.FLOAD, index);
     }
 
     private void emitALOAD(int index) {
         if (index >= 0 && index <= 3)
             emit(JVM.ALOAD + "_" + index);
         else
             emit(JVM.ALOAD, index);
     }
 
     private void emitGETSTATIC(String T, String I) {
         emit(JVM.GETSTATIC, classname + "/" + I, T);
     }
 
     private void emitISTORE(Ident ast) {
         int index;
         if (ast.decl instanceof ParaDecl)
             index = ((ParaDecl) ast.decl).index;
         else
             index = ((LocalVarDecl) ast.decl).index;
         if (index >= 0 && index <= 3)
             emit(JVM.ISTORE + "_" + index);
         else
             emit(JVM.ISTORE, index);
     }
 
     private void emitFSTORE(Ident ast) {
         int index;
         if (ast.decl instanceof ParaDecl)
             index = ((ParaDecl) ast.decl).index;
         else
             index = ((LocalVarDecl) ast.decl).index;
         if (index >= 0 && index <= 3)
             emit(JVM.FSTORE + "_" + index);
         else
             emit(JVM.FSTORE, index);
     }
 
     private void emitPUTSTATIC(String T, String I) {
         emit(JVM.PUTSTATIC, classname + "/" + I, T);
     }
 
     private void emitICONST(int value) {
         if (value == -1)
             emit(JVM.ICONST_M1);
         else if (value >= 0 && value <= 5)
             emit(JVM.ICONST + "_" + value);
         else if (value >= -128 && value <= 127)
             emit(JVM.BIPUSH, value);
         else if (value >= -32768 && value <= 32767)
             emit(JVM.SIPUSH, value);
         else
             emit(JVM.LDC, value);
     }
 
     private void emitFCONST(float value) {
         if (value == 0.0)
             emit(JVM.FCONST_0);
         else if (value == 1.0)
             emit(JVM.FCONST_1);
         else if (value == 2.0)
             emit(JVM.FCONST_2);
         else
             emit(JVM.LDC, value);
     }
 
     private void emitBCONST(boolean value) {
         if (value)
             emit(JVM.ICONST_1);
         else
             emit(JVM.ICONST_0);
     }
 
     private String VCtoJavaType(Type t) {
         if (t.equals(StdEnvironment.booleanType))
             return "Z";
         else if (t.equals(StdEnvironment.intType))
             return "I";
         else if (t.equals(StdEnvironment.floatType))
             return "F";
         else if (t.isArrayType()) {
             ArrayType arr_type = (ArrayType) t;
             if (arr_type.T.isIntType())
                 return "[I";
             else if (arr_type.T.isFloatType())
                 return "[F";
             else if (arr_type.T.isBooleanType())
                 return "[Z";
             else
                 return null;
         } else
             return "V";
     }
 }
 