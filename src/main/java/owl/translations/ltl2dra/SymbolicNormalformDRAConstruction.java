package owl.translations.ltl2dra;

import static owl.logic.propositional.PropositionalFormula.Conjunction;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import owl.automaton.symbolic.SymbolicAutomaton;
import owl.automaton.symbolic.SymbolicBooleanOperations;
import owl.bdd.BddSetFactory;
import owl.bdd.Factories;
import owl.bdd.FactorySupplier;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.translations.canonical.DeterministicConstructions;

public class SymbolicNormalformDRAConstruction extends AbstractNormalformDRAConstruction
  implements Function<LabelledFormula, SymbolicAutomaton<?>> {

  private BddSetFactory factory;

  public SymbolicNormalformDRAConstruction(BddSetFactory factory) {
    this(true, factory);
  }

  public SymbolicNormalformDRAConstruction(boolean useDualConstruction, BddSetFactory factory) {
    super(useDualConstruction);
    this.factory = factory;
  }

  @Override
  public SymbolicAutomaton<?> apply(LabelledFormula labelledFormula) {
    var pairs = group(labelledFormula.nnf());

    if (pairs.isEmpty()) {
      pairs = List.of(
        Sigma2Pi2Pair.of(labelledFormula.atomicPropositions(),
        BooleanConstant.FALSE, BooleanConstant.FALSE)
      );
    }

    Factories explicitFactories = FactorySupplier.defaultSupplier()
      .getFactories(labelledFormula.atomicPropositions());
    Map<LabelledFormula, Integer> coSafetySafetyIndices = new HashMap<>();
    Map<LabelledFormula, Integer> safetyCoSafetyIndices = new HashMap<>();

    List<SymbolicAutomaton<?>> automata = new ArrayList<>();
    List<PropositionalFormula<Integer>> disjuncts = new ArrayList<>();

    for (Sigma2Pi2Pair formulaPair : pairs) {
      var sigma2 = formulaPair.sigma2();
      var pi2 = formulaPair.pi2();

      var coSafetySafetyIndex = coSafetySafetyIndices.get(sigma2);

      if (coSafetySafetyIndex == null) {
        coSafetySafetyIndex = automata.size();
        coSafetySafetyIndices.put(sigma2, coSafetySafetyIndex);
        automata.add(SymbolicAutomaton.of(
          DeterministicConstructions.CoSafetySafety.of(
            explicitFactories, sigma2.formula(), true, false
          ), factory));
      }

      var safetyCoSafetyIndex = safetyCoSafetyIndices.get(pi2);

      if (safetyCoSafetyIndex == null) {
        safetyCoSafetyIndex = automata.size();
        safetyCoSafetyIndices.put(pi2, safetyCoSafetyIndex);
        automata.add(SymbolicAutomaton.of(
          DeterministicConstructions.SafetyCoSafety.of(
            explicitFactories, pi2.formula(), true, false
          ), factory));
      }

      disjuncts.add(Conjunction.of(
        Variable.of(coSafetySafetyIndex), Variable.of(safetyCoSafetyIndex)));
    }

    return SymbolicBooleanOperations.deterministicProduct(Disjunction.of(disjuncts), automata);
  }
}
