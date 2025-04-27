/*
 * Scanner.java                        
 *
 * Sun 09 Feb 2025 13:31:52 AEDT
 *
 * The starter code here is provided as a high-level guide for implementation.
 *
 * You may completely disregard the starter code and develop your own solution, 
 * provided that it maintains the same public interface.
 *
 */

 package VC.Scanner;

 import VC.ErrorReporter;
 
 public final class Scanner {
 
     private SourceFile sourceFile;
     private ErrorReporter errorReporter;
     private boolean debug;
 
     private StringBuilder currentSpelling;
     private char currentChar;
     private SourcePosition sourcePos;
 
     private int startLine, startCol;
     private int line, col;
 
     // =========================================================
 
     public Scanner(SourceFile source, ErrorReporter reporter) {
         sourceFile = source;
         errorReporter = reporter;
         debug = false;
         currentChar = sourceFile.getNextChar();
         line = 1;
         col = 1;
     }
 
     public void enableDebugging() {
         debug = true;
     }
 
     private void accept() {
         updatePosition(currentChar);
         currentChar = sourceFile.getNextChar();
     }
 
     private void addAccept() {
         currentSpelling.append(currentChar);
         updatePosition(currentChar);
         currentChar = sourceFile.getNextChar();
     }
 
     private void updatePosition(char c) {
         if (c == '\n') {
             line++;
             col = 1;
         } else if (c == '\t') {
             col = col + (8 - ((col - 1) % 8));
         } else {
             col++;
         }
     }
 
     private char inspectChar(int nthChar) {
         return sourceFile.inspectChar(nthChar);
     }
 
     private int nextToken() {
         switch (currentChar) {
             case '(':
                 addAccept();
                 return Token.LPAREN;
             case ')':
                 addAccept();
                 return Token.RPAREN;
             case '{':
                 addAccept();
                 return Token.LCURLY;
             case '}':
                 addAccept();
                 return Token.RCURLY;
             case '[':
                 addAccept();
                 return Token.LBRACKET;
             case ']':
                 addAccept();
                 return Token.RBRACKET;
             case ';':
                 addAccept();
                 return Token.SEMICOLON;
             case ',':
                 addAccept();
                 return Token.COMMA;
             case '+':
                 addAccept();
                 return Token.PLUS;
             case '-':
                 addAccept();
                 return Token.MINUS;
             case '/':
                 addAccept();
                 return Token.DIV;
             case '*':
                 addAccept();
                 return Token.MULT;
             case '!':
                 addAccept();
                 if (currentChar == '=') {
                     addAccept();
                     return Token.NOTEQ;
                 } else {
                     return Token.NOT;
                 }
             case '=':
                 addAccept();
                 if (currentChar == '=') {
                     addAccept();
                     return Token.EQEQ;
                 } else {
                     return Token.EQ;
                 }
             case '<':
                 addAccept();
                 if (currentChar == '=') {
                     addAccept();
                     return Token.LTEQ;
                 } else {
                     return Token.LT;
                 }
             case '>':
                 addAccept();
                 if (currentChar == '=') {
                     addAccept();
                     return Token.GTEQ;
                 } else {
                     return Token.GT;
                 }
             case '&':
                 addAccept();
                 if (currentChar == '&') {
                     addAccept();
                     return Token.ANDAND;
                 } else {
                     return Token.ERROR;
                 }
             case '|':
                 addAccept();
                 if (currentChar == '|') {
                     addAccept();
                     return Token.OROR;
                 } else {
                     return Token.ERROR;
                 }
             case '.':
                 if (Character.isDigit(inspectChar(1))) {
                     return scanFloatLiteral();
                 } else {
                     addAccept();
                     return Token.ERROR;
                 }
             case '"':
                 return scanStringLiteral();
             case SourceFile.eof:
                 currentSpelling.append(Token.spell(Token.EOF));
                 return Token.EOF;
             default:
                 if (Character.isDigit(currentChar)) {
                     return scanNumber();
                 } else if (Character.isLetter(currentChar) || currentChar == '_') {
                     return scanIdentifier();
                 } else {
                     addAccept();
                     return Token.ERROR;
                 }
         }
     }
 
     private int scanIdentifier() {
         while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
             addAccept();
         }
         // Check for boolean literals "true" and "false"
         String lex = currentSpelling.toString();
         if (lex.equals("true") || lex.equals("false")) {
             return Token.BOOLEANLITERAL;
         }
         return Token.ID;
     }
 
     private int scanNumber() {
         boolean isFloat = false;
         // Consume integer part
         while (Character.isDigit(currentChar)) {
             addAccept();
         }
         // Check for fractional part
         if (currentChar == '.') {
             isFloat = true;
             addAccept();
             while (Character.isDigit(currentChar)) {
                 addAccept();
             }
         }
         // Check for exponent part only if it is valid
         if (currentChar == 'e' || currentChar == 'E') {
             char la1 = inspectChar(1);
             if (la1 == '+' || la1 == '-') {
                 char la2 = inspectChar(2);
                 if (Character.isDigit(la2)) {
                     isFloat = true;
                     addAccept(); // consume 'e' or 'E'
                     addAccept(); // consume '+' or '-'
                     while (Character.isDigit(currentChar)) {
                         addAccept();
                     }
                 }
                 // Otherwise, do not consume 'e'
             } else if (Character.isDigit(la1)) {
                 isFloat = true;
                 addAccept(); // consume 'e' or 'E'
                 while (Character.isDigit(currentChar)) {
                     addAccept();
                 }
             }
         }
         return isFloat ? Token.FLOATLITERAL : Token.INTLITERAL;
     }
 
     private int scanFloatLiteral() {
         // Called when literal starts with '.'
         addAccept(); // consume '.'
         while (Character.isDigit(currentChar)) {
             addAccept();
         }
         if (currentChar == 'e' || currentChar == 'E') {
             char la1 = inspectChar(1);
             if (la1 == '+' || la1 == '-') {
                 char la2 = inspectChar(2);
                 if (Character.isDigit(la2)) {
                     addAccept(); // consume 'e' or 'E'
                     addAccept(); // consume sign
                     while (Character.isDigit(currentChar)) {
                         addAccept();
                     }
                 }
             } else if (Character.isDigit(la1)) {
                 addAccept(); // consume 'e' or 'E'
                 while (Character.isDigit(currentChar)) {
                     addAccept();
                 }
             }
         }
         return Token.FLOATLITERAL;
     }
 
     private int scanStringLiteral() {
         int tokenStartLine = line;
         int tokenStartCol = col;
         accept(); // consume opening quote
         boolean terminated = false;
         while (currentChar != SourceFile.eof && currentChar != '\n') {
             if (currentChar == '"') {
                 accept(); // consume closing quote
                 terminated = true;
                 break;
             } else if (currentChar == '\\') {
                 int escapeStartCol = col;
                 accept(); // consume backslash
                 switch (currentChar) {
                     case '"':
                         currentSpelling.append('"');
                         accept();
                         break;
                     case '\\':
                         currentSpelling.append('\\');
                         accept();
                         break;
                     case 'b':
                         currentSpelling.append('\b');
                         accept();
                         break;
                     case 'f':
                         currentSpelling.append('\f');
                         accept();
                         break;
                     case 'n':
                         currentSpelling.append('\n');
                         accept();
                         break;
                     case 'r':
                         currentSpelling.append('\r');
                         accept();
                         break;
                     case 't':
                         currentSpelling.append('\t');
                         accept();
                         break;
                     case '\'':
                         currentSpelling.append('\'');
                         accept();
                         break;
                     default:
                         char illegalChar = currentChar;
                         currentSpelling.append('\\').append(illegalChar);
                         accept();
                         String illegal = "\\" + illegalChar;
                         SourcePosition pos = new SourcePosition(tokenStartLine, line, tokenStartCol, escapeStartCol);
                         errorReporter.reportError("%: illegal escape character", illegal, pos);
                         break;
                 }
             } else {
                 currentSpelling.append(currentChar);
                 accept();
             }
         }
         if (!terminated) {
             String errorContent = currentSpelling.toString();
             SourcePosition pos = new SourcePosition(tokenStartLine, tokenStartLine, tokenStartCol, tokenStartCol);
             errorReporter.reportError("%: unterminated string", errorContent, pos);
         }
         return Token.STRINGLITERAL;
     }
 
     private void skipSpaceAndComments() {
         while (true) {
             if (currentChar == ' ' || currentChar == '\r' || currentChar == '\n' ||
                     currentChar == '\t' || currentChar == '\f') {
                 accept();
             } else if (currentChar == '/') {
                 char nextChar = inspectChar(1);
                 if (nextChar == '/') {
                     accept();
                     accept();
                     while (currentChar != '\n' && currentChar != SourceFile.eof) {
                         accept();
                     }
                 } else if (nextChar == '*') {
                     int commentStartLine = line;
                     int commentStartCol = col;
                     accept();
                     accept();
                     boolean terminated = false;
                     while (currentChar != SourceFile.eof) {
                         if (currentChar == '*') {
                             if (inspectChar(1) == '/') {
                                 accept();
                                 accept();
                                 terminated = true;
                                 break;
                             } else {
                                 accept();
                             }
                         } else {
                             accept();
                         }
                     }
                     if (!terminated) {
                         SourcePosition pos = new SourcePosition(commentStartLine, commentStartLine, commentStartCol,
                                 commentStartCol);
                         errorReporter.reportError("%: unterminated comment", "", pos);
                         break;
                     }
                 } else {
                     break;
                 }
             } else {
                 break;
             }
         }
     }
 
     public Token getToken() {
         Token token;
         int kind;
 
         skipSpaceAndComments();
         startLine = line;
         startCol = col;
         currentSpelling = new StringBuilder();
 
         kind = nextToken();
         if (kind == Token.EOF) {
             sourcePos = new SourcePosition(startLine, line, startCol, col);
         } else {
             sourcePos = new SourcePosition(startLine, line, startCol, col - 1);
         }
         token = new Token(kind, currentSpelling.toString(), sourcePos);
 
         if (debug) {
             System.out.println(token);
         }
         return token;
     }
 }
 