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
  SUPPRESS_JUMPS_FOR_TRANSIENT_STATES, REMOVE_EPSILON_TRANSITIONS,

  /* ltl2ldba */
  DETERMINISTIC_INITIAL_COMPONENT, EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE,
  SUPPRESS_JUMPS,

  /* nba2ldba */
  COMPUTE_SAFETY_PROPERTY,

  /* ldba2dpa */
  RESET_AFTER_SCC_SWITCH,

  /* ltl2dpa */
  COMPLEMENT_CONSTRUCTION, EXISTS_SAFETY_CORE, COMPLETE
}
