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

package owl.factories.jbdd;

import de.tum.in.jbdd.BddFactory;
import java.util.List;
import owl.collections.ValuationSetTest;
import owl.factories.ValuationSetFactory;

public class BddValuationSetTest extends ValuationSetTest {
  @Override
  protected ValuationSetFactory setUpFactory(List<String> aliases) {
    return new ValuationFactory(BddFactory.buildBdd(1024), aliases);
  }
}
