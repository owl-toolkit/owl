/* Generated By:JavaCC: Do not edit this line. ParserConstants.java */
package ltl.parser;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface ParserConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int LCPAR = 6;
  /** RegularExpression Id. */
  int RCPAR = 7;
  /** RegularExpression Id. */
  int SEMIC = 8;
  /** RegularExpression Id. */
  int INFO = 9;
  /** RegularExpression Id. */
  int TITLE = 10;
  /** RegularExpression Id. */
  int DESCRIPTION = 11;
  /** RegularExpression Id. */
  int SEMANTICS = 12;
  /** RegularExpression Id. */
  int TARGET = 13;
  /** RegularExpression Id. */
  int TAGS = 14;
  /** RegularExpression Id. */
  int MEALY = 15;
  /** RegularExpression Id. */
  int MOORE = 16;
  /** RegularExpression Id. */
  int MEALYSTRICT = 17;
  /** RegularExpression Id. */
  int MOORESTRICT = 18;
  /** RegularExpression Id. */
  int MAIN = 19;
  /** RegularExpression Id. */
  int INPUTS = 20;
  /** RegularExpression Id. */
  int OUTPUTS = 21;
  /** RegularExpression Id. */
  int INITIALLY = 22;
  /** RegularExpression Id. */
  int PRESET = 23;
  /** RegularExpression Id. */
  int REQUIRE = 24;
  /** RegularExpression Id. */
  int ASSERT = 25;
  /** RegularExpression Id. */
  int ASSUME = 26;
  /** RegularExpression Id. */
  int GUARANTEE = 27;
  /** RegularExpression Id. */
  int FOP = 28;
  /** RegularExpression Id. */
  int GOP = 29;
  /** RegularExpression Id. */
  int XOP = 30;
  /** RegularExpression Id. */
  int UOP = 31;
  /** RegularExpression Id. */
  int VOP = 32;
  /** RegularExpression Id. */
  int ROP = 33;
  /** RegularExpression Id. */
  int WOP = 34;
  /** RegularExpression Id. */
  int NEG = 35;
  /** RegularExpression Id. */
  int AND = 36;
  /** RegularExpression Id. */
  int OR = 37;
  /** RegularExpression Id. */
  int IMP = 38;
  /** RegularExpression Id. */
  int BIIMP = 39;
  /** RegularExpression Id. */
  int LPAR = 40;
  /** RegularExpression Id. */
  int RPAR = 41;
  /** RegularExpression Id. */
  int FREQG = 42;
  /** RegularExpression Id. */
  int GEQ = 43;
  /** RegularExpression Id. */
  int LEQ = 44;
  /** RegularExpression Id. */
  int GT = 45;
  /** RegularExpression Id. */
  int LT = 46;
  /** RegularExpression Id. */
  int SUP = 47;
  /** RegularExpression Id. */
  int INF = 48;
  /** RegularExpression Id. */
  int NUMBER = 49;
  /** RegularExpression Id. */
  int TRUE = 50;
  /** RegularExpression Id. */
  int FALSE = 51;
  /** RegularExpression Id. */
  int ID = 52;
  /** RegularExpression Id. */
  int QUOTE = 53;
  /** RegularExpression Id. */
  int ENDQUOTE = 55;
  /** RegularExpression Id. */
  int CHAR = 56;
  /** RegularExpression Id. */
  int CNTRL_ESC = 57;

  /** Lexical state. */
  int DEFAULT = 0;
  /** Lexical state. */
  int STRING_STATE = 1;
  /** Lexical state. */
  int ESC_STATE = 2;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\r\"",
    "\"\\t\"",
    "\"\\n\"",
    "<token of kind 5>",
    "\"{\"",
    "\"}\"",
    "\";\"",
    "\"INFO\"",
    "\"TITLE:\"",
    "\"DESCRIPTION:\"",
    "\"SEMANTICS:\"",
    "\"TARGET:\"",
    "\"TAGS: \"",
    "\"Mealy\"",
    "\"Moore\"",
    "\"Mealy,Strict\"",
    "\"Moore,Strict\"",
    "\"MAIN\"",
    "\"INPUTS\"",
    "\"OUTPUTS\"",
    "\"INITIALLY\"",
    "\"PRESET\"",
    "\"REQUIRE\"",
    "<ASSERT>",
    "<ASSUME>",
    "<GUARANTEE>",
    "\"F\"",
    "\"G\"",
    "\"X\"",
    "\"U\"",
    "\"V\"",
    "\"R\"",
    "\"W\"",
    "\"!\"",
    "<AND>",
    "<OR>",
    "\"->\"",
    "\"<->\"",
    "\"(\"",
    "\")\"",
    "\"G^\"",
    "\">=\"",
    "\"<=\"",
    "\">\"",
    "\"<\"",
    "\"sup\"",
    "\"inf\"",
    "<NUMBER>",
    "<TRUE>",
    "<FALSE>",
    "<ID>",
    "\"\\\"\"",
    "\"\\\\\"",
    "<ENDQUOTE>",
    "<CHAR>",
    "<CNTRL_ESC>",
  };

}
