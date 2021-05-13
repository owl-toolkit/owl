#include "../headers/owlsylvan.h"
#include <sylvan.h>
#include <stdint.h>
#include <stddef.h>
#include <pthread.h>

pthread_cond_t signal_nodes_retrieved = PTHREAD_COND_INITIALIZER;
pthread_cond_t signal_nodes_requested = PTHREAD_COND_INITIALIZER;
pthread_cond_t signal_exchange_loop_ready = PTHREAD_COND_INITIALIZER;
pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

volatile int nodes_retrieved = 1;
volatile int exchange_loop_ready = 0;
owl_sylvan_protected_nodes_list protected_nodes_list;

VOID_TASK_0(owl_sylvan_gc_mark) {
    pthread_mutex_lock(&lock);
    while (! exchange_loop_ready) {
        pthread_cond_wait(&signal_exchange_loop_ready, &lock);
    }
    nodes_retrieved = 0;
    pthread_cond_broadcast(&signal_nodes_requested);
    while (!nodes_retrieved) {
        pthread_cond_wait(&signal_nodes_retrieved, &lock);
    }
    for (int i = 0; i < protected_nodes_list.size; i++) {
        SPAWN(mtbdd_gc_mark_rec, *(protected_nodes_list.list + i));
    }
    for (int i = 0; i < protected_nodes_list.size; i++) {
        SYNC(mtbdd_gc_mark_rec);
    }
    pthread_mutex_unlock(&lock);
}

void owl_sylvan_init() {
    lace_start(6, 1000000);
    sylvan_set_limits(8*1024*1024*1024UL, 0, 10);
    sylvan_init_package();
    sylvan_init_mtbdd();

    sylvan_gc_add_mark(TASK(owl_sylvan_gc_mark));
}

void owl_sylvan_exit() {
    sylvan_quit();
    lace_stop();
}

void owl_sylvan_exchange_loop(void* isolate_thread) {
    while(1) {
        pthread_mutex_lock(&lock);
        exchange_loop_ready = 1;
        pthread_cond_broadcast(&signal_exchange_loop_ready);
        while(nodes_retrieved) {
            pthread_cond_wait(&signal_nodes_requested, &lock);
        }
        owl_sylvan_get_referenced_nodes(isolate_thread, &protected_nodes_list);
        nodes_retrieved=1;
        pthread_cond_broadcast(&signal_nodes_retrieved);
        pthread_mutex_unlock(&lock);
    }
}

uint64_t owl_sylvan_true() {
    return mtbdd_true;
}

uint64_t owl_sylvan_false() {
    return mtbdd_false;
}

uint32_t owl_sylvan_getvar(uint64_t bdd) {
    return mtbdd_getvar(bdd);
}

uint64_t owl_sylvan_gethigh(uint64_t bdd) {
    return mtbdd_gethigh(bdd);
}

uint64_t owl_sylvan_getlow(uint64_t bdd) {
    return mtbdd_getlow(bdd);
}

uint64_t owl_sylvan_var(uint32_t var) {
    return sylvan_ithvar(var);
}

uint64_t owl_sylvan_nvar(uint32_t var) {
    return sylvan_nithvar(var);
}

uint64_t owl_sylvan_not(uint64_t bdd) {
    return sylvan_not(bdd);
}

// Lace tasks
uint64_t owl_sylvan_ite(uint64_t i, uint64_t t, uint64_t e) {
    return sylvan_ite(i, t, e);
}

uint64_t owl_sylvan_and(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_and(bdd1, bdd2);
}

uint64_t owl_sylvan_or(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_or(bdd1, bdd2);
}

uint64_t owl_sylvan_nand(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_nand(bdd1, bdd2);
}

uint64_t owl_sylvan_nor(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_nor(bdd1, bdd2);
}

uint64_t owl_sylvan_imp(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_imp(bdd1, bdd2);
}

uint64_t owl_sylvan_xor(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_xor(bdd1, bdd2);
}

uint64_t owl_sylvan_equiv(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_equiv(bdd1, bdd2);
}

uint64_t owl_sylvan_diff(uint64_t bdd1, uint64_t bdd2) {
    return sylvan_diff(bdd1, bdd2);
}

uint64_t owl_sylvan_varset_from_array(uint32_t* arr, size_t len) {
    return mtbdd_set_from_array(arr, len);
}

uint64_t owl_sylvan_sat_one_bdd(uint64_t bdd) {
    return sylvan_sat_one_bdd(bdd);
}

size_t owl_sylvan_set_count(uint64_t vars) {
    return mtbdd_set_count(vars);
}

uint64_t owl_sylvan_exists(uint64_t bdd, uint64_t vars) {
    return sylvan_exists(bdd, vars);
}

uint64_t owl_sylvan_support(uint64_t bdd) {
    return sylvan_support(bdd);
}

uint64_t owl_sylvan_map_add(uint64_t map, uint32_t var, uint64_t bdd) {
    return mtbdd_map_add(map, var, bdd);
}

uint64_t owl_sylvan_compose(uint64_t bdd, uint64_t map) {
    return sylvan_compose(bdd, map);
}

double owl_sylvan_satcount(uint64_t bdd, size_t nrOfVars) {
    return mtbdd_satcount(bdd, nrOfVars);
}

size_t owl_sylvan_nodecount(uint64_t bdd) {
    return mtbdd_nodecount(bdd);
}