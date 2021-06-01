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

package owl.translations.rabinizer;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class RabinizerConfiguration {
  public abstract boolean eager();

  public abstract boolean supportBasedRelevantFormulaAnalysis();

  public abstract boolean suspendableFormulaDetection();

  public static RabinizerConfiguration of(
    boolean eager,
    boolean supportBasedRelevantFormulaAnalysis,
    boolean suspendableFormulaDetection) {
    return new AutoValue_RabinizerConfiguration(
      eager,
      supportBasedRelevantFormulaAnalysis,
      suspendableFormulaDetection);
  }
}
