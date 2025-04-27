/*
 * Recogniser.java            
 *
 * Wed 26 Feb 2025 14:06:17 AEDT
 */

/* This recogniser accepts a subset of VC defined by the following CFG: 

	program       -> func-decl
	
	// declaration
	
	func-decl     -> void identifier "(" ")" compound-stmt
	
	identifier    -> ID
	
	// statements 
	compound-stmt -> "{" stmt* "}" 
	stmt          -> continue-stmt
	    	      |  expr-stmt
	continue-stmt -> continue ";"
	expr-stmt     -> expr? ";"
	
	// expressions 
	expr                -> assignment-expr
	assignment-expr     -> additive-expr
	additive-expr       -> multiplicative-expr
	                    |  additive-expr "+" multiplicative-expr
	multiplicative-expr -> unary-expr
		            |  multiplicative-expr "*" unary-expr
	unary-expr          -> "-" unary-expr
			    |  primary-expr
	
	primary-expr        -> identifier
	 		    |  INTLITERAL
			    | "(" expr ")"
 
It serves as a good starting point for implementing your own VC recogniser. 
You can modify the existing parsing methods (if necessary) and add any missing ones 
to build a complete recogniser for VC.

Alternatively, you are free to disregard the starter code entirely and develop 
your own solution, as long as it adheres to the same public interface.

*/

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {

    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;

    public Recogniser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;
        currentToken = scanner.getToken();
    }

    // match checks that the current token is what is expected, then fetches the next token.
    void match(int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {
            currentToken = scanner.getToken();
        } else {
            syntacticError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }

    // accept simply fetches the next token.
    void accept() {
        currentToken = scanner.getToken();
    }
    
    // For operators we may simply advance.
    void acceptOperator() throws SyntaxError {
        currentToken = scanner.getToken();
    }

    // Reports a syntactic error (error message must include "ERROR") and throws a SyntaxError exception.
    void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
        SourcePosition pos = currentToken.position;
        errorReporter.reportError(messageTemplate, tokenQuoted, pos);
        throw new SyntaxError();
    }

    // ======================= PROGRAM ============================
    // program -> ( func-decl | var-decl )*
    public void parseProgram() {
        try {
            while (currentToken.kind != Token.EOF) {
                parseDecl();
            }
            if (currentToken.kind != Token.EOF) {
                syntacticError("Extra tokens after program end", currentToken.spelling);
            }
        } catch (SyntaxError s) { }
    }

    // ======================= DECLARATIONS ============================
    // A declaration can be either a function declaration or a variable declaration.
    // func-decl -> type identifier para-list compound-stmt
    // var-decl  -> type init-declarator-list ";"
    void parseDecl() throws SyntaxError {
        parseType();
        if (currentToken.kind != Token.ID) {
            syntacticError("identifier expected after type", "");
        }
        // Consume the identifier.
        parseIdent();
        if (currentToken.kind == Token.LPAREN) {
            // Function declaration.
            parseParaList();
            parseCompoundStmt();
        } else {
            // Variable declaration.
            parseInitDeclaratorRest();
            while (currentToken.kind == Token.COMMA) {
                match(Token.COMMA);
                parseInitDeclarator();
            }
            match(Token.SEMICOLON);
        }
    }
    
    // parseType -> void | boolean | int | float
    void parseType() throws SyntaxError {
        if (currentToken.kind == Token.VOID ||
            currentToken.kind == Token.BOOLEAN ||
            currentToken.kind == Token.INT ||
            currentToken.kind == Token.FLOAT) {
            accept();
        } else {
            // If an identifier is encountered here, it likely indicates a missing result type for a function.
            if (currentToken.kind == Token.ID) {
                syntacticError("\"" + currentToken.spelling + "\" wrong result type for a function", "");
            } else {
                syntacticError("type expected", currentToken.spelling);
            }
        }
    }
    
    // For variable declarations:
    // init-declarator-list -> init-declarator ( "," init-declarator )*
    // init-declarator -> declarator ( "=" initialiser )?
    // Here, since we already consumed the identifier for the first declarator, we complete it.
    void parseInitDeclaratorRest() throws SyntaxError {
        // Optionally an array declarator.
        if (currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            if (currentToken.kind == Token.INTLITERAL) {
                parseIntLiteral();
            }
            match(Token.RBRACKET);
        }
        // Optional initializer.
        if (currentToken.kind == Token.EQ) {
            match(Token.EQ);
            parseInitialiser();
        }
    }
    
    // For subsequent init-declarators.
    void parseInitDeclarator() throws SyntaxError {
        parseIdent();
        parseInitDeclaratorRest();
    }
    
    // initialiser -> expr | "{" expr ( "," expr )* "}"
    void parseInitialiser() throws SyntaxError {
        if (currentToken.kind == Token.LCURLY) {
            match(Token.LCURLY);
            parseExpr();
            while (currentToken.kind == Token.COMMA) {
                match(Token.COMMA);
                parseExpr();
            }
            match(Token.RCURLY);
        } else {
            parseExpr();
        }
    }
    
    // ======================= PARAMETERS ============================
    // para-list -> "(" proper-para-list? ")"
    // proper-para-list -> para-decl ( "," para-decl )*
    // para-decl -> type declarator
    void parseParaList() throws SyntaxError {
        match(Token.LPAREN);
        if (isTypeToken(currentToken.kind)) {
            parseParaDecl();
            while (currentToken.kind == Token.COMMA) {
                match(Token.COMMA);
                parseParaDecl();
            }
        }
        match(Token.RPAREN);
    }
    
    void parseParaDecl() throws SyntaxError {
        parseType();
        parseDeclarator();
    }
    
    // declarator -> identifier | identifier "[" INTLITERAL? "]"
    void parseDeclarator() throws SyntaxError {
        parseIdent();
        if (currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            if (currentToken.kind == Token.INTLITERAL) {
                parseIntLiteral();
            }
            match(Token.RBRACKET);
        }
    }
    
    // Helper: returns true if the token represents a type.
    boolean isTypeToken(int kind) {
        return (kind == Token.VOID || kind == Token.BOOLEAN ||
                kind == Token.INT || kind == Token.FLOAT);
    }
    
    // ======================= STATEMENTS ============================
    // stmt -> compound-stmt | if-stmt | for-stmt | while-stmt | break-stmt | continue-stmt | return-stmt | expr-stmt
    void parseStmt() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.LCURLY:
                parseCompoundStmt();
                break;
            case Token.IF:
                parseIfStmt();
                break;
            case Token.FOR:
                parseForStmt();
                break;
            case Token.WHILE:
                parseWhileStmt();
                break;
            case Token.BREAK:
                parseBreakStmt();
                break;
            case Token.CONTINUE:
                parseContinueStmt();
                break;
            case Token.RETURN:
                parseReturnStmt();
                break;
            default:
                parseExprStmt();
                break;
        }
    }
    
    // compound-stmt -> "{" var-decl* stmt* "}"
    void parseCompoundStmt() throws SyntaxError {
        match(Token.LCURLY);
        // Local variable declarations (if any) must come first.
        while (isTypeToken(currentToken.kind)) {
            parseVarDecl();
        }
        while (currentToken.kind != Token.RCURLY) {
            parseStmt();
        }
        match(Token.RCURLY);
    }
    
    // Variable declaration inside a compound statement.
    void parseVarDecl() throws SyntaxError {
        parseType();
        if (currentToken.kind != Token.ID) {
            syntacticError("identifier expected here", "");
        }
        parseIdent();
        parseInitDeclaratorRest();
        while (currentToken.kind == Token.COMMA) {
            match(Token.COMMA);
            parseInitDeclarator();
        }
        match(Token.SEMICOLON);
    }
    
    // if-stmt -> if "(" expr ")" stmt ( else stmt )?
    void parseIfStmt() throws SyntaxError {
        match(Token.IF);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
        if (currentToken.kind == Token.ELSE) {
            match(Token.ELSE);
            parseStmt();
        }
    }
    
    // for-stmt -> for "(" expr? ";" expr? ";" expr? ")" stmt
    void parseForStmt() throws SyntaxError {
        match(Token.FOR);
        match(Token.LPAREN);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
        if (currentToken.kind != Token.RPAREN) {
            parseExpr();
        }
        match(Token.RPAREN);
        parseStmt();
    }
    
    // while-stmt -> while "(" expr ")" stmt
    void parseWhileStmt() throws SyntaxError {
        match(Token.WHILE);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
    }
    
    // break-stmt -> break ";"
    void parseBreakStmt() throws SyntaxError {
        match(Token.BREAK);
        match(Token.SEMICOLON);
    }
    
    // continue-stmt -> continue ";"
    void parseContinueStmt() throws SyntaxError {
        match(Token.CONTINUE);
        match(Token.SEMICOLON);
    }
    
    // return-stmt -> return expr? ";"
    void parseReturnStmt() throws SyntaxError {
        match(Token.RETURN);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
    }
    
    // expr-stmt -> expr? ";"
    void parseExprStmt() throws SyntaxError {
        if (currentToken.kind == Token.ID  ||
            currentToken.kind == Token.INTLITERAL ||
            currentToken.kind == Token.FLOATLITERAL ||
            currentToken.kind == Token.BOOLEANLITERAL ||
            currentToken.kind == Token.STRINGLITERAL ||
            currentToken.kind == Token.MINUS ||
            currentToken.kind == Token.PLUS ||
            currentToken.kind == Token.LPAREN ||
            currentToken.kind == Token.NOT) {
            parseExpr();
            match(Token.SEMICOLON);
        } else {
            match(Token.SEMICOLON);
        }
    }
    
    // ======================= EXPRESSIONS ============================
    // expr -> assignment-expr
    void parseExpr() throws SyntaxError {
        parseAssignmentExpr();
    }
    
    // assignment-expr -> cond-or-expr ( "=" cond-or-expr )*
    void parseAssignmentExpr() throws SyntaxError {
        parseCondOrExpr();
        while (currentToken.kind == Token.EQ) {
            match(Token.EQ);
            parseCondOrExpr();
        }
    }
    
    // cond-or-expr -> cond-and-expr ( "||" cond-and-expr )*
    void parseCondOrExpr() throws SyntaxError {
        parseCondAndExpr();
        while (currentToken.kind == Token.OROR) {
            match(Token.OROR);
            parseCondAndExpr();
        }
    }
    
    // cond-and-expr -> equality-expr ( "&&" equality-expr )*
    void parseCondAndExpr() throws SyntaxError {
        parseEqualityExpr();
        while (currentToken.kind == Token.ANDAND) {
            match(Token.ANDAND);
            parseEqualityExpr();
        }
    }
    
    // equality-expr -> rel-expr ( ( "==" | "!=" ) rel-expr )*
    void parseEqualityExpr() throws SyntaxError {
        parseRelExpr();
        while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
            match(currentToken.kind);
            parseRelExpr();
        }
    }
    
    // rel-expr -> additive-expr ( ( "<" | "<=" | ">" | ">=" ) additive-expr )*
    void parseRelExpr() throws SyntaxError {
        parseAdditiveExpr();
        while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ ||
               currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
            match(currentToken.kind);
            parseAdditiveExpr();
        }
    }
    
    // additive-expr -> multiplicative-expr ( ("+" | "-") multiplicative-expr )*
    void parseAdditiveExpr() throws SyntaxError {
        parseMultiplicativeExpr();
        while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
            acceptOperator();
            parseMultiplicativeExpr();
        }
    }
    
    // multiplicative-expr -> unary-expr ( ("*" | "/") unary-expr )*
    void parseMultiplicativeExpr() throws SyntaxError {
        parseUnaryExpr();
        while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
            acceptOperator();
            parseUnaryExpr();
        }
    }
    
    // unary-expr -> ("+" | "-" | "!") unary-expr | primary-expr
    void parseUnaryExpr() throws SyntaxError {
        if (currentToken.kind == Token.PLUS || 
            currentToken.kind == Token.MINUS ||
            currentToken.kind == Token.NOT) {
            acceptOperator();
            parseUnaryExpr();
        } else {
            parsePrimaryExpr();
        }
    }
    
    // primary-expr -> identifier ( arg-list | "[" expr "]" )? | "(" expr ")" | literal
    void parsePrimaryExpr() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.ID:
                parseIdent();
                if (currentToken.kind == Token.LPAREN) {
                    parseArgList();
                } else if (currentToken.kind == Token.LBRACKET) {
                    match(Token.LBRACKET);
                    parseExpr();
                    match(Token.RBRACKET);
                }
                break;
            case Token.LPAREN:
                match(Token.LPAREN);
                parseExpr();
                match(Token.RPAREN);
                break;
            case Token.INTLITERAL:
                parseIntLiteral();
                break;
            case Token.FLOATLITERAL:
                parseFloatLiteral();
                break;
            case Token.BOOLEANLITERAL:
                parseBooleanLiteral();
                break;
            case Token.STRINGLITERAL:
                accept(); // simply accept the string literal
                break;
            default:
                syntacticError("illegal primary expression", currentToken.spelling);
        }
    }
    
    // arg-list -> "(" proper-arg-list? ")"
    void parseArgList() throws SyntaxError {
        match(Token.LPAREN);
        if (currentToken.kind != Token.RPAREN) {
            parseExpr();
            while (currentToken.kind == Token.COMMA) {
                match(Token.COMMA);
                parseExpr();
            }
        }
        match(Token.RPAREN);
    }
    
    // ======================= IDENTIFIERS AND LITERALS ============================
    // parseIdent: In the future, an Identifier node can be built here.
    void parseIdent() throws SyntaxError {
        if (currentToken.kind == Token.ID) {
            accept();
        } else {
            syntacticError("identifier expected", "");
        }
    }
    
    void parseIntLiteral() throws SyntaxError {
        if (currentToken.kind == Token.INTLITERAL) {
            accept();
        } else {
            syntacticError("integer literal expected", "");
        }
    }
    
    void parseFloatLiteral() throws SyntaxError {
        if (currentToken.kind == Token.FLOATLITERAL) {
            accept();
        } else {
            syntacticError("float literal expected", "");
        }
    }
    
    void parseBooleanLiteral() throws SyntaxError {
        if (currentToken.kind == Token.BOOLEANLITERAL) {
            accept();
        } else {
            syntacticError("boolean literal expected", "");
        }
    }
}
