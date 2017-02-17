/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.ltl2ldba;

import java.util.BitSet;
import java.util.EnumSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import owl.automaton.AutomatonState;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.ldba.AbstractInitialComponent;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.Optimisation;

public class InitialComponent<S extends AutomatonState<S>, T>
  extends AbstractInitialComponent<InitialComponentState, S> {

  protected final EquivalenceClassStateFactory factory;
  final Selector<T> selector;
  private final AbstractAcceptingComponent<S, ? extends GeneralizedBuchiAcceptance, T>
    acceptingComponent;
  private final Evaluator<T> evaluator;

  protected InitialComponent(
    AbstractAcceptingComponent<S, ? extends GeneralizedBuchiAcceptance, T> acceptingComponent,
    Factories factories,
    EnumSet<Optimisation> optimisations,
    Selector<T> selector,
    Evaluator<T> evaluator) {
    super(factories);

    this.acceptingComponent = acceptingComponent;
    this.selector = selector;
    this.evaluator = evaluator;

    factory = new EquivalenceClassStateFactory(factories.equivalenceClassFactory, optimisations);
  }

  @Nullable
  InitialComponentState addInitialState(EquivalenceClass initialClass) {
    if (initialClass.isFalse()) {
      return null;
    }

    InitialComponentState initialState = new InitialComponentState(this,
      factory.getInitial(initialClass));
    initialStates.add(initialState);
    return initialState;
  }

  @Override
  public void generateJumps(InitialComponentState state) {
    selector.select(state.getClazz()).forEach((obligation) -> {
      if (obligation == null) {
        return;
      }

      EquivalenceClass remainingGoal = this.evaluator.evaluate(state.getClazz(), obligation);
      S successor = acceptingComponent.jump(remainingGoal, obligation);

      if (successor == null) {
        return;
      }

      epsilonJumps.put(state, successor);
    });

    InitialComponentState trueSink =
      new InitialComponentState(this, factories.equivalenceClassFactory.getTrue());
    updateEdge(trueSink, trueSink, getAcceptBitSet());
  }

  @Nonnull
  @Override
  public InitialComponentState generateRejectingTrap() {
    return new InitialComponentState(this, factories.equivalenceClassFactory.getFalse());
  }

  public BitSet getAcceptBitSet() {
    return acceptingComponent.getAcceptBitSet();
  }
}
