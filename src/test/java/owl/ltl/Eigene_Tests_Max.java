package owl.ltl;

import org.junit.jupiter.api.Test;
import owl.grammar.LTLParser;
import owl.ltl.ltlf.LTLfToLTLVisitor;
import owl.ltl.ltlf.Translator;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.ltlf.LtlfParser;


import java.io.*;
import java.text.Normalizer;
import java.util.*;

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
    Formula J = LtlParser.syntax(("(!p0 & !p1 & p2 & !p3 & !p4 & G(!p2 | !p5) & G(!p0 | Fp2) & G(!p1 | Fp5) & G(!p0 | p2 | Xp0) & G(!p1 | p5 | Xp1) & G(!p2 | p4 | X!p5) & G(!p4 | !p5 | X!p2) & G(p3 <-> X!p3) & G(p6 <-> (p0 | p1)) & G(!p2 | X(p2 | p5)) & G(!p5 | X(p2 | p5)) & G(!p2 | X!p2 | (p4 <-> Xp4)) & G(!p5 | X!p5 | (p4 <-> Xp4)) & G(p3 | ((p0 <-> Xp0) & (p1 <-> Xp1))) & G(!p3 | ((p2 <-> Xp2) & (p5 <-> Xp5))) & G(!p2 | p6 | ((p2) U (((p6) R ((!p4 & Fp2)))))) & G(!p5 | p6 | ((p5) U (((p6) R ((!p4 & Fp2)))))))"));

    PrintVisitor P = new PrintVisitor(false,null);
    System.out.println(P.apply(F));
    System.out.println(P.apply(G));
    System.out.println(P.apply(H));
    System.out.println(P.apply(J));
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
  void translate_SyftBenchmarkstoStrixInputs() throws Exception {
    for (int i = 1;i< 102;i++){
      File LTLffile = new File("/home/max/Dokumente/Bachelorarbeit/Syft_Benchmarks/basic/" + i + ".ltlf" );
      File Partfile = new File("/home/max/Dokumente/Bachelorarbeit/Syft_Benchmarks/basic/" + i + ".part" );
      BufferedReader brLTLf = new BufferedReader(new FileReader(LTLffile));
      BufferedReader brPart = new BufferedReader(new FileReader(Partfile));
      String LTLfformula = brLTLf.readLine();
      String inputline = brPart.readLine();
      String outputline = brPart.readLine();
      inputline =inputline.substring(9);
      outputline =outputline.substring(10);
      List<String> Literals= new LinkedList<>();
      Literals.addAll(Arrays.asList(inputline.split(" ")));
      Literals.addAll(Arrays.asList(outputline.split(" ")));

      Formula F = LtlfParser.syntax(LTLfformula,Literals);

      Literal Tail =  Literal.of(F.atomicPropositions(true).length());
      Formula F_ = Translator.translate(F,Tail);

      Literals.add("tail");
      PrintVisitor P = new PrintVisitor(false,Literals);
      String output = "-f '";
      output += P.apply(F_)+"' --ins=\"";
      inputline =inputline.replace(" ",",");
      output += inputline + "\" --outs=\"";
      outputline =outputline.replace(" ",",");
      output += outputline +",tail\"";
      write(output,"/home/max/Dokumente/Bachelorarbeit/Tests/vsSyft/strixInputs/"+i +".strix");

    }
  }
  @Test
  void TestAutomataArk(){
    List<String> Literals = List.of("a","b","c","tail");
    Formula F = LtlfParser.syntax("(((a) && ( G(a -> ((X(!a)) && (X(X(a))))))) && ((!b) && (X(!b))) && (G ((a && (!b)) -> ((!c) && (X(X(b)))))) && (G ((a && b) -> (c && (X(X(!b)))))) && (G ( ((!c) && X(!a)) -> ( X(!c) && (X(b) -> (X(X(X(b))))) && (X(!b) -> (X(X(X(!b)))) ) ) )) && (G ( c -> ( ( X(!b) -> ( X(!c) && (X(X(X(b)))) ) ) && ( X(b) -> ( X(c) && (X(X(X(!b)))) ) ) ))))",List.of("a","b","c"));
    Literal Tail =  Literal.of(F.atomicPropositions(true).length());
    Formula F_ = Translator.translate(F,Tail);
    List<String> input=  List.of("b");
    List<String> output=  List.of("a","c");
    PrintVisitor P = new PrintVisitor(false,Literals);
    System.out.println(P.apply(F_));
    try {
      write(Translator.convToTLSF(input,output,Tail,F_,Literals),"/home/max/Dokumente/Bachelorarbeit/TLSFzwischenablage/test.tlsf");
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
  @Test
  void Radler(){

    Formula F = LtlfParser.syntax("(GF p0) & (GF !p0)");
    List<String> LiteralsG = List.of("rqt0","rqt1","rqt2","grt0","grt1","grt2");
    Formula G = LtlfParser.syntax("(( true )->( (G (false  | (!(X(grt0))) | (rqt0)))  & (G (false  | (!(X(grt1))) | (rqt1)))  & (G (false  | (!(X(grt2))) | (rqt2)))  & (G (true  & (false  | (!(X(grt0))) | (!(X(grt1)))) & (false  | (!(X(grt0))) | (!(X(grt2)))) & (false  | (!(X(grt1))) | (!(X(grt0)))) & (false  | (!(X(grt1))) | (!(X(grt2)))) & (false  | (!(X(grt2))) | (!(X(grt0)))) & (false  | (!(X(grt2))) | (!(X(grt1))))))  & true ))",LiteralsG);
    PrintVisitor P = new PrintVisitor(false,LiteralsG);
    System.out.println(P.apply(G));
    List<String> input=  List.of();
    List<String> output=  List.of();
    Literal Tail =  Literal.of(G.atomicPropositions(true).length());
    Formula F_ = Translator.translate(F,Tail);
    Formula G_ = Translator.translate(G,Tail);

    System.out.println(Translator.convToTLSF(input,output,Tail,G_,LiteralsG));
    try {
      write(Translator.convToTLSF(input,output,Tail,G_,LiteralsG),"/home/max/Dokumente/Bachelorarbeit/TLSFzwischenablage/test.tlsf");
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
