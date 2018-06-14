/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.rabinizer;

import org.immutables.value.Value;

@Value.Immutable
public class RabinizerConfiguration { // NOPMD
  @Value.Default
  public boolean completeAutomaton() {
    return false;
  }

  @Value.Default
  public boolean computeAcceptance() {
    return true;
  }

  @Value.Default
  public boolean eager() {
    return true;
  }

  @Value.Default
  public boolean supportBasedRelevantFormulaAnalysis() {
    return true;
  }

  @Value.Default
  public boolean suspendableFormulaDetection() {
    return true;
  }

  @Value.Default
  public boolean removeFormulaRepresentative() {
    return false;
  }
}
