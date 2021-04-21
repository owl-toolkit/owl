#ifndef __OWLSYLVAN_H
#define __OWLSYLVAN_H
#include <stdint.h>
#include <stddef.h>

void owl_sylvan_init();
void owl_sylvan_exit();

uint64_t owl_sylvan_true();
uint64_t owl_sylvan_false();
uint64_t owl_sylvan_var(uint32_t var);
uint64_t owl_sylvan_nvar(uint32_t var);

uint64_t owl_sylvan_not(uint64_t bdd);

uint32_t owl_sylvan_getvar(uint64_t bdd);
uint64_t owl_sylvan_gethigh(uint64_t bdd);
uint64_t owl_sylvan_getlow(uint64_t bdd);

uint64_t owl_sylvan_ite(uint64_t i, uint64_t t, uint64_t e);
uint64_t owl_sylvan_and(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_or(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_nand(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_nor(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_imp(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_xor(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_equiv(uint64_t bdd1, uint64_t bdd2);
uint64_t owl_sylvan_diff(uint64_t bdd1, uint64_t bdd2);

uint64_t owl_sylvan_exists(uint64_t bdd, uint64_t vars);

double owl_sylvan_satcount(uint64_t bdd, size_t nrOfVars);

uint64_t owl_sylvan_varset_from_array(uint32_t* arr, size_t len);

uint64_t owl_sylvan_sat_one_bdd(uint64_t bdd);

size_t owl_sylvan_set_count(uint64_t vars);

uint64_t owl_sylvan_support(uint64_t bdd);

uint64_t owl_sylvan_map_add(uint64_t map, uint32_t var, uint64_t bdd);

uint64_t owl_sylvan_compose(uint64_t bdd, uint64_t map);

typedef struct {
    volatile uint32_t size;
    uint64_t* volatile list;
} owl_sylvan_protected_nodes_list;

void owl_sylvan_get_referenced_nodes(void* isolate_thread, owl_sylvan_protected_nodes_list* node_list);

void owl_sylvan_exchange_loop(void* isolate_thread);

#endif