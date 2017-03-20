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

package owl.translations;

public enum Optimisation {
  /* LDBA Constructions */
  SCC_ANALYSIS, REMOVE_EPSILON_TRANSITIONS,

  /* ltl2ldba */
  EAGER_UNFOLD, REMOVE_REDUNDANT_OBLIGATIONS, FORCE_JUMPS, MINIMIZE_JUMPS,
  OPTIMISED_CONSTRUCTION_FOR_FRAGMENTS, DETERMINISTIC_INITIAL_COMPONENT,

  /* ltl2dpa */
  PARALLEL, PERMUTATION_SHARING,

  /* fgx2dba */
  DYNAMIC_HISTORY
}
