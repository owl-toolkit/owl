package owl.translations.ltl2ldba.breakpointfree;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collections;
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
    FOperator fOperator = (FOperator) LtlParser.formula("F (a M b)");

    Set<UnaryModalOperator> choice = ImmutableSet.of(fOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testFScopedSelectorR() {
    FOperator fOperator = (FOperator) LtlParser.formula("F (a R b)");
    GOperator gOperator = new GOperator(new Literal(1));

    Set<UnaryModalOperator> choice1 = ImmutableSet.of(fOperator);
    Set<UnaryModalOperator> choice2 = ImmutableSet.of(fOperator, gOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice1, choice2));
  }

  @Test
  public void testFScopedSelectorU() {
    FOperator fOperator = (FOperator) LtlParser.formula("F (a U b)");

    Set<UnaryModalOperator> choice = ImmutableSet.of(fOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testFScopedSelectorW() {
    FOperator fOperator = (FOperator) LtlParser.formula("F (a W b)");
    GOperator gOperator = (GOperator) LtlParser.formula("G a");

    Set<UnaryModalOperator> choice1 = ImmutableSet.of(fOperator);
    Set<UnaryModalOperator> choice2 = ImmutableSet.of(fOperator, gOperator);
    assertThat(getGScoped(fOperator), containsInAnyOrder(choice1, choice2));
  }

  @Test
  public void testGScopedSelectorM() {
    GOperator gOperator = (GOperator) LtlParser.formula("G (a M b)");
    FOperator fOperator = (FOperator) LtlParser.formula("F a");

    Set<UnaryModalOperator> choice = ImmutableSet.of(gOperator, fOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testGScopedSelectorR() {
    GOperator gOperator = (GOperator) LtlParser.formula("G (a R b)");

    Set<UnaryModalOperator> choice = ImmutableSet.of(gOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testGScopedSelectorU() {
    GOperator gOperator = (GOperator) LtlParser.formula("G (a U b)");
    FOperator fOperator = new FOperator(new Literal(1));

    Set<UnaryModalOperator> choice = ImmutableSet.of(gOperator, fOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testGScopedSelectorW() {
    GOperator gOperator = (GOperator) LtlParser.formula("G (a W b)");

    Set<UnaryModalOperator> choice = ImmutableSet.of(gOperator);
    assertThat(getFScoped(gOperator), containsInAnyOrder(choice));
  }

  @Test
  public void testSelectorSkippedUpwardClosure() {
    Conjunction conjunction = (Conjunction) LtlParser.formula("G (a | F b) & G (b | F a)");
    Disjunction disjunction = (Disjunction) LtlParser.formula("G (F b) | G (F a)");

    Set<GOperator> gOperators = (Set<GOperator>) ((Set) conjunction.children);

    FOperator fOperatorA = new FOperator(new Literal(0));
    FOperator fOperatorB = new FOperator(new Literal(1));

    Set<? extends UnaryModalOperator> baseChoiceConj = gOperators;
    Set<UnaryModalOperator> choiceA = ImmutableSet.of(fOperatorA);
    Set<UnaryModalOperator> choiceB = ImmutableSet.of(fOperatorB);
    Set<UnaryModalOperator> choiceAandB = ImmutableSet.of(fOperatorA, fOperatorB);

    assertThat(getToplevel(conjunction),
      containsInAnyOrder(baseChoiceConj, Sets.union(baseChoiceConj, choiceA),
        Sets.union(baseChoiceConj, choiceB), Sets.union(baseChoiceConj, choiceAandB)));

    assertThat(getToplevel(disjunction),
      containsInAnyOrder(
        Collections2.transform(disjunction.children, (Formula formula) -> Sets.union(
          Collections.singleton(formula), Collector.collectFOperators(formula))).toArray()));
  }

  @Test
  public void testSelectorUpwardClosure() {
    GOperator gOperatorConj = (GOperator) LtlParser.formula("G (((a | F b) & (b | F a)) | c)");
    GOperator gOperatorDisj = (GOperator) LtlParser.formula("G ((a & F b) | (b & F a))");
    FOperator fOperatorA = new FOperator(new Literal(0));
    FOperator fOperatorB = new FOperator(new Literal(1));

    Set<UnaryModalOperator> baseChoiceConj = ImmutableSet.of(gOperatorConj);
    Set<UnaryModalOperator> baseChoiceDisj = ImmutableSet.of(gOperatorDisj);
    Set<UnaryModalOperator> choiceA = ImmutableSet.of(fOperatorA);
    Set<UnaryModalOperator> choiceB = ImmutableSet.of(fOperatorB);
    Set<UnaryModalOperator> choiceAandB = ImmutableSet.of(fOperatorA, fOperatorB);

    assertThat(getToplevel(gOperatorConj),
      containsInAnyOrder(baseChoiceConj, Sets.union(baseChoiceConj, choiceA),
        Sets.union(baseChoiceConj, choiceB), Sets.union(baseChoiceConj, choiceAandB)));
    assertThat(getToplevel(gOperatorDisj),
      containsInAnyOrder(Sets.union(baseChoiceDisj, choiceA), Sets.union(baseChoiceDisj, choiceB),
        Sets.union(baseChoiceDisj, choiceAandB)));
  }
}