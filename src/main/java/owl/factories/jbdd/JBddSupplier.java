/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.List;
import owl.factories.EquivalenceClassFactory;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;

public final class JBddSupplier implements FactorySupplier {
  private static final JBddSupplier INSTANCE = new JBddSupplier();

  private JBddSupplier() {}

  public static FactorySupplier async() {
    return INSTANCE;
  }

  private static Bdd create(int size) {
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
  public EquivalenceClassFactory getEquivalenceClassFactory(List<String> atomicPropositions) {
    Bdd eqFactoryBdd = create(1024 * (atomicPropositions.size() + 1));
    return new EquivalenceFactory(eqFactoryBdd, atomicPropositions);
  }

  @Override
  public ValuationSetFactory getValuationSetFactory(List<String> atomicPropositions) {
    int alphabetSize = atomicPropositions.size();
    Bdd vsFactoryBdd = create(Math.abs(13 * alphabetSize));
    return new ValuationFactory(vsFactoryBdd, atomicPropositions);
  }
}
