package owl.automaton.algorithms;

import java.util.EnumSet;
import org.junit.Assert;
import org.junit.Test;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

public class LanguageAnalysisTest {

  @Test
  public void contains() {
    LabelledFormula formula1 = LtlParser.parse("G F (a & b)");
    LabelledFormula formula2 = LtlParser.parse("G F a");
    LabelledFormula formula3 = LtlParser.parse("G F (X a & (a U X b))");

    var translation = new LTL2DAFunction(DefaultEnvironment.annotated(), false,
      EnumSet.allOf(LTL2DAFunction.Constructions.class));
    var infOftAandB
      = AutomatonUtil.cast(translation.apply(formula1), Object.class, BuchiAcceptance.class);
    var infOftA
      = AutomatonUtil.cast(translation.apply(formula2), Object.class, BuchiAcceptance.class);
    var infOftComplex
      = AutomatonUtil.cast(translation.apply(formula3), Object.class, BuchiAcceptance.class);

    Assert.assertTrue(LanguageAnalysis.contains(infOftAandB, infOftA));
    Assert.assertFalse(LanguageAnalysis.contains(infOftA, infOftAandB));

    Assert.assertTrue(LanguageAnalysis.contains(infOftComplex, infOftA));
    Assert.assertFalse(LanguageAnalysis.contains(infOftA, infOftComplex));

    Assert.assertTrue(LanguageAnalysis.contains(infOftAandB, infOftComplex));
    Assert.assertFalse(LanguageAnalysis.contains(infOftComplex, infOftAandB));
  }
}
