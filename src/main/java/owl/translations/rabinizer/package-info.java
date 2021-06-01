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
 * A translation from LTL to tDGRA.
 *
 * <p>Rabinizer is a construction to translate LTL formulas to deterministic (generalized) Rabin
 * automata, additionally providing semantic information for all states, compared to {@code
 * LTL->NBA->DRA} approaches using Safra trees, which lose any reasonable state space information in
 * the determinization process. It is the conceptual father to most other LTL translations in
 * owl.</p>
 *
 * <p>The basic idea of Rabinizer is as follows. The resulting automaton consists of two different
 * building blocks, the "master automaton" and the "monitors", which then are connected by a
 * non-trivial product construction.</p> <ul> <li>The master tracks the evolution of the formula as
 * implied by the finite prefix read so far by simple unfolding. For example, the formula {@code phi
 * = "a U b | X a"} would induce a master automaton with states {@code (phi)}, @{code a U b}, {@code
 * false}, and {@code true}. This is sufficient for formulas without a "G" operator in them (or any
 * similar temporal operator requiring infinite horizon), since a state like {@code G a} will never
 * unfold to {@code tt} but still might hold for a word.</li> <li> The monitors are designed to
 * provide this information. For each {@code G<psi>} in the given formula, a monitor is constructed
 * which tracks the validity of {@code F G<psi>}. This, of course is not the same as the original
 * sub-formula. The product construction takes care of connecting the provided information as
 * needed. </li> </ul> <p>The central idea of the product construction is the following. The state
 * of the master automaton gives us a "safety" condition - it tracks what was definitely satisfied
 * and definitely violated by the finite prefix. If we now "stabilize" to certain non-false states
 * in the master, we now use the monitors to decide the infinite time behaviour. Consider the
 * following setting. We remain in state {@code G<psi>} in the master, hence we know that no finite
 * prefix violates {@code G<psi>}. If the monitor for {@code <psi>} accepts, we also know that
 * {@code F G<psi>} holds. Together, we can deduce that the word satisfies {@code G<psi>}.</p>
 *
 * <p>This concept is encoded into the acceptance condition. We use a big disjunction of conditions
 * which allows the automaton to "guess" which G sub-formulas will accept. This is called the
 * "active set" |G|. Consider, for example, the formula {@code G a XOR G b}. There, {@code |G|
 * = {G a}} and {@code |G| = {G b}} would accept, but not {@code {G a, G b}}. The first part of each
 * condition therefore is a check if the chosen set entails the current master state, the
 * second part checks if all monitors satisfy this "promise".</p>
 *
 * <p>Unfortunately, the actual construction is more complicated as soon as nested G formulae are
 * involved. Explaining these technicalities is beyond the scope of this documentation and the
 * interested reader is referred to actual paper defining the construction.</p>
 *
 * @see owl.translations.rabinizer.RabinizerBuilder
 * @see owl.translations.rabinizer.MonitorBuilder
 */
@EverythingIsNonnullByDefault
package owl.translations.rabinizer;

import owl.util.annotation.EverythingIsNonnullByDefault;