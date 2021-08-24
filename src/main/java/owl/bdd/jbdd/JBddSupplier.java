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

package owl.bdd.jbdd;

import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.List;
import owl.bdd.BddSetFactory;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.FactorySupplier;

public enum JBddSupplier implements FactorySupplier {
  JBDD_SUPPLIER_INSTANCE;

  static Bdd create(int size) {
    var configuration = ImmutableBddConfiguration.builder()
      .logStatisticsOnShutdown(false)
      .useGlobalComposeCache(false)
      .integrityDuplicatesMaximalSize(50)
      .cacheBinaryDivider(4)
      .cacheTernaryDivider(4)
      .growthFactor(2)
      .build();

    // Do not use buildBddIterative, since 'support(...)' is broken.
    return BddFactory.buildBddRecursive(size, configuration);
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(
    List<String> atomicPropositions, EquivalenceClassFactory.Encoding defaultEncoding) {
    Bdd eqFactoryBdd = create(1024 * (atomicPropositions.size() + 1));
    return new JBddEquivalenceClassFactory(eqFactoryBdd, atomicPropositions, defaultEncoding);
  }

  @Override
  public BddSetFactory getBddSetFactory() {
    return new JBddSetFactory(create(1024));
  }
}