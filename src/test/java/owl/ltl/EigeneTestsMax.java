package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import owl.ltl.ltlf.LtlfParser;
import owl.ltl.ltlf.LtlfToLtlVisitor;
import owl.ltl.ltlf.Translator;
import owl.ltl.parser.LtlParser;

import owl.ltl.visitors.PrintVisitor;

public class EigeneTestsMax {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> LTLfFORMULAS = List.of(
    LtlfParser.syntax("false", Literals),
    LtlfParser.syntax("true", Literals),
    LtlfParser.syntax("a", Literals),

    LtlfParser.syntax("! a", Literals),
    LtlfParser.syntax("a & b", Literals),
    LtlfParser.syntax("a | b", Literals),
    LtlfParser.syntax("a -> b", Literals),
    LtlfParser.syntax("a <-> b", Literals),
    LtlfParser.syntax("a xor b", Literals),

    LtlfParser.syntax("F a", Literals),
    LtlfParser.syntax("G a", Literals),
    LtlfParser.syntax("X a", Literals),

    LtlfParser.syntax("a M b", Literals),
    LtlfParser.syntax("a R b", Literals),
    LtlfParser.syntax("a U b", Literals),
    LtlfParser.syntax("a W b", Literals),

    LtlfParser.syntax("(a <-> b) xor (c <-> d)", Literals),

    LtlfParser.syntax("F ((a R b) & c)", Literals),
    LtlfParser.syntax("F ((a W b) & c)", Literals),
    LtlfParser.syntax("G ((a M b) | c)", Literals),
    LtlfParser.syntax("G ((a U b) | c)", Literals),
    LtlfParser.syntax("G (X (a <-> b))", Literals),
    LtlfParser.syntax("G (X (a xor b))", Literals));

  private static final List<Formula> LTLFORMULAS = List.of(
    LtlParser.syntax("false", Literals),
    LtlParser.syntax("true", Literals),
    LtlParser.syntax("a", Literals),

    LtlParser.syntax("! a", Literals),
    LtlParser.syntax("a & b", Literals),
    LtlParser.syntax("a | b", Literals),
    LtlParser.syntax("a -> b", Literals),
    LtlParser.syntax("a <-> b", Literals),
    LtlParser.syntax("a xor b", Literals),

    LtlParser.syntax("F (t & a)", Literals),
    LtlParser.syntax("G (!t | a)", Literals),
    LtlParser.syntax("X (t & a)", Literals),

    LtlParser.syntax("(t & a) M b", Literals),
    LtlParser.syntax("a R (!t | b)", Literals),
    LtlParser.syntax("a U (t & b)", Literals),
    LtlParser.syntax("(!t | a) W b", Literals),

    LtlParser.syntax("(a <-> b) xor (c <-> d)", Literals),

    LtlParser.syntax("F (t & (a R (!t | b)) & c)", Literals),
    LtlParser.syntax("F (t & ((!t | a) W b) & c)", Literals),
    LtlParser.syntax("G (!t | (t & a) M b | c)", Literals),
    LtlParser.syntax("G (!t |(a U (t & b)) | c)", Literals),
    LtlParser.syntax("G (!t | X (t & (a <-> b)))", Literals),
    LtlParser.syntax("G (!t | X (t & (a xor b)))", Literals));

  @Test
  void testLtlfToLtlVisitor() {
    LtlfToLtlVisitor translator = new LtlfToLtlVisitor();
    for (int i = 0; i < LTLfFORMULAS.size(); i++) {
      Formula should = LTLFORMULAS.get(i);
      Formula is = translator.apply(LTLfFORMULAS.get(i), Literal.of(4));
      assertEquals(should, (is));
    }

  }

  @Test
  void translateSyftBenchmarkstoStrixInputs() throws IOException {
    for (int i = 1; i < 102; i++) {
      File ltlffile = new File("/home/max/Dokumente/Bachelorarbeit/Syft_Benchmarks/"
        + "basic/" + i + ".ltlf");
      File partfile = new File("/home/max/Dokumente/Bachelorarbeit/Syft_Benchmarks/"
        + "basic/" + i + ".part");
     BufferedReader brLtlf = new BufferedReader(new FileReader(ltlffile));
      BufferedReader brPart = new BufferedReader(new FileReader(partfile));
     String ltlfformula = brLtlf.readLine();
      String inputline = brPart.readLine();
      String outputline = brPart.readLine();
      inputline = inputline.substring(9);
      outputline = outputline.substring(10);
      List<String> literals = new LinkedList<>();
      literals.addAll(Arrays.asList(inputline.split(" ")));
      literals.addAll(Arrays.asList(outputline.split(" ")));

      Formula f = LtlfParser.syntax(ltlfformula, literals);

      Literal tail = Literal.of(f.atomicPropositions(true).length());
      Formula f1 = Translator.translate(f, tail);

      literals.add("tail");
      PrintVisitor p = new PrintVisitor(false, literals);
      StringBuilder output = new StringBuilder("-f '");
      output.append(p.apply(f1)).append("' --ins=\"");
      inputline = inputline.replace(" ", ",");
      output.append(inputline).append("\" --outs=\"");
      outputline = outputline.replace(" ", ",");
      output.append(outputline).append(",tail\"");
      write(output.toString(),
        "/home/max/Dokumente/Bachelorarbeit/Tests/vsSyft/strixInputs/" + i + ".strix");

    }
  }

  @Test
  void testAutomataArk() {
    List<String> literals = List.of("a", "b", "c", "tail");
    Formula f = LtlfParser.syntax("(((a) && ( G(a -> ((X(!a)) && (X(X(a))))))) &&"
      + " ((!b) && (X(!b))) && (G ((a && (!b)) -> ((!c) && (X(X(b)))))) && (G ((a && b) -> (c"
      + " && (X(X(!b)))))) && (G ( ((!c) && X(!a)) -> "
      + "( X(!c) && (X(b) -> (X(X(X(b))))) && (X(!b) -> "
      + "(X(X(X(!b)))) ) ) )) && (G ( c -> ( ( X(!b) "
      + "-> ( X(!c) && (X(X(X(b)))) ) ) && ( X(b) -> ( X(c) && ("
      + "X(X(X(!b)))) ) ) ))))", List.of("a", "b", "c"));
    Literal tail = Literal.of(f.atomicPropositions(true).length());
    Formula f1 = Translator.translate(f, tail);
    List<String> input = List.of("b");
    List<String> output = List.of("a", "c");

    try {
      write(Translator.convToTlsf(input, output, tail, f1, literals),
        "/home/max/Dokumente/Bachelorarbeit/TLSFzwischenablage/test.tlsf");
    } catch (IOException e) {
      e.getCause();
    }

  }

  @Test
  void radler() {

    Formula f = LtlfParser.syntax("(GF p0) & (GF !p0)");
    List<String> literalsG = List.of("rqt0", "rqt1", "rqt2", "grt0", "grt1", "grt2");

    List<String> input = List.of();
    List<String> output = List.of();
    Literal tail = Literal.of(f.atomicPropositions(true).length());
    Formula f1 = Translator.translate(f, tail);
    try {
      write(Translator.convToTlsf(input, output, tail, f1, literalsG),
        "/home/max/Dokumente/Bachelorarbeit/TLSFzwischenablage/test.tlsf");
    } catch (IOException e) {
      e.getCause();
    }


  }

  public static void write(String content, String path) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(path));
    writer.write(content);
    writer.close();
  }

}