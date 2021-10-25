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

package owl.translations.mastertheorem;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import owl.ltl.FOperator;
import owl.ltl.Fixpoint;
import owl.ltl.Formula;
import owl.ltl.Formulas;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;

@AutoValue
public abstract class Fixpoints implements Comparable<Fixpoints> {

  public abstract Set<? extends Fixpoint.LeastFixpoint> leastFixpoints();

  public abstract Set<? extends Fixpoint.GreatestFixpoint> greatestFixpoints();

  public static Fixpoints of(Collection<? extends Fixpoint.LeastFixpoint> leastFixpoints,
    Collection<? extends Fixpoint.GreatestFixpoint> greatestFixpoints) {

    return new AutoValue_Fixpoints(
      Set.copyOf(leastFixpoints),
      Set.copyOf(greatestFixpoints));
  }

  public static Fixpoints of(Collection<? extends Formula.TemporalOperator> fixpoints) {
    Set<Fixpoint.LeastFixpoint> leastFixpoints = new HashSet<>();
    Set<Fixpoint.GreatestFixpoint> greatestFixpoints = new HashSet<>();

    for (Formula.TemporalOperator fixpoint : fixpoints) {
      if (fixpoint instanceof Fixpoint.LeastFixpoint leastFixpoint) {
        leastFixpoints.add(leastFixpoint);
      } else {
        assert fixpoint instanceof Fixpoint.GreatestFixpoint;
        greatestFixpoints.add((Fixpoint.GreatestFixpoint) fixpoint);
      }
    }

    return of(Set.of(leastFixpoints.toArray(Fixpoint.LeastFixpoint[]::new)),
      Set.of(greatestFixpoints.toArray(Fixpoint.GreatestFixpoint[]::new)));
  }

  public boolean allFixpointsPresent(
    Collection<? extends Formula.TemporalOperator> formulas) {
    Set<Fixpoint> waitingFixpoints = new HashSet<>(leastFixpoints());
    waitingFixpoints.addAll(greatestFixpoints());
    waitingFixpoints.removeAll(formulas);
    return waitingFixpoints.isEmpty();
  }

  @Override
  public int compareTo(Fixpoints that) {
    return Formulas.compare((Set) this.fixpoints(), (Set) that.fixpoints());
  }

  @Override
  public abstract boolean equals(Object o);

  @Memoized
  public Set<Fixpoint> fixpoints() {
    return Set.copyOf(Sets.union(leastFixpoints(), greatestFixpoints()));
  }

  @Memoized
  @Override
  public abstract int hashCode();

  @Memoized
  public Fixpoints simplified() {
    Set<FOperator> fOperators = new HashSet<>();
    Set<GOperator> gOperators = new HashSet<>();

    leastFixpoints().forEach(x -> {
      if (x instanceof MOperator) {
        fOperators.add(new FOperator(((MOperator) x).leftOperand()));
      } else if (x instanceof UOperator) {
        fOperators.add(new FOperator(((UOperator) x).rightOperand()));
      } else {
        fOperators.add((FOperator) x);
      }
    });

    greatestFixpoints().forEach(x -> {
      if (x instanceof ROperator) {
        gOperators.add(new GOperator(((ROperator) x).rightOperand()));
      } else if (x instanceof WOperator) {
        gOperators.add(new GOperator(((WOperator) x).leftOperand()));
      } else {
        gOperators.add((GOperator) x);
      }
    });

    var simplifiedFixpoints = of(fOperators, gOperators);
    return this.equals(simplifiedFixpoints) ? this : simplifiedFixpoints;
  }
}
