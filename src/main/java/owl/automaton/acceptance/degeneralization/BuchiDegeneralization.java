/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.automaton.acceptance.degeneralization;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

/**
 * This class provides a conversion from generalised Büchi automata into Büchi automata. The
 * conversion preserves determinism.
 *
 * <p>This class is also an example on how to do the following:</p>
 * <ul>
 *   <li>How to implement an OwlModule and derive a standalone tool.</li>
 *   <li>How to generate an automaton on-the-fly using the symbolic and explicit API.</li>
 * </ul>
 */
public final class BuchiDegeneralization {

  /**
   * Instantiation of the module. We give the name, a short description, and a function to be
   * called for construction of an instance of the module. {@link OwlModule.AutomatonTransformer}
   * checks the input passed to the instance of the module and does the necessary transformation
   * to obtain a generalized B&uuml;chi automaton if possible. If this is not possible an
   * exception is thrown.
   *
   * <p> This object has to be imported by the {@link owl.run.modules.OwlModuleRegistry} in order to
   * be available on the Owl CLI interface. </p>
   */
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "buchi-degeneralization",
    "Converts generalized Büchi automata into Büchi automata.",
    (commandLine, environment) ->
      OwlModule.AutomatonTransformer
        .of(BuchiDegeneralization::degeneralize, GeneralizedBuchiAcceptance.class)
  );

  private BuchiDegeneralization() {}

  /**
   * Entry-point for standalone CLI tool. This class has be registered in build.gradle
   * in the "Startup Scripts" section in order to get the necessary scripts for CLI invocation.
   *
   * @param args the arguments
   * @throws IOException the exception
   */
  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  /**
   * Degeneralization procedure. This function returns an on-the-fly generated automaton and assumes
   * the the argument automaton is not changed after calling this method.
   *
   * @param automaton the automaton
   * @param <S> the state type
   * @return an on-the-fly generated Büchi automaton
   */
  public static <S> Automaton<IndexedState<S>, BuchiAcceptance> degeneralize(
    Automaton<S, GeneralizedBuchiAcceptance> automaton) {

    // Compute the set of initial states.
    var initialStates = Collections3.transformSet(automaton.initialStates(), IndexedState::of);

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      automaton.factory(), initialStates, BuchiAcceptance.INSTANCE) {

      private final Automaton<S, GeneralizedBuchiAcceptance> backingAutomaton = automaton;
      private final int sets = backingAutomaton.acceptance().acceptanceSets();

      // All possible edges are encoded in a binary decision diagram with multiple terminals.
      @Override
      public MtBdd<Edge<IndexedState<S>>> edgeTreeImpl(IndexedState<S> state) {
        return backingAutomaton.edgeTree(state.state())
          .map(edges -> Collections3.transformSet(edges,
            edge -> transformEdge(edge, state.index())));
      }

      private Edge<IndexedState<S>> transformEdge(Edge<? extends S> edge, int currentIndex) {
        int nextIndex = currentIndex;

        while (nextIndex < sets && edge.colours().contains(nextIndex)) {
          nextIndex++;
        }

        boolean accepting = nextIndex == sets;

        while (nextIndex < currentIndex && edge.colours().contains(nextIndex)) {
          nextIndex++;
        }

        if (accepting) {
          return Edge.of(IndexedState.of(edge.successor()), 0);
        }

        return Edge.of(IndexedState.of(edge.successor(), nextIndex));
      }
    };
  }

  // We use @AutoValue to automatically generate implementations. This is going to be replaced by
  // records once they are available in Java.
  @AutoValue
  public abstract static class IndexedState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    public abstract int index();

    public static <S> IndexedState<S> of(S state) {
      return of(state, 0);
    }

    public static <S> IndexedState<S> of(S state, int index) {
      Preconditions.checkArgument(index >= 0);
      return new AutoValue_BuchiDegeneralization_IndexedState<>(state, index);
    }
  }
}
