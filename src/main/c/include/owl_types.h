/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

#include <assert.h>

#ifndef SRC_MAIN_C_INCLUDE_OWL_TYPES_H_
#define SRC_MAIN_C_INCLUDE_OWL_TYPES_H_

// Typed vectors

typedef struct {
  int *elements;
  int size;
} int_vector;

typedef struct {
  double *elements;
  int size;
} double_vector;

// LTL Simplifier

typedef enum {
  OWL_AP_CONSTANT_TRUE,
  OWL_AP_CONSTANT_FALSE,
  OWL_AP_USED,
  OWL_AP_UNUSED
} owl_atomic_proposition_status;

// LTL Translations

#define OWL_INITIAL_STATE (0)
#define OWL_EDGE_DELIMITER (-1)
#define OWL_EDGE_GROUP_DELIMITER (-2)

// Acceptance conditions of automata.
// TODO(sickert): add remaining acceptance conditions (EL, GR, ...).
typedef enum {
  OWL_BUCHI,
  OWL_CO_BUCHI,
  OWL_PARITY_MAX_EVEN,
  OWL_PARITY_MAX_ODD,
  OWL_PARITY_MIN_EVEN,
  OWL_PARITY_MIN_ODD,
  OWL_RABIN
} owl_acceptance_condition;

#define OWL_ACCEPTANCE_CONDITION_NAME /**/         \
  (char const* const[OWL_RABIN + 1]) {             \
    [OWL_BUCHI] = "Buchi",                         \
    [OWL_CO_BUCHI] = "co-Buchi",                   \
    [OWL_PARITY_MAX_EVEN] = "parity (max, even)",  \
    [OWL_PARITY_MAX_ODD] = "parity (max, odd)",    \
    [OWL_PARITY_MIN_EVEN] = "parity (min, even)",  \
    [OWL_PARITY_MIN_ODD] = "parity (min, odd)",    \
    [OWL_RABIN] = "Rabin",                         \
  }                                                \

// LTL to DPA translations. Names are derived from publications.
typedef enum {
  OWL_LTL_TO_DPA_SEJK16_EKRS17 = 10,
  OWL_LTL_TO_DPA_EKS20_EKRS17,
  OWL_LTL_TO_DPA_SYMBOLIC_SE20_BKS10,
  OWL_LTL_TO_DPA_SLM21,
  OWL_LTL_TO_DPA_SMALLEST_AUTOMATON
} owl_ltl_to_dpa_translation;

// LTL to DRA translations. Names are derived from publications.
typedef enum {
  OWL_LTL_TO_DRA_EKS16 = 20,
  OWL_LTL_TO_DRA_EKS20,
  OWL_LTL_TO_DRA_SE20,
  OWL_LTL_TO_DRA_SMALLEST_AUTOMATON
} owl_ltl_to_dra_translation;

static_assert(
  ((int) OWL_LTL_TO_DPA_SMALLEST_AUTOMATON) < ((int) OWL_LTL_TO_DRA_EKS16),
  "Overlapping enum definitions.");

typedef enum {
  // Simplify the formula before applying the translation
  OWL_SIMPLIFY_FORMULA,

  // Simplify the automaton, e.g. remove non-accepting states.
  // This explores the complete automaton.
  OWL_SIMPLIFY_AUTOMATON,

  // Ensures that the transition relation of the automaton is complete.
  OWL_COMPLETE,

  // Use a portfolio of simpler constructions for fragments of LTL.
  OWL_USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS,

  // Translate the formula and the negation of the formula to DPWs and
  // return the smaller one.
  OWL_X_DPA_USE_COMPLEMENT,

  // Use the dual normalisation procedure for the construction of DRWs.
  OWL_X_DRA_NORMAL_FORM_USE_DUAL,
} owl_ltl_translation_option;

// State layout for 'UNPUBLISHED_ZIELONKA'
typedef struct {
  int key;
  int_vector *all_profile;
  int_vector *rejecting_profile;
  int disambiguation;
} owl_zielonka_normal_form_state_state_map_entry;

// State layout for 'UNPUBLISHED_ZIELONKA'
typedef struct {
  int state_formula;
  owl_zielonka_normal_form_state_state_map_entry *state_map;
  int state_map_size;
  int_vector *round_robin_counters;
  int_vector *zielonka_path;
} owl_zielonka_normal_form_state;

// Workaround for GraalVM native-image bug.
// TODO(sickert): remove workaround.
#define DEFAULT (4242)

#endif  // SRC_MAIN_C_INCLUDE_OWL_TYPES_H_
