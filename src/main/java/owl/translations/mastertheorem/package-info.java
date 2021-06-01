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

/**
 * Provides the implementation of the Master Theorem as well as an efficient normalisation procedure
 * for linear temporal logic.
 *
 * <p>The Master Theorem is described in {@value owl.Bibliography#DISSERTATION_19_CITEKEY}
 * ({@link owl.Bibliography#DISSERTATION_19}) and has been presented before in the preceding
 * conference publication {@value owl.Bibliography#LICS_18_CITEKEY}
 * ({@link owl.Bibliography#LICS_18}).</p>
 *
 * <p>The normalisation procedure is described in {@value owl.Bibliography#LICS_20_CITEKEY}
 * ({@link owl.Bibliography#LICS_20}).</p>
 *
 * <p>Classes containing 'Asymmetric' in their name implement the supporting material for the
 * LDBA-construction described in {@value owl.Bibliography#CAV_16_CITEKEY}
 * ({@link owl.Bibliography#CAV_16}).</p>
 */
@EverythingIsNonnullByDefault
package owl.translations.mastertheorem;

import owl.util.annotation.EverythingIsNonnullByDefault;