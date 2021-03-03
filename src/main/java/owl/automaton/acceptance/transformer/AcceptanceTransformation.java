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

package owl.automaton.acceptance.transformer;

import com.google.auto.value.AutoValue;
import java.util.function.Function;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.edge.Colours;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;
import owl.collections.Collections3;

abstract class AcceptanceTransformation {

  interface AcceptanceTransformer<A extends EmersonLeiAcceptance, E> {
    A transformedAcceptance();

    E initialExtension();

    Edge<E> transformColours(Colours edge, E extension);
  }

  static <S, E, A extends EmersonLeiAcceptance, B extends EmersonLeiAcceptance>
    Automaton<ExtendedState<S, E>, B> transform(
      Automaton<S, ? extends A> automaton,
      Function<A, ? extends AcceptanceTransformer<B, E>> constructor) {

    var transformer = constructor.apply(automaton.acceptance());
    E initialExtension = transformer.initialExtension();
    var initialStates = Collections3.transformSet(
      automaton.initialStates(), s -> ExtendedState.of(s, initialExtension));

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      automaton.factory(), initialStates, transformer.transformedAcceptance()) {

      @Override
      protected MtBdd<Edge<ExtendedState<S, E>>> edgeTreeImpl(ExtendedState<S, E> state) {
        return automaton.edgeTree(state.state())
          .map(x -> Collections3.transformSet(x, y -> transformEdge(y, state.extension())));
      }

      private Edge<ExtendedState<S, E>> transformEdge(Edge<? extends S> edge, E annotation) {
        Edge<E> extensionEdge = transformer.transformColours(edge.colours(), annotation);
        return extensionEdge.mapSuccessor(x -> ExtendedState.of(edge.successor(), x));
      }
    };
  }

  @AutoValue
  public abstract static class ExtendedState<S, E> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    public abstract E extension();

    public static <S, E> ExtendedState<S, E> of(S state, E extension) {
      return new AutoValue_AcceptanceTransformation_ExtendedState(state, extension);
    }
  }
}
