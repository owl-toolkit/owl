#ifndef __OWLTYPES_H
#define __OWLTYPES_H

#define OWL_INITIAL_STATE 0
#define OWL_REJECTING_SINK -1
#define OWL_ACCEPTING_SINK -2

#define OWL_SEPARATOR -232323
#define OWL_FEATURE_SEPARATOR -424242

typedef enum {
  CONSTANT_TRUE,
  CONSTANT_FALSE,
  USED,
  UNUSED
} atomic_proposition_status_t;

typedef enum {
  BUCHI,
  CO_BUCHI,
  PARITY_MAX_EVEN,
  PARITY_MAX_ODD,
  PARITY_MIN_EVEN,
  PARITY_MIN_ODD
} acceptance_t;

typedef enum {
  PERMUTATION,
  ROUND_ROBIN_COUNTER,
  TEMPORAL_OPERATORS_PROFILE
} feature_type_t;

typedef struct {
  int *elements;
  int size;
} vector_int_t;

typedef struct {
  double *elements;
  int size;
} vector_double_t;

typedef enum {
  // Simplify the formula before applying the translation
  SIMPLIFY_FORMULA,

  // Simplify the automaton, e.g. remove non-accepting states.
  // This explores the complete automaton.
  SIMPLIFY_AUTOMATON,

  // Use a portfolio of simpler constructions for fragments of LTL.
  USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS,

  // Translate the formula and the negation of the formula to DPWs and
  // return the smaller one.
  X_DPA_USE_COMPLEMENT,

  // Use the dual normalisation procedure for the construction of DRWs.
  X_DRA_NORMAL_FORM_USE_DUAL,
} ltl_translation_option_t;

// Translations are named after corresponding publications.
typedef enum {
  SEJK16_EKRS17,
  EKS20_EKRS17,
  UNPUBLISHED_ZIELONKA,
  SMALLEST_AUTOMATON,
  DEFAULT // Work-around for native-image bug. Do not use this value!
} ltl_to_dpa_translation_t;

// State layout for 'UNPUBLISHED_ZIELONKA'
typedef struct {
  int key;
  vector_int_t *all_profile;
  vector_int_t *rejecting_profile;
  int disambiguation;
} zielonka_normal_form_state_state_map_entry_t;

// State layout for 'UNPUBLISHED_ZIELONKA'
typedef struct {
  int state_formula;
  zielonka_normal_form_state_state_map_entry_t *state_map;
  int state_map_size;
  vector_int_t *round_robin_counters;
  vector_int_t *zielonka_path;
} zielonka_normal_form_state_t;


#endif
