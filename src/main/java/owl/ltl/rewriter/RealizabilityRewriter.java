package owl.ltl.rewriter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.DefaultConverter;

public class RealizabilityRewriter {

  private RealizabilityRewriter() {}

  private static Formula removeAtoms(BitSet inputVariablesMask, Formula formula,
    Map<Integer, Boolean> fixedValuations) {
    BitSet positiveAtoms = Collector.collectAtoms(formula, false);
    BitSet negativeAtoms = Collector.collectAtoms(formula, true);

    BitSet singleAtoms = (BitSet) positiveAtoms.clone();
    singleAtoms.xor(negativeAtoms);

    BitSet inputSingleAtoms = (BitSet) singleAtoms.clone();
    inputSingleAtoms.and(inputVariablesMask);

    BitSet outputSingleAtoms = (BitSet) singleAtoms.clone();
    outputSingleAtoms.andNot(inputVariablesMask);

    return formula
      .accept(new AtomSimplifier(BooleanConstant.FALSE, inputSingleAtoms, fixedValuations))
      .accept(new AtomSimplifier(BooleanConstant.TRUE, outputSingleAtoms, fixedValuations));
  }

  public static List<Formula> split(Formula formula, BitSet inputVariablesMask) {
    return split(inputVariablesMask, formula, new HashMap<>());
  }

  public static List<Formula> split(BitSet inputVariablesMask, Formula formula,
    Map<Integer, Boolean> fixedValuations) {
    Formula original;
    Formula rewritten = formula;

    do {
      original = rewritten;
      rewritten = removeAtoms(inputVariablesMask, original, fixedValuations);
    } while (!original.equals(rewritten));

    // Group by
    Map<Formula, BitSet> groups = new HashMap<>();

    for (Set<Formula> set : NormalForms.toCnf(original)) {
      Set<Formula> conjunction = new HashSet<>();
      conjunction.add(Disjunction.of(set));
      BitSet outputVariables = Collector.collectAtoms(set);
      outputVariables.andNot(inputVariablesMask);

      for (boolean removedElement = true; removedElement;) {
        removedElement = groups.entrySet().removeIf(x -> {
          BitSet intersection = (BitSet) x.getValue().clone();
          intersection.and(outputVariables);

          if (!intersection.isEmpty()) {
            conjunction.add(x.getKey());
            outputVariables.or(x.getValue());
            return true;
          }

          return false;
        });
      }

      groups.put(Conjunction.of(conjunction), outputVariables);
    }

    return new ArrayList<>(groups.keySet());
  }

  public static Formula[] split(Formula formula, int numberOfInputSignals,
    Map<Integer, Boolean> fixedValuations) {
    BitSet inputVariablesMask = new BitSet(numberOfInputSignals);
    inputVariablesMask.set(0, numberOfInputSignals);
    List<Formula> var = split(inputVariablesMask, formula, fixedValuations);
    return var.toArray(new Formula[var.size()]);
  }

  static class AtomSimplifier extends DefaultConverter {
    final BooleanConstant constant;
    final BitSet singleAtoms;
    final Map<Integer, Boolean> fixedValuations;

    AtomSimplifier(BooleanConstant constant, BitSet singleAtoms,
      Map<Integer, Boolean> fixedValuations) {
      this.constant = constant;
      this.singleAtoms = singleAtoms;
      this.fixedValuations = fixedValuations;
    }

    @Override
    public Formula visit(Literal literal) {
      if (singleAtoms.get(literal.getAtom())) {
        boolean value = literal.isNegated() ? constant.not().value : constant.value;
        Boolean oldValue = fixedValuations.put(literal.getAtom(), value);
        assert oldValue == null || oldValue == value;
        return constant;
      }

      return literal;
    }
  }
}
