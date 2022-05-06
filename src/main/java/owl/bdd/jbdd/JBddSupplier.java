/*
 * Copyright (C) 2017, 2022  (Salomon Sickert, Tobias Meggendorfer)
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

import java.util.List;
import owl.bdd.BddSetFactory;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.FactorySupplier;

public enum JBddSupplier implements FactorySupplier {
  INSTANCE;

  @Override
  public EquivalenceClassFactory getEquivalenceClassFactory(
      List<String> atomicPropositions,
      EquivalenceClassFactory.Encoding defaultEncoding) {

    return new JBddEquivalenceClassFactory(atomicPropositions, defaultEncoding);
  }

  @Override
  public BddSetFactory getBddSetFactory() {
    return new JBddSetFactory(1024);
  }
}
