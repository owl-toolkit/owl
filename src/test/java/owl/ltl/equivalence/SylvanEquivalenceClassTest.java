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

package owl.ltl.equivalence;

import owl.factories.EquivalenceClassFactory;
import owl.ltl.Formula;
import owl.factories.Registry;
import owl.factories.Registry.Backend;

public class SylvanEquivalenceClassTest extends EquivalenceClassTest {

  @Override
  public EquivalenceClassFactory setUpFactory(Formula domain) {
    return Registry.getFactories(domain, Backend.SYLVAN).equivalenceClassFactory;
  }

}