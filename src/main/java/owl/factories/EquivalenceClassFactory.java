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

package owl.factories;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Iterator;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

public interface EquivalenceClassFactory {
  EquivalenceClass of(Formula formula);

  default EquivalenceClass conjunction(EquivalenceClass... classes) {
    return conjunction(Arrays.asList(classes));
  }

  default EquivalenceClass conjunction(Iterable<EquivalenceClass> classes) {
    return conjunction(classes.iterator());
  }

  EquivalenceClass conjunction(Iterator<EquivalenceClass> classes);

  default EquivalenceClass disjunction(EquivalenceClass... classes) {
    return disjunction(Arrays.asList(classes));
  }

  default EquivalenceClass disjunction(Iterable<EquivalenceClass> classes) {
    return disjunction(classes.iterator());
  }

  EquivalenceClass disjunction(Iterator<EquivalenceClass> classes);

  default EquivalenceClass getFalse() {
    return of(BooleanConstant.FALSE);
  }

  default EquivalenceClass getTrue() {
    return of(BooleanConstant.TRUE);
  }

  ImmutableList<String> getVariables();
}
