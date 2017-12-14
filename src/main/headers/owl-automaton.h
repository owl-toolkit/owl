#include <jni.h>
#include <vector>

#include "owl-formula.h"

#ifndef OWL_AUTOMATON_H
#define OWL_AUTOMATON_H

namespace owl {
    struct Edge;

    enum Acceptance {
        BUCHI, CO_BUCHI, CO_SAFETY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN, PARITY_MIN_ODD, SAFETY
    };

    class Automaton {
    private:
        JNIEnv* env{nullptr};
        jobject handle{nullptr};

        // Method IDs.
        jmethodID alphabet_mappingID{};
        jmethodID acceptanceID{};
        jmethodID edgesID{};
        jmethodID successorsID{};

        Automaton(JNIEnv* env, const Formula& formula);
        Automaton(JNIEnv* env, jobject handle);

        void bind_methods(jclass const &clazz);

        friend class Owl;
        friend class copy_from_java;

    public:
        Automaton() : env(nullptr) {};
        Automaton(const Automaton &automaton);
        ~Automaton();

        std::map<int, int> alphabet_mapping() const;
        Acceptance acceptance() const;
        std::vector<Edge> edges(int state) const;
        std::vector<int> successors(int state) const;
    };

    struct Edge {
        int successor;
        int colour;

        Edge() : successor(-1), colour(-1) {};
        Edge(int _successor, int _colour) : successor(_successor), colour(_colour) {};
    };
}

#endif // OWL_AUTOMATON_H