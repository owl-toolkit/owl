/*
 * Copyright (C) 2021, 2022  (Salomon Sickert)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.translations.ltl2dpa;

import static owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.AutomatonWithZielonkaTreeLookup;
import static owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.ZielonkaTree;
import static owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.transform;
import static owl.translations.ltl2dela.NormalformDELAConstruction.State;

import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.ZielonkaState;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.LabelledFormula;
import owl.translations.ltl2dela.NormalformDELAConstruction;

public final class NormalformDPAConstruction
    implements Function<LabelledFormula, Automaton<ZielonkaState<State>, ParityAcceptance>> {

  private final NormalformDELAConstruction normalformDELAConstruction;
  private final OptionalInt lookahead;

  public NormalformDPAConstruction(OptionalInt lookahead) {
    this.lookahead = lookahead;
    this.normalformDELAConstruction = new NormalformDELAConstruction();
  }

  @Override
  public AutomatonWithZielonkaTreeLookup<ZielonkaState<State>, ParityAcceptance>
  apply(LabelledFormula formula) {

    var delwConstruction = normalformDELAConstruction.applyConstruction(formula);
    return transform(
        delwConstruction.automaton(),
        lookahead,
        State::inDifferentSccs,
        delwConstruction::alpha
    );
  }

  // [0, 1, 2, 3, ...] -> [0.5, 0.25, 0.125, ...]
  private static double scaleLevel(int level) {
    assert 0 <= level;
    return Math.pow(2.0d, -(level + 1));
  }

  private static double approximateTrueness(
      PropositionalFormula<Integer> formula,
      State state,
      BitSet processedStates) {

    if (formula instanceof PropositionalFormula.Variable<Integer> variable) {

      // Do not contribute.
      if (processedStates.get(variable.variable())) {
        return 0.5d;
      }

      return state.get(variable.variable()).all().trueness();
    }

    if (formula instanceof PropositionalFormula.Negation<Integer> negation) {
      return 1.0d - approximateTrueness(
          negation.operand(), state, processedStates);
    }

    if (formula instanceof PropositionalFormula.Biconditional<Integer> biconditional) {

      return Math.abs(
          approximateTrueness(biconditional.leftOperand(), state, processedStates)
              - approximateTrueness(biconditional.rightOperand(), state, processedStates)
      );
    }

    if (formula instanceof PropositionalFormula.Conjunction<Integer> conjunction) {
      double trueness = 1.0d;

      for (var conjunct : conjunction.conjuncts()) {
        trueness = Math.min(trueness, approximateTrueness(conjunct, state, processedStates));
      }

      return trueness;
    }

    assert formula instanceof PropositionalFormula.Disjunction;

    var disjunction = (PropositionalFormula.Disjunction<Integer>) formula;

    double trueness = 0.0d;

    for (var disjunct : disjunction.disjuncts()) {
      trueness = Math.max(trueness, approximateTrueness(disjunct, state, processedStates));
    }

    return trueness;
  }

  public static ToDoubleFunction<Edge<ZielonkaState<State>>> scoringFunction(
      AutomatonWithZielonkaTreeLookup<ZielonkaState<State>, ParityAcceptance> automaton) {

    return edge -> {
      ZielonkaState<State> successor = edge.successor();
      ZielonkaTree tree = automaton.lookup(successor);

      // We compute the colours of the path to the current leaf.
      List<ImmutableBitSet> colours = new ArrayList<>();

      {
        ImmutableIntArray path = successor.path();
        ZielonkaTree node = tree;

        int i = 0;

        for (int s = path.length(); i <= s; i++) {
          colours.add(node.colours());

          if (i < s) {
            node = node.children().get(path.get(i));
          }
        }

        // The tree is trivial...
        if (colours.size() <= 1) {
          colours.clear();
        }
      }

      // The score is computed as follows:
      // - for each level we compute a local score that is discounted by the level (2^-(i+1))
      // - the local score takes the minimal height one can achieve, again discounted.
      double score = 0.0d;
      BitSet processedStates = new BitSet();

      // Process components relevant to the Zielonka-tree.
      {
        for (int i = 0, s = colours.size(); i < s - 1; i++) {
          Set<Integer> activeColours = BitSet2.asSet(colours.get(i).copyInto(new BitSet()));
          activeColours.removeAll(colours.get(i + 1));

          double nextBuechiEvent = 0.0d;

          for (int activeColour : activeColours) {
            var dbwState = Objects.requireNonNull(successor.state().get(activeColour));
            processedStates.set(activeColour);

            // TODO: access round-robin information to find other chained state components.
            if (!dbwState.isSuspended()) {
              nextBuechiEvent = Math.max(nextBuechiEvent, dbwState.rejecting().trueness());
            }
          }

          score = score + scaleLevel(i) * nextBuechiEvent;
        }
      }

      // Process components irrelevant to the Zielonka-tree.
      {
        double trueness = approximateTrueness(
            successor.state().stateFormula(),
            successor.state(),
            processedStates);

        double nextEvent = 2.0d * Math.abs(trueness - 0.5d);

        assert colours.size() != 1;
        score = colours.isEmpty() ? nextEvent : score + scaleLevel(colours.size() - 1) * nextEvent;
      }

      assert 0.0d <= score;
      assert score <= 1.1d; // allow some slack due to rounding.

      return score;
    };
  }
}
