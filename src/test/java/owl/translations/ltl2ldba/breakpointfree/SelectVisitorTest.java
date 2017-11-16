package owl.translations.ltl2ldba.breakpointfree;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.UnaryModalOperator;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.Collector;
import owl.translations.ltl2ldba.breakpointfree.FGObligationsJumpManager.FScopedSelectVisitor;
import owl.translations.ltl2ldba.breakpointfree.FGObligationsJumpManager.GScopedSelectVisitor;
import owl.translations.ltl2ldba.breakpointfree.FGObligationsJumpManager.ToplevelSelectVisitor;

@SuppressWarnings("unchecked")
public class SelectVisitorTest {

  private static List<Set<UnaryModalOperator>> getFScoped(Formula formula) {
    return formula.accept(FScopedSelectVisitor.INSTANCE);
  }

  private static List<Set<UnaryModalOperator>> getGScoped(Formula formula) {
    return formula.accept(GScopedSelectVisitor.INSTANCE);
  }

  private static List<Set<UnaryModalOperator>> getToplevel(Formula formula) {
    return formula.accept(ToplevelSelectVisitor.INSTANCE);
  }

  @Test
  public void testFScopedSelectorM() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a M b)").formula;

    Set<UnaryModalOperator> choice = Set.of(fOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testFScopedSelectorR() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a R b)").formula;
    GOperator gOperator = new GOperator(new Literal(1));

    Set<UnaryModalOperator> choice1 = Set.of(fOperator);
    Set<UnaryModalOperator> choice2 = Set.of(fOperator, gOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice1, choice2));
  }

  @Test
  public void testFScopedSelectorU() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a U b)").formula;

    Set<UnaryModalOperator> choice = Set.of(fOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testFScopedSelectorW() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a W b)").formula;
    GOperator gOperator = (GOperator) LtlParser.syntax("G a");

    Set<UnaryModalOperator> choice1 = Set.of(fOperator);
    Set<UnaryModalOperator> choice2 = Set.of(fOperator, gOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice1, choice2));
  }

  @Test
  public void testGScopedSelectorM() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a M b)").formula;
    FOperator fOperator = (FOperator) LtlParser.syntax("F a");

    Set<UnaryModalOperator> choice = Set.of(gOperator, fOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testGScopedSelectorR() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a R b)").formula;

    Set<UnaryModalOperator> choice = Set.of(gOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testGScopedSelectorU() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a U b)").formula;
    FOperator fOperator = new FOperator(new Literal(1));

    Set<UnaryModalOperator> choice = Set.of(gOperator, fOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testGScopedSelectorW() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a W b)").formula;

    Set<UnaryModalOperator> choice = Set.of(gOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testSelectorSkippedUpwardClosure() {
    Conjunction conjunction = (Conjunction) LtlParser.parse("G (a | F b) & G (b | F a)").formula;
    Disjunction disjunction = (Disjunction) LtlParser.parse("G (F b) | G (F a)").formula;

    Set<GOperator> gOperators = (Set<GOperator>) ((Set) conjunction.children);

    FOperator fOperatorA = new FOperator(new Literal(0));
    FOperator fOperatorB = new FOperator(new Literal(1));

    Set<? extends UnaryModalOperator> baseChoiceConj = gOperators;
    Set<UnaryModalOperator> choiceA = Set.of(fOperatorA);
    Set<UnaryModalOperator> choiceB = Set.of(fOperatorB);
    Set<UnaryModalOperator> choiceAandB = Set.of(fOperatorA, fOperatorB);

    assertThat(getToplevel(conjunction),
      containsInAnyOrder(baseChoiceConj, Sets.union(baseChoiceConj, choiceA),
        Sets.union(baseChoiceConj, choiceB), Sets.union(baseChoiceConj, choiceAandB)));

    assertThat(getToplevel(disjunction),
      containsInAnyOrder(
        Collections2.transform(disjunction.children, (Formula formula) -> Sets.union(
          Set.of(formula), Collector.collectFOperators(formula))).toArray()));
  }

  @Test
  public void testSelectorUpwardClosure() {
    GOperator gOperatorConj =
      (GOperator) LtlParser.parse("G (((a | F b) & (b | F a)) | c)").formula;
    GOperator gOperatorDisj = (GOperator) LtlParser.parse("G ((a & F b) | (b & F a))").formula;
    FOperator fOperatorA = new FOperator(new Literal(0));
    FOperator fOperatorB = new FOperator(new Literal(1));

    Set<UnaryModalOperator> baseChoiceConj = Set.of(gOperatorConj);
    Set<UnaryModalOperator> baseChoiceDisj = Set.of(gOperatorDisj);
    Set<UnaryModalOperator> choiceA = Set.of(fOperatorA);
    Set<UnaryModalOperator> choiceB = Set.of(fOperatorB);
    Set<UnaryModalOperator> choiceAandB = Set.of(fOperatorA, fOperatorB);

    assertThat(getToplevel(gOperatorConj),
      containsInAnyOrder(baseChoiceConj, Sets.union(baseChoiceConj, choiceA),
        Sets.union(baseChoiceConj, choiceB), Sets.union(baseChoiceConj, choiceAandB)));
    assertThat(getToplevel(gOperatorDisj),
      containsInAnyOrder(Sets.union(baseChoiceDisj, choiceA), Sets.union(baseChoiceDisj, choiceB),
        Sets.union(baseChoiceDisj, choiceAandB)));
  }
}