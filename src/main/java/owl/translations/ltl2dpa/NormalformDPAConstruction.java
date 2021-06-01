/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.ZielonkaState;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import owl.translations.ltl2dela.NormalformDELAConstruction;

public final class NormalformDPAConstruction
  implements Function<LabelledFormula, Automaton<ZielonkaState<State>, ParityAcceptance>> {

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "ltl2dpaNormalform",
    "Translate LTL to DPA using normalforms and zielonka split trees.",
    new Options()
      .addOption(null, "X-lookahead", true,
      "Only used for testing internal implementation."),
    (commandLine, environment) -> {
      OptionalInt lookahead;

      if (commandLine.hasOption("X-lookahead")) {
        String value = commandLine.getOptionValue("X-lookahead");
        int intValue = Integer.parseInt(value);

        if (intValue < -1) {
          throw new IllegalArgumentException();
        }

        lookahead = intValue == -1 ? OptionalInt.empty() : OptionalInt.of(intValue);
      } else {
        lookahead = OptionalInt.empty();
      }

      return OwlModule.LabelledFormulaTransformer.of(
        x -> new NormalformDPAConstruction(lookahead).apply(x));
    });

  private final NormalformDELAConstruction normalformDELAConstruction;

  private final OptionalInt lookahead;

  public NormalformDPAConstruction(OptionalInt lookahead) {
    this.lookahead = lookahead;
    this.normalformDELAConstruction = new NormalformDELAConstruction(lookahead);
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  @Override
  public AutomatonWithZielonkaTreeLookup<ZielonkaState<State>, ParityAcceptance>
    apply(LabelledFormula formula) {

    var delwConstruction = normalformDELAConstruction.applyConstruction(formula);
    return transform(
      delwConstruction.automaton(),
      lookahead,
      State::inDifferentSccs,
      delwConstruction::alpha,
      delwConstruction::beta);
  }

  // [0, 1, 2, 3, ...] -> [0.5, 0.25, 0.125, ...]
  private static double scaleLevel(int level) {
    assert 0 <= level;
    return Math.pow(2.0d, -(level + 1));
  }

  private static double approximateTrueness(
    PropositionalFormula<Integer> formula,
    Map<Integer, BreakpointStateRejecting> stateMap,
    BitSet processedStates) {

    if (formula instanceof PropositionalFormula.Variable) {
      int index = ((PropositionalFormula.Variable<Integer>) formula).variable;

      // Do not contribute.
      if (processedStates.get(index)) {
        return 0.5d;
      }

      return stateMap.get(index).all().trueness();
    }

    if (formula instanceof PropositionalFormula.Negation) {
      return 1.0d - approximateTrueness(
        ((PropositionalFormula.Negation<Integer>) formula).operand, stateMap, processedStates);
    }

    if (formula instanceof PropositionalFormula.Biconditional) {
      var castedFormula = (PropositionalFormula.Biconditional<Integer>) formula;

      return Math.abs(
        approximateTrueness(castedFormula.leftOperand, stateMap, processedStates)
          - approximateTrueness(castedFormula.rightOperand, stateMap, processedStates)
      );
    }

    if (formula instanceof PropositionalFormula.Conjunction) {
      var castedFormula = (PropositionalFormula.Conjunction<Integer>) formula;

      double trueness = 1.0d;

      for (var conjunct : castedFormula.conjuncts) {
        trueness = Math.min(trueness, approximateTrueness(conjunct, stateMap, processedStates));
      }

      return trueness;
    }

    assert formula instanceof PropositionalFormula.Disjunction;

    var castedFormula = (PropositionalFormula.Disjunction<Integer>) formula;

    double trueness = 0.0d;

    for (var disjunct : castedFormula.disjuncts) {
      trueness = Math.max(trueness, approximateTrueness(disjunct, stateMap, processedStates));
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
        ImmutableIntArray path = successor.path().indices();
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
            var dbwState = successor.state().stateMap().get(activeColour);
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
          successor.state().stateMap(),
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
