#ifndef __OWLTYPES_H
#define __OWLTYPES_H

#define OWL_INITIAL_STATE 0
#define OWL_REJECTING_SINK -1
#define OWL_ACCEPTING_SINK -2

#define OWL_SEPARATOR -232323
#define OWL_FEATURE_SEPARATOR -424242

typedef enum {
  AUTOMATON,
  BICONDITIONAL,
  CONJUNCTION,
  DISJUNCTION
} node_type_t;

typedef enum {
  REALIZABLE,
  UNREALIZABLE,
  UNKNOWN
} realizability_status_t;

typedef enum {
  CONSTANT_TRUE,
  CONSTANT_FALSE,
  USED,
  UNUSED
} atomic_proposition_status_t;

typedef enum {
  BUCHI,
  CO_BUCHI,
  CO_SAFETY,
  PARITY,
  PARITY_MAX_EVEN,
  PARITY_MAX_ODD,
  PARITY_MIN_EVEN,
  PARITY_MIN_ODD,
  SAFETY,
  WEAK,
  BOTTOM
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

#endif
