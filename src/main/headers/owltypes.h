#ifndef __OWLTYPES_H
#define __OWLTYPES_H

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

typedef struct {
  int *elements;
  int length;
} sized_int_array_t;

typedef struct {
  double *elements;
  int length;
} sized_double_array_t;

#endif
