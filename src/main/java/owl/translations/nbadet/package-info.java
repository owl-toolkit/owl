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
 * Contains the translation from non-deterministic Büchi automata to deterministic parity automata
 * described in {@value owl.Bibliography#ICALP_19_1_CITEKEY} ({@link owl.Bibliography#ICALP_19_1})
 * with optimisations from {@value owl.Bibliography#ATVA_19_CITEKEY}
 * ({@link owl.Bibliography#ATVA_19}).
 */
@EverythingIsNonnullByDefault
package owl.translations.nbadet;

import owl.util.annotation.EverythingIsNonnullByDefault;
