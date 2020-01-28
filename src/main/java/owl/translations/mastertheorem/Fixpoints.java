/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Formulas;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;

@AutoValue
public abstract class Fixpoints implements Comparable<Fixpoints> {

  public abstract Set<Formula.TemporalOperator> leastFixpoints();

  public abstract Set<Formula.TemporalOperator> greatestFixpoints();

  public static Fixpoints of(Collection<? extends Formula.TemporalOperator> leastFixpoints,
    Collection<? extends Formula.TemporalOperator> greatestFixpoints) {
    Set<Formula.TemporalOperator> leastFixpointsCopy = Set.copyOf(leastFixpoints);
    Set<Formula.TemporalOperator> greatestFixpointsCopy = Set.copyOf(greatestFixpoints);

    checkArgument(leastFixpointsCopy.stream().allMatch(Predicates.IS_LEAST_FIXPOINT));
    checkArgument(greatestFixpointsCopy.stream().allMatch(Predicates.IS_GREATEST_FIXPOINT));

    return new AutoValue_Fixpoints(leastFixpointsCopy, greatestFixpointsCopy);
  }

  public static Fixpoints of(Collection<? extends Formula.TemporalOperator> fixpoints) {
    Set<Formula.TemporalOperator> leastFixpoints = new HashSet<>();
    Set<Formula.TemporalOperator> greatestFixpoints = new HashSet<>();

    for (Formula.TemporalOperator fixpoint : fixpoints) {
      if (Predicates.IS_LEAST_FIXPOINT.test(fixpoint)) {
        leastFixpoints.add(fixpoint);
      } else {
        assert Predicates.IS_GREATEST_FIXPOINT.test(fixpoint);
        greatestFixpoints.add(fixpoint);
      }
    }

    return of(Set.of(leastFixpoints.toArray(Formula.TemporalOperator[]::new)),
      Set.of(greatestFixpoints.toArray(Formula.TemporalOperator[]::new)));
  }

  public boolean allFixpointsPresent(
    Collection<? extends Formula.TemporalOperator> formulas) {
    Set<Formula> waitingFixpoints = new HashSet<>(leastFixpoints());
    waitingFixpoints.addAll(greatestFixpoints());
    waitingFixpoints.removeAll(formulas);
    return waitingFixpoints.isEmpty();
  }

  @Override
  public int compareTo(Fixpoints that) {
    return Formulas.compare(this.fixpoints(), that.fixpoints());
  }

  @Override
  public abstract boolean equals(Object o);

  @Memoized
  public Set<Formula.TemporalOperator> fixpoints() {
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
    return this.equals(simplifiedFixpoints) ?  this : simplifiedFixpoints;
  }
}
