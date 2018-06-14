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

package owl.translations.ltl2ldba;

import java.util.Collection;
import java.util.Set;

final class AnalysisResult<U extends RecurringObligation> {
  final Set<Jump<U>> jumps;
  final TYPE type;

  private AnalysisResult(TYPE type, Set<Jump<U>> jumps) {
    this.type = type;
    this.jumps = jumps;
  }

  static <U extends RecurringObligation> AnalysisResult<U> buildMay(Collection<Jump<U>> jumps) {
    return new AnalysisResult<>(TYPE.MAY, Set.copyOf(jumps));
  }

  static <U extends RecurringObligation> AnalysisResult<U> buildMust(Jump<U> jump) {
    return new AnalysisResult<>(TYPE.MUST, Set.of(jump));
  }

  enum TYPE {
    MAY, MUST
  }
}
