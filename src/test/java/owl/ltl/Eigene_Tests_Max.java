package owl.ltl;

import org.junit.jupiter.api.Test;
import owl.ltl.ltlf.LTLfToLTLVisitor;
import owl.ltl.ltlf.Translator;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.ltlf.LtlfParser;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class Eigene_Tests_Max {
  private static final List<String> Literals = List.of("a","b","c","d","t");
  private static final List<Formula> LTLfFORMULAS = List.of(
    LtlfParser.syntax("false",Literals),
    LtlfParser.syntax("true",Literals),
    LtlfParser.syntax("a",Literals),

    LtlfParser.syntax("! a",Literals),
    LtlfParser.syntax("a & b",Literals),
    LtlfParser.syntax("a | b",Literals),
    LtlfParser.syntax("a -> b",Literals),
    LtlfParser.syntax("a <-> b",Literals),
    LtlfParser.syntax("a xor b",Literals),

    LtlfParser.syntax("F a",Literals),
    LtlfParser.syntax("G a",Literals),
    LtlfParser.syntax("X a",Literals),

    LtlfParser.syntax("a M b",Literals),
    LtlfParser.syntax("a R b",Literals),
    LtlfParser.syntax("a U b",Literals),
    LtlfParser.syntax("a W b",Literals),

    LtlfParser.syntax("(a <-> b) xor (c <-> d)",Literals),

    LtlfParser.syntax("F ((a R b) & c)",Literals),
    LtlfParser.syntax("F ((a W b) & c)",Literals),
    LtlfParser.syntax("G ((a M b) | c)",Literals),
    LtlfParser.syntax("G ((a U b) | c)",Literals),
    LtlfParser.syntax("G (X (a <-> b))",Literals),
    LtlfParser.syntax("G (X (a xor b))",Literals));

  private static final List<Formula> LTLFORMULAS = List.of(
    LtlParser.syntax("false",Literals),
    LtlParser.syntax("true",Literals),
    LtlParser.syntax("a",Literals),

    LtlParser.syntax("! a",Literals),
    LtlParser.syntax("a & b",Literals),
    LtlParser.syntax("a | b",Literals),
    LtlParser.syntax("a -> b",Literals),
    LtlParser.syntax("a <-> b",Literals),
    LtlParser.syntax("a xor b",Literals),

    LtlParser.syntax("F (t & a)",Literals),
    LtlParser.syntax("G (!t | a)",Literals),
    LtlParser.syntax("X (t & a)",Literals),

    LtlParser.syntax("(t & a) M b",Literals),
    LtlParser.syntax("a R (!t | b)",Literals),
    LtlParser.syntax("a U (t & b)",Literals),
    LtlParser.syntax("(!t | a) W b",Literals),

    LtlParser.syntax("(a <-> b) xor (c <-> d)",Literals),

    LtlParser.syntax("F (t & (a R (!t | b)) & c)",Literals),
    LtlParser.syntax("F (t & ((!t | a) W b) & c)",Literals),
    LtlParser.syntax("G (!t | (t & a) M b | c)",Literals),
    LtlParser.syntax("G (!t |(a U (t & b)) | c)",Literals),
    LtlParser.syntax("G (!t | X (t & (a <-> b)))",Literals),
    LtlParser.syntax("G (!t | X (t & (a xor b)))",Literals));

  @Test
  void test_LTLfParser(){

    Formula F = LtlfParser.syntax("!X (!b) "); // == Xweak b
    Formula G = LtlfParser.syntax("!tt & (X c) ");
    Formula H = LtlfParser.syntax("G(p1 U !p1)");
    Formula J = LtlParser.syntax("" );


    System.out.println(F);
    System.out.println(G);
    System.out.println(H);
    System.out.println(J);
  }
  @Test
  void test_LTLfToLTLVisitor(){
    LTLfToLTLVisitor Translator = new LTLfToLTLVisitor();
    for (int i = 0;i < LTLfFORMULAS.size();i++){
      Formula should = LTLFORMULAS.get(i);
      Formula is = Translator.apply(LTLfFORMULAS.get(i),Literal.of(4));
      assertEquals(should,(is));
    }

  }
  @Test
  void Radler(){

    Formula F = LtlfParser.syntax("(GF p0) & (GF !p0)");
    Formula G = LtlfParser.syntax("(G(p0 -> X (F p1)))");
    List<Literal> input=  List.of(Literal.of(0));
    List<Literal> output=  List.of(Literal.of(1));
    Literal Tail =  Literal.of(G.atomicPropositions(true).length());
    Formula F_ = Translator.translate(F,Tail);
    Formula G_ = Translator.translate(G,Tail);

    System.out.println(Translator.convToTLSF(input,output,Tail,G_));
    try {
      write(Translator.convToTLSF(input,output,Tail,G_),"/home/max/Dokumente/Bachelorarbeit/TLSFzwischenablage/test.tlsf");
    } catch (IOException e) {
      e.printStackTrace();
    }


  }
  public static void write(String content, String path) throws IOException
  {
    BufferedWriter writer = new BufferedWriter(new FileWriter(path));
    writer.write(content);
    writer.close();
  }
  @Test
  void test_toString(){
    Formula F1 = Conjunction.of(GOperator.of(Literal.of(0)),Disjunction.of(XOperator.of(Literal.of(1)),XOperator.of(Literal.of(1,true))));
    Formula F = LtlParser.syntax("G a & (X b) ");
    System.out.println(F1.not());
    System.out.println(F1.not().not());
    System.out.println(F1.toString());

    System.out.println(F.not());


    }
  @Test
  void test_t(){

    Formula F = Conjunction.of(Literal.of(0),ROperator.of(Literal.of(1),Literal.of(2)));
    System.out.println(F.atomicPropositions(true).toString());
    Literal Tail = Literal.of(F.atomicPropositions(true).length());
    System.out.println(F.toString());
    System.out.println((t(F,Tail).toString().replace(Tail.toString(),"Tail")));

  }
@Test
  void test_visitor(){
    Formula F = Conjunction.of(Literal.of(0),ROperator.of(Literal.of(1),Literal.of(2)));
    PrintVisitor PV = new PrintVisitor(true,null);
    System.out.println(F.accept(PV));

  }



  Formula t(Formula in,Literal Tail){
    if (in instanceof BooleanConstant){
      return in;
    }
    if (in instanceof Literal){
      return in;
    }
    if (in instanceof Conjunction){
      Conjunction C =(Conjunction) in;
      Set<Formula> A = new HashSet<>() ;
      C.children.forEach(c -> A.add(t(c,Tail)));
      return Conjunction.of(A);
    }
    if (in instanceof Disjunction){
      Disjunction D =(Disjunction) in;
      Set<Formula> A = new HashSet<>() ;
      D.children.forEach(c -> A.add(t(c,Tail)));
      return Disjunction.of(A);
    }
    if(in instanceof Biconditional){
      Biconditional BiCon = (Biconditional) in;
      return Biconditional.of(t(BiCon.left,Tail),t(BiCon.right,Tail));
    }
    if (in instanceof XOperator){
      XOperator X = (XOperator) in;
      return XOperator.of(Conjunction.of(Tail,t(X.operand,Tail)));
    }
    if (in instanceof GOperator){
      GOperator G = (GOperator) in;
      return GOperator.of(t(Disjunction.of(G.operand,Tail),Tail.not()));
    }
    if (in instanceof FOperator){
      FOperator F = (FOperator) in;
      return FOperator.of(t(Conjunction.of(F.operand,Tail),Tail));
    }
    if (in instanceof UOperator){
      UOperator U = (UOperator) in;
      return UOperator.of(U.left,Conjunction.of(Tail,t((U.right),Tail)));
    }
    if (in instanceof ROperator){
      ROperator R = (ROperator) in;
      return ROperator.of((R.left),(Disjunction.of(Tail.not(),t(R.right,Tail))));
    }

    if (in instanceof WOperator){
      WOperator W = (WOperator) in;
      return ROperator.of(W.right,Disjunction.of(Tail.not(),t(W.left,Tail),t(W.right,Tail)));
    }
    if (in instanceof MOperator){
      MOperator M = (MOperator) in;
      return UOperator.of(M.right,Conjunction.of(Tail,t(M.left,Tail),t(M.right,Tail)));
    }

    return null;
  }
}
