/*
 * Parser.java 
 *
 * Thu 06 Mar 2025 12:58:29 AEDT
 *
 * PLEASE COMPARE Recogniser.java PROVIDED IN ASSIGNMENT 2 AND Parser.java
 * PROVIDED BELOW TO UNDERSTAND HOW THE FORMER IS MODIFIED TO OBTAIN THE LATTER.
 *
 * This parser for a subset of the VC language is intended to 
 *  demonstrate how to create the AST nodes, including (among others): 
 *  (1) a list (of statements)
 *  (2) a function
 *  (3) a statement (which is an expression statement), 
 *  (4) a unary expression
 *  (5) a binary expression
 *  (6) terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start 
 * and finish to determine the position information for the start and 
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *
 * Note that what is provided below is an implementation for a subset of VC
 * given below rather than VC itself. It provides a good starting point for you
 * to implement a parser for VC yourself, by modifying the parsing methods
 * provided (whenever necessary).
 *
 *
 * Alternatively, you are free to disregard the starter code entirely and 
 * develop your own solution, as long as it adheres to the same public 
 * interface.


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}" 
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions 
expr                -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
                    |  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
	            |  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

 package VC.Parser;

 import VC.Scanner.Scanner;
 import VC.Scanner.SourcePosition;
 import VC.Scanner.Token;
 import VC.ErrorReporter;
 import VC.ASTs.*;
 
 public class Parser {
 
   private Scanner scanner;
   private ErrorReporter errorReporter;
   private Token currentToken;
   private SourcePosition previousTokenPosition;
   // dummy position for empty nodes
   private SourcePosition dummyPos = new SourcePosition();
 
   public Parser(Scanner lexer, ErrorReporter reporter) {
     scanner = lexer;
     errorReporter = reporter;
     previousTokenPosition = new SourcePosition();
     currentToken = scanner.getToken();
   }
 
   // --- Helper to check whether a token can start a type ---
   private boolean isTypeToken(int kind) {
     return (kind == Token.INT || kind == Token.FLOAT ||
         kind == Token.BOOLEAN || kind == Token.VOID);
   }
 
   // ----------------------- Utility methods -----------------------
 
   void match(int tokenExpected) throws SyntaxError {
     if (currentToken.kind == tokenExpected) {
       previousTokenPosition = currentToken.position;
       currentToken = scanner.getToken();
     } else {
       syntacticError("\"%\" expected here", Token.spell(tokenExpected));
     }
   }
 
   void accept() {
     previousTokenPosition = currentToken.position;
     currentToken = scanner.getToken();
   }
 
   Operator acceptOperator() throws SyntaxError {
     Operator op = new Operator(currentToken.spelling, currentToken.position);
     currentToken = scanner.getToken();
     return op;
   }
 
   void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
     SourcePosition pos = currentToken.position;
     errorReporter.reportError(messageTemplate, tokenQuoted, pos);
     throw new SyntaxError();
   }
 
   void start(SourcePosition position) {
     position.lineStart = currentToken.position.lineStart;
     position.charStart = currentToken.position.charStart;
   }
 
   void finish(SourcePosition position) {
     position.lineFinish = previousTokenPosition.lineFinish;
     position.charFinish = previousTokenPosition.charFinish;
   }
 
   void copyStart(SourcePosition from, SourcePosition to) {
     to.lineStart = from.lineStart;
     to.charStart = from.charStart;
   }
 
   // ----------------------- Top-Level Parsing -----------------------
 
   public Program parseProgram() {
     Program programAST = null;
     SourcePosition programPos = new SourcePosition();
     start(programPos);
     try {
       VC.ASTs.List dlAST = parseDeclList();
       finish(programPos);
       programAST = new Program(dlAST, programPos);
       if (currentToken.kind != Token.EOF)
         syntacticError("\"%\" unknown token", currentToken.spelling);
     } catch (SyntaxError s) {
       return null;
     }
     return programAST;
   }
 
   // ----------------------- Global Declarations -----------------------
 
   // Returns a list (DeclList / EmptyDeclList) of global declarations.
   VC.ASTs.List parseDeclList() throws SyntaxError {
     if (!isTypeToken(currentToken.kind))
       return new EmptyDeclList(dummyPos);
     else {
       VC.ASTs.List firstDecl = parseDecl();
       VC.ASTs.List restDecl = parseDeclList();
       return appendDeclLists(firstDecl, restDecl);
     }
   }
 
   // A global declaration is either a function declaration or a variable
   // declaration.
   // Returns a list (with one or more Decl nodes).
   VC.ASTs.List parseDecl() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Type tAST = parseType();
     Ident idAST = parseIdent();
     if (currentToken.kind == Token.LPAREN) { // function declaration
       VC.ASTs.List paraList = parseParaList();
       Stmt bodyAST = parseCompoundStmt();
       finish(pos);
       Decl fDecl = new FuncDecl(tAST, idAST, paraList, bodyAST, pos);
       return new DeclList(fDecl, new EmptyDeclList(dummyPos), pos);
     } else { // variable declaration
       VC.ASTs.List varList = parseVarDeclList(tAST, idAST, pos);
       match(Token.SEMICOLON);
       finish(pos);
       return varList;
     }
   }
 
   // Splits a comma-separated global variable declaration into separate Decl
   // nodes
   VC.ASTs.List parseVarDeclList(Type tAST, Ident idAST, SourcePosition pos) throws SyntaxError {
     GlobalVarDecl firstDecl = parseGlobalVarDeclRest(tAST, idAST, pos);
     VC.ASTs.List tail = parseVarDeclListHelper(tAST, pos);
     return new DeclList(firstDecl, tail, pos);
   }
 
   // Recursive helper for global declarations
   VC.ASTs.List parseVarDeclListHelper(Type tAST, SourcePosition pos) throws SyntaxError {
     if (currentToken.kind == Token.COMMA) {
       match(Token.COMMA);
       Ident nextId = parseIdent();
       GlobalVarDecl decl = parseGlobalVarDeclRest(tAST, nextId, pos);
       VC.ASTs.List tail = parseVarDeclListHelper(tAST, pos);
       return new DeclList(decl, tail, pos);
     } else {
       return new EmptyDeclList(dummyPos);
     }
   }
 
   GlobalVarDecl parseGlobalVarDeclRest(Type tAST, Ident idAST, SourcePosition pos) throws SyntaxError {
     if (currentToken.kind == Token.LBRACKET) {
       match(Token.LBRACKET);
       Expr dimension = new EmptyExpr(dummyPos);
       if (currentToken.kind == Token.INTLITERAL) {
         IntLiteral ilAST = parseIntLiteral();
         SourcePosition ipos = new SourcePosition();
         copyStart(ilAST.position, ipos);
         finish(ipos);
         dimension = new IntExpr(ilAST, ipos);
       }
       match(Token.RBRACKET);
       tAST = new ArrayType(tAST, dimension, pos);
     }
     Expr initExpr = new EmptyExpr(dummyPos);
     if (currentToken.kind == Token.EQ) {
       match(Token.EQ);
       initExpr = parseExpr();
     }
     return new GlobalVarDecl(tAST, idAST, initExpr, pos);
   }
 
   // ----------------------- Parameters -----------------------
 
   VC.ASTs.List parseParaList() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.LPAREN);
     VC.ASTs.List paraList;
     if (isTypeToken(currentToken.kind))
       paraList = parseParaDeclTail();
     else
       paraList = new EmptyParaList(pos);
     match(Token.RPAREN);
     finish(pos);
     return paraList;
   }
 
   VC.ASTs.List parseParaDeclTail() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     ParaDecl pd = parseParaDecl();
     if (currentToken.kind == Token.COMMA) {
       match(Token.COMMA);
       VC.ASTs.List tail = parseParaDeclTail();
       finish(pos);
       return new ParaList(pd, tail, pos);
     } else {
       finish(pos);
       return new ParaList(pd, new EmptyParaList(dummyPos), pos);
     }
   }
 
   // Allows an optional INTLITERAL for array parames
   ParaDecl parseParaDecl() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Type tAST = parseType();
     Ident idAST = parseIdent();
     if (currentToken.kind == Token.LBRACKET) {
       match(Token.LBRACKET);
       Expr dimension = new EmptyExpr(dummyPos);
       if (currentToken.kind == Token.INTLITERAL) {
         IntLiteral ilAST = parseIntLiteral();
         SourcePosition ipos = new SourcePosition();
         copyStart(ilAST.position, ipos);
         finish(ipos);
         dimension = new IntExpr(ilAST, ipos);
       }
       match(Token.RBRACKET);
       tAST = new ArrayType(tAST, dimension, pos);
     }
     finish(pos);
     return new ParaDecl(tAST, idAST, pos);
   }
 
   // ----------------------- Local Declarations -----------------------
 
   VC.ASTs.List parseLocalDeclList() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     VC.ASTs.List dlAST = new EmptyDeclList(dummyPos);
     while (isTypeToken(currentToken.kind)) {
       VC.ASTs.List oneDecl = parseLocalVarDeclStmt();
       dlAST = appendDeclLists(dlAST, oneDecl);
     }
     return dlAST;
   }
 
   // Append two local declaration lists.
   VC.ASTs.List appendDeclLists(VC.ASTs.List l1, VC.ASTs.List l2) {
     if (l1 instanceof EmptyDeclList)
       return l2;
     else if (l1 instanceof DeclList) {
       DeclList dl = (DeclList) l1;
       return new DeclList(dl.D, appendDeclLists(dl.DL, l2), dl.position);
     }
     return l1;
   }
 
   // Splits a comma-separated local variable declaration statement.
   VC.ASTs.List parseLocalVarDeclStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Type tAST = parseType();
     Ident idAST = parseIdent();
     LocalVarDecl firstDecl = parseLocalVarDeclRest(tAST, idAST, pos);
     VC.ASTs.List tail = parseLocalVarDeclListHelper(tAST, pos);
     finish(pos);
     match(Token.SEMICOLON);
     return new DeclList(firstDecl, tail, pos);
   }
 
   VC.ASTs.List parseLocalVarDeclListHelper(Type tAST, SourcePosition pos) throws SyntaxError {
     if (currentToken.kind == Token.COMMA) {
       match(Token.COMMA);
       Ident nextId = parseIdent();
       LocalVarDecl decl = parseLocalVarDeclRest(tAST, nextId, pos);
       VC.ASTs.List tail = parseLocalVarDeclListHelper(tAST, pos);
       return new DeclList(decl, tail, pos);
     } else {
       return new EmptyDeclList(dummyPos);
     }
   }
 
   LocalVarDecl parseLocalVarDeclRest(Type tAST, Ident idAST, SourcePosition pos) throws SyntaxError {
     if (currentToken.kind == Token.LBRACKET) {
       match(Token.LBRACKET);
       Expr dimension = new EmptyExpr(dummyPos);
       if (currentToken.kind == Token.INTLITERAL) {
         IntLiteral ilAST = parseIntLiteral();
         SourcePosition ipos = new SourcePosition();
         copyStart(ilAST.position, ipos);
         finish(ipos);
         dimension = new IntExpr(ilAST, ipos);
       }
       match(Token.RBRACKET);
       tAST = new ArrayType(tAST, dimension, pos);
     }
     Expr initExpr = new EmptyExpr(dummyPos);
     if (currentToken.kind == Token.EQ) {
       match(Token.EQ);
       initExpr = parseExpr();
     }
     return new LocalVarDecl(tAST, idAST, initExpr, pos);
   }
 
   // ----------------------- Statements -----------------------
 
   Stmt parseCompoundStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.LCURLY);
     // Always parse local declarations (the routine returns an empty list if none)
     VC.ASTs.List dlAST = parseLocalDeclList();
     VC.ASTs.List slAST = parseStmtList();
     match(Token.RCURLY);
     finish(pos);
     return new CompoundStmt(dlAST, slAST, pos);
   }
 
   VC.ASTs.List parseStmtList() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     if (currentToken.kind != Token.RCURLY) {
       Stmt sAST = parseStmt();
       VC.ASTs.List tail = (currentToken.kind != Token.RCURLY) ? parseStmtList() : new EmptyStmtList(dummyPos);
       finish(pos);
       return new StmtList(sAST, tail, pos);
     } else {
       return new EmptyStmtList(dummyPos);
     }
   }
 
   Stmt parseStmt() throws SyntaxError {
     Stmt sAST = null;
     switch (currentToken.kind) {
       case Token.LCURLY:
         sAST = parseCompoundStmt();
         break;
       case Token.IF:
         sAST = parseIfStmt();
         break;
       case Token.FOR:
         sAST = parseForStmt();
         break;
       case Token.WHILE:
         sAST = parseWhileStmt();
         break;
       case Token.BREAK:
         sAST = parseBreakStmt();
         break;
       case Token.CONTINUE:
         sAST = parseContinueStmt();
         break;
       case Token.RETURN:
         sAST = parseReturnStmt();
         break;
       default:
         sAST = parseExprStmt();
         break;
     }
     return sAST;
   }
 
   Stmt parseIfStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.IF);
     match(Token.LPAREN);
     Expr eAST = parseExpr();
     match(Token.RPAREN);
     Stmt s1AST = parseStmt();
     Stmt s2AST = new EmptyStmt(dummyPos);
     if (currentToken.kind == Token.ELSE) {
       match(Token.ELSE);
       s2AST = parseStmt();
     }
     finish(pos);
     return new IfStmt(eAST, s1AST, s2AST, pos);
   }
 
   Stmt parseForStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.FOR);
     match(Token.LPAREN);
     Expr e1AST = (currentToken.kind != Token.SEMICOLON) ? parseExpr() : new EmptyExpr(dummyPos);
     match(Token.SEMICOLON);
     Expr e2AST = (currentToken.kind != Token.SEMICOLON) ? parseExpr() : new EmptyExpr(dummyPos);
     match(Token.SEMICOLON);
     Expr e3AST = (currentToken.kind != Token.RPAREN) ? parseExpr() : new EmptyExpr(dummyPos);
     match(Token.RPAREN);
     Stmt sAST = parseStmt();
     finish(pos);
     return new ForStmt(e1AST, e2AST, e3AST, sAST, pos);
   }
 
   Stmt parseWhileStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.WHILE);
     match(Token.LPAREN);
     Expr eAST = parseExpr();
     match(Token.RPAREN);
     Stmt sAST = parseStmt();
     finish(pos);
     return new WhileStmt(eAST, sAST, pos);
   }
 
   Stmt parseBreakStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.BREAK);
     match(Token.SEMICOLON);
     finish(pos);
     return new BreakStmt(pos);
   }
 
   Stmt parseContinueStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.CONTINUE);
     match(Token.SEMICOLON);
     finish(pos);
     return new ContinueStmt(pos);
   }
 
   Stmt parseReturnStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.RETURN);
     Expr eAST = new EmptyExpr(dummyPos);
     if (currentToken.kind != Token.SEMICOLON)
       eAST = parseExpr();
     match(Token.SEMICOLON);
     finish(pos);
     return new ReturnStmt(eAST, pos);
   }
 
   Stmt parseExprStmt() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Expr eAST = new EmptyExpr(dummyPos);
     if (currentToken.kind != Token.SEMICOLON)
       eAST = parseExpr();
     match(Token.SEMICOLON);
     finish(pos);
     return new ExprStmt(eAST, pos);
   }
 
   // ----------------------- Expressions -----------------------
 
   Expr parseExpr() throws SyntaxError {
     return parseAssignmentExpr();
   }
 
   Expr parseAssignmentExpr() throws SyntaxError {
     Expr left = parseLogicalOrExpr();
     if (currentToken.kind == Token.EQ) {
       Operator op = acceptOperator();
       Expr right = parseAssignmentExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(left.position, pos);
       finish(pos);
       return new AssignExpr(left, right, pos);
     } else {
       return left;
     }
   }
 
   Expr parseLogicalOrExpr() throws SyntaxError {
     Expr left = parseLogicalAndExpr();
     while (currentToken.kind == Token.OROR) {
       Operator op = acceptOperator();
       Expr right = parseLogicalAndExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(left.position, pos);
       finish(pos);
       left = new BinaryExpr(left, op, right, pos);
     }
     return left;
   }
 
   Expr parseLogicalAndExpr() throws SyntaxError {
     Expr left = parseEqualityExpr();
     while (currentToken.kind == Token.ANDAND) {
       Operator op = acceptOperator();
       Expr right = parseEqualityExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(left.position, pos);
       finish(pos);
       left = new BinaryExpr(left, op, right, pos);
     }
     return left;
   }
 
   Expr parseEqualityExpr() throws SyntaxError {
     Expr left = parseRelationalExpr();
     while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
       Operator op = acceptOperator();
       Expr right = parseRelationalExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(left.position, pos);
       finish(pos);
       left = new BinaryExpr(left, op, right, pos);
     }
     return left;
   }
 
   Expr parseRelationalExpr() throws SyntaxError {
     Expr left = parseAdditiveExpr();
     while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ ||
         currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
       Operator op = acceptOperator();
       Expr right = parseAdditiveExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(left.position, pos);
       finish(pos);
       left = new BinaryExpr(left, op, right, pos);
     }
     return left;
   }
 
   Expr parseAdditiveExpr() throws SyntaxError {
     SourcePosition addStartPos = new SourcePosition();
     start(addStartPos);
     Expr exprAST = parseMultiplicativeExpr();
     while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
       Operator opAST = acceptOperator();
       Expr e2AST = parseMultiplicativeExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(addStartPos, pos);
       finish(pos);
       exprAST = new BinaryExpr(exprAST, opAST, e2AST, pos);
     }
     return exprAST;
   }
 
   Expr parseMultiplicativeExpr() throws SyntaxError {
     SourcePosition multStartPos = new SourcePosition();
     start(multStartPos);
     Expr exprAST = parseUnaryExpr();
     while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
       Operator opAST = acceptOperator();
       Expr e2AST = parseUnaryExpr();
       SourcePosition pos = new SourcePosition();
       copyStart(multStartPos, pos);
       finish(pos);
       exprAST = new BinaryExpr(exprAST, opAST, e2AST, pos);
     }
     return exprAST;
   }
 
   Expr parseUnaryExpr() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     if (currentToken.kind == Token.MINUS || currentToken.kind == Token.PLUS || currentToken.kind == Token.NOT) {
       Operator op = acceptOperator();
       Expr eAST = parseUnaryExpr();
       finish(pos);
       return new UnaryExpr(op, eAST, pos);
     } else {
       return parsePrimaryExpr();
     }
   }
 
   Expr parsePrimaryExpr() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Expr exprAST = null;
     switch (currentToken.kind) {
       case Token.ID:
         Ident iAST = parseIdent();
         if (currentToken.kind == Token.LPAREN) {
           VC.ASTs.List argList = parseArgList();
           finish(pos);
           exprAST = new CallExpr(iAST, argList, pos);
         } else if (currentToken.kind == Token.LBRACKET) {
           match(Token.LBRACKET);
           Expr indexAST = parseExpr();
           match(Token.RBRACKET);
           finish(pos);
           exprAST = new ArrayExpr(new SimpleVar(iAST, pos), indexAST, pos);
         } else {
           finish(pos);
           exprAST = new VarExpr(new SimpleVar(iAST, pos), pos);
         }
         break;
       case Token.INTLITERAL:
         IntLiteral ilAST = parseIntLiteral();
         finish(pos);
         exprAST = new IntExpr(ilAST, pos);
         break;
       case Token.FLOATLITERAL:
         FloatLiteral flAST = parseFloatLiteral();
         finish(pos);
         exprAST = new FloatExpr(flAST, pos);
         break;
       case Token.BOOLEANLITERAL:
         BooleanLiteral blAST = parseBooleanLiteral();
         finish(pos);
         exprAST = new BooleanExpr(blAST, pos);
         break;
       case Token.STRINGLITERAL:
         StringLiteral slAST = parseStringLiteral();
         finish(pos);
         exprAST = new StringExpr(slAST, pos);
         break;
       case Token.LCURLY:
         // Array initializer: "{" expr ("," expr)* "}"
         match(Token.LCURLY);
         VC.ASTs.List exprList = parseArrayInitExprList();
         match(Token.RCURLY);
         finish(pos);
         exprAST = new ArrayInitExpr(exprList, pos);
         break;
       case Token.LPAREN:
         match(Token.LPAREN);
         exprAST = parseExpr();
         match(Token.RPAREN);
         break;
       default:
         syntacticError("illegal primary expression", currentToken.spelling);
     }
     return exprAST;
   }
 
   VC.ASTs.List parseArrayInitExprList() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Expr first = parseExpr();
     if (currentToken.kind == Token.COMMA) {
       match(Token.COMMA);
       VC.ASTs.List tail = parseArrayInitExprList();
       finish(pos);
       return new ArrayExprList(first, tail, pos);
     } else {
       finish(pos);
       return new ArrayExprList(first, new EmptyArrayExprList(dummyPos), pos);
     }
   }
 
   VC.ASTs.List parseArgList() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.LPAREN);
     VC.ASTs.List argList;
     if (currentToken.kind != Token.RPAREN) {
       Expr argExpr = parseExpr();
       Arg argAST = new Arg(argExpr, pos);
       if (currentToken.kind == Token.COMMA) {
         VC.ASTs.List tail = parseArgListRest();
         argList = new ArgList(argAST, tail, pos);
       } else {
         argList = new ArgList(argAST, new EmptyArgList(dummyPos), pos);
       }
     } else {
       argList = new EmptyArgList(pos);
     }
     match(Token.RPAREN);
     return argList;
   }
 
   VC.ASTs.List parseArgListRest() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     match(Token.COMMA);
     Expr argExpr = parseExpr();
     Arg argAST = new Arg(argExpr, pos);
     VC.ASTs.List tail;
     if (currentToken.kind == Token.COMMA)
       tail = parseArgListRest();
     else
       tail = new EmptyArgList(dummyPos);
     finish(pos);
     return new ArgList(argAST, tail, pos);
   }
 
   // ----------------------- Identifiers and Literals -----------------------
 
   Ident parseIdent() throws SyntaxError {
     if (currentToken.kind == Token.ID) {
       previousTokenPosition = currentToken.position;
       String spelling = currentToken.spelling;
       Ident I = new Ident(spelling, previousTokenPosition);
       currentToken = scanner.getToken();
       return I;
     } else {
       syntacticError("identifier expected", "");
       return null;
     }
   }
 
   Type parseType() throws SyntaxError {
     SourcePosition pos = new SourcePosition();
     start(pos);
     Type tAST = null;
     switch (currentToken.kind) {
       case Token.INT:
         match(Token.INT);
         finish(pos);
         tAST = new IntType(pos);
         break;
       case Token.FLOAT:
         match(Token.FLOAT);
         finish(pos);
         tAST = new FloatType(pos);
         break;
       case Token.BOOLEAN:
         match(Token.BOOLEAN);
         finish(pos);
         tAST = new BooleanType(pos);
         break;
       case Token.VOID:
         match(Token.VOID);
         finish(pos);
         tAST = new VoidType(pos);
         break;
       default:
         syntacticError("type expected", currentToken.spelling);
     }
     return tAST;
   }
 
   IntLiteral parseIntLiteral() throws SyntaxError {
     if (currentToken.kind == Token.INTLITERAL) {
       String spelling = currentToken.spelling;
       previousTokenPosition = currentToken.position;
       accept();
       return new IntLiteral(spelling, previousTokenPosition);
     } else {
       syntacticError("integer literal expected", "");
       return null;
     }
   }
 
   FloatLiteral parseFloatLiteral() throws SyntaxError {
     if (currentToken.kind == Token.FLOATLITERAL) {
       String spelling = currentToken.spelling;
       previousTokenPosition = currentToken.position;
       accept();
       return new FloatLiteral(spelling, previousTokenPosition);
     } else {
       syntacticError("float literal expected", "");
       return null;
     }
   }
 
   BooleanLiteral parseBooleanLiteral() throws SyntaxError {
     if (currentToken.kind == Token.BOOLEANLITERAL) {
       String spelling = currentToken.spelling;
       previousTokenPosition = currentToken.position;
       accept();
       return new BooleanLiteral(spelling, previousTokenPosition);
     } else {
       syntacticError("boolean literal expected", "");
       return null;
     }
   }
 
   StringLiteral parseStringLiteral() throws SyntaxError {
     if (currentToken.kind == Token.STRINGLITERAL) {
       String spelling = currentToken.spelling;
       previousTokenPosition = currentToken.position;
       accept();
       return new StringLiteral(spelling, previousTokenPosition);
     } else {
       syntacticError("string literal expected", "");
       return null;
     }
   }
 }
 