package owl.automaton.algorithms;

import org.junit.Assert;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.SimpleTranslations;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;

public class LanguageAnalysisTest {

  @Test
  public void contains() {
    LabelledFormula formula1 = LtlParser.parse("G F (a & b)");
    LabelledFormula formula2 = LtlParser.parse("G F a");
    LabelledFormula formula3 = LtlParser.parse("G F (X a & (a U X b))");

    Automaton<DegeneralizedBreakpointState, BuchiAcceptance> infOftAandB = SimpleTranslations
      .buildBuchi(formula1, DefaultEnvironment.annotated());
    Automaton<DegeneralizedBreakpointState, BuchiAcceptance> infOftA = SimpleTranslations
      .buildBuchi(formula2, DefaultEnvironment.annotated());
    Automaton<DegeneralizedBreakpointState, BuchiAcceptance> infOftComplex = SimpleTranslations
      .buildBuchi(formula3, DefaultEnvironment.annotated());

    Assert.assertTrue(LanguageAnalysis.contains(infOftAandB, infOftA));
    Assert.assertFalse(LanguageAnalysis.contains(infOftA, infOftAandB));

    Assert.assertTrue(LanguageAnalysis.contains(infOftComplex, infOftA));
    Assert.assertFalse(LanguageAnalysis.contains(infOftA, infOftComplex));

    Assert.assertTrue(LanguageAnalysis.contains(infOftAandB, infOftComplex));
    Assert.assertFalse(LanguageAnalysis.contains(infOftComplex, infOftAandB));
  }
}
