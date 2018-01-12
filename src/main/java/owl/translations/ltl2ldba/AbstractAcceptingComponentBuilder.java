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

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.ldba.MutableAutomatonBuilder;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public abstract class AbstractAcceptingComponentBuilder<S, T extends OmegaAcceptance,
  U extends RecurringObligation> implements MutableAutomatonBuilder<Jump<U>, S, T> {

  protected final List<S> anchors = new ArrayList<>();
  protected final Factories factories;
  protected final EquivalenceClassStateFactory factory;

  protected AbstractAcceptingComponentBuilder(ImmutableSet<Configuration> optimisations,
    Factories factories) {
    this.factory = new EquivalenceClassStateFactory(factories.eqFactory, optimisations);
    this.factories = factories;
  }

  @Nullable
  @Override
  public S add(@Nullable Jump<U> jump) {
    if (jump == null) {
      return null;
    }

    S state = createState(jump.remainder, jump.obligations);

    if (state == null) {
      return null;
    }

    anchors.add(state);
    return state;
  }

  @Nullable
  protected abstract S createState(EquivalenceClass remainder, U obligations);
}
