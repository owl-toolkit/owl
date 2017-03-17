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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import owl.automaton.ExploreBuilder;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.Optimisation;

public abstract class AbstractAcceptingComponentBuilder<S, T extends OmegaAcceptance, U>
  implements ExploreBuilder<Jump<U>, S, T> {

  protected final List<S> anchors = new ArrayList<>();
  private final JumpEvaluator<U> evaluator;
  protected final Factories factories;
  protected final EquivalenceClassStateFactory factory;

  protected AbstractAcceptingComponentBuilder(EnumSet<Optimisation> optimisations,
    Factories factories, JumpEvaluator<U> evaluator) {
    this.factory = new EquivalenceClassStateFactory(factories.equivalenceClassFactory,
      optimisations);
    this.factories = factories;
    this.evaluator = evaluator;
  }

  @Nullable
  @Override
  public S add(Jump<U> jump) {
    EquivalenceClass remainingGoal = evaluator.evaluate(jump);

    if (remainingGoal.isFalse()) {
      return null;
    }

    S state = createState(remainingGoal, jump.obligations);

    if (state == null) {
      return null;
    }

    anchors.add(state);
    return state;
  }

  @Nullable
  protected abstract S createState(EquivalenceClass remainder, U obligations);
}
