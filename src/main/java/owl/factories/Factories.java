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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import ltl.equivalence.EquivalenceClassFactory;
import omega_automaton.collections.valuationset.ValuationSetFactory;

@SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
public class Factories {

  public final EquivalenceClassFactory equivalenceClassFactory;
  public final ValuationSetFactory valuationSetFactory;

  Factories(EquivalenceClassFactory factory1, ValuationSetFactory factory2) {
    equivalenceClassFactory = factory1;
    valuationSetFactory = factory2;
  }
}
