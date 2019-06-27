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

import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddConfiguration;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;
import java.util.List;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;

public final class JBddSupplier implements FactorySupplier {
  private static final JBddSupplier PLAIN = new JBddSupplier(false);
  private static final JBddSupplier ANNOTATED = new JBddSupplier(true);

  private final boolean keepRepresentativesDefault;

  private JBddSupplier(boolean keepRepresentativesDefault) {
    this.keepRepresentativesDefault = keepRepresentativesDefault;
  }

  public static FactorySupplier async(boolean keepRepresentativesDefault) {
    return keepRepresentativesDefault ? ANNOTATED : PLAIN;
  }

  private Bdd create(int size) {
    BddConfiguration configuration = ImmutableBddConfiguration.builder()
      .logStatisticsOnShutdown(false)
      .build();
    return BddFactory.buildBdd(size, configuration);
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet) {
    return getEquivalenceClassFactory(alphabet, keepRepresentativesDefault);
  }

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet,
    boolean keepRepresentatives) {
    Bdd eqFactoryBdd = create(1024 * (alphabet.size() + 1));
    return new EquivalenceFactory(eqFactoryBdd, alphabet, keepRepresentatives);
  }

  @Override
  public Factories getFactories(List<String> alphabet) {
    return new Factories(
      getEquivalenceClassFactory(alphabet, keepRepresentativesDefault),
      getValuationSetFactory(alphabet));
  }

  @Override
  public ValuationSetFactory getValuationSetFactory(List<String> alphabet) {
    int alphabetSize = alphabet.size();
    Bdd vsFactoryBdd = create((1024 * alphabetSize * alphabetSize) + 256);
    return new ValuationFactory(vsFactoryBdd, alphabet);
  }
}
