package owl.translations;

import java.util.BitSet;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragments;
import owl.translations.mastertheorem.Predicates;

public class BlockingElements {
  private final BitSet atomicPropositions;

  private final Set<Formula.ModalOperator> blockingCoSafety;
  private final Set<Formula.ModalOperator> blockingSafety;

  public BlockingElements(Formula formula) {
    this.atomicPropositions = formula.atomicPropositions(true);
    formula.subformulas(Predicates.IS_FIXPOINT)
      .forEach(x -> atomicPropositions.andNot(x.atomicPropositions(true)));

    if (formula instanceof Conjunction) {
      var greatestFixpoints = formula.subformulas(Predicates.IS_GREATEST_FIXPOINT);
      blockingCoSafety = formula.children()
        .stream()
        .filter(x -> x instanceof Formula.ModalOperator
          && SyntacticFragments.isCoSafety(x)
          && greatestFixpoints.stream().noneMatch(y -> y.anyMatch(x::equals)))
        .map(Formula.ModalOperator.class::cast)
        .collect(Collectors.toUnmodifiableSet());
    } else {
      blockingCoSafety = Set.of();
    }

    if (formula instanceof Disjunction) {
      var fixpoints = formula
        .subformulas(Predicates.IS_FIXPOINT)
        .stream()
        .filter(
          x -> !SyntacticFragments.isCoSafety(x) && !SyntacticFragments.isSafety(x))
        .collect(Collectors.toSet());

      blockingSafety = formula.children()
        .stream()
        .filter(x -> x instanceof Formula.ModalOperator
          && SyntacticFragments.isSafety(x)
          && fixpoints.stream().noneMatch(y -> y.anyMatch(x::equals)))
        .map(Formula.ModalOperator.class::cast)
        .collect(Collectors.toUnmodifiableSet());
    } else {
      blockingSafety = Set.of();
    }
  }

  public boolean isBlockedByCoSafety(EquivalenceClass clazz) {
    var modalOperators = clazz.modalOperators();

    return SyntacticFragments.isCoSafety(modalOperators)
      || clazz.atomicPropositions(true).intersects(atomicPropositions)
      || !Collections.disjoint(blockingCoSafety, modalOperators);
  }

  public boolean isBlockedBySafety(EquivalenceClass clazz) {
    var modalOperators = clazz.modalOperators();

    return SyntacticFragments.isSafety(modalOperators)
      || !Collections.disjoint(blockingSafety, modalOperators);
  }
}
