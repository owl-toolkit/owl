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

package owl.bdd;

import java.util.List;
import owl.bdd.jbdd.JBddSupplier;

public interface FactorySupplier {

  static FactorySupplier defaultSupplier() {
    // TODO: add compile-time switch (JDD vs sylvan)
    return JBddSupplier.INSTANCE;
  }

  BddSetFactory getBddSetFactory();

  default EquivalenceClassFactory getEquivalenceClassFactory(List<String> atomicPropositions) {
    return getEquivalenceClassFactory(
      atomicPropositions,
      EquivalenceClassFactory.Encoding.AP_COMBINED);
  }

  EquivalenceClassFactory getEquivalenceClassFactory(
    List<String> atomicPropositions, EquivalenceClassFactory.Encoding defaultEncoding);

  default Factories getFactories(List<String> atomicPropositions) {
    return getFactories(atomicPropositions, EquivalenceClassFactory.Encoding.AP_COMBINED);
  }

  default Factories getFactories(
    List<String> atomicPropositions, EquivalenceClassFactory.Encoding defaultEncoding) {

    return new Factories(
      getEquivalenceClassFactory(atomicPropositions, defaultEncoding),
      getBddSetFactory());
  }
}
