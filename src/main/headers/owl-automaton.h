#include <jni.h>
#include <vector>

#include "owl-formula.h"

#ifndef OWL_AUTOMATON_H
#define OWL_AUTOMATON_H

namespace owl {
    struct Edge {
        int successor;
        int colour;

        Edge(int successor, int colour) : successor(successor), colour(colour) {};
    };

    enum Parity {
        MIN_EVEN, MIN_ODD, MAX_EVEN, MAX_ODD
    };

    class DPA {
    private:
        JNIEnv* env;
        jobject handle;
        jmethodID successorsID;
        jmethodID parityID;

        DPA(JNIEnv* env, const Formula& formula);
        friend class Owl;

    public:
        DPA(const DPA &dpa);
        DPA(const DPA &&dpa) noexcept : env(dpa.env), handle(dpa.handle), successorsID(dpa.successorsID), parityID(dpa.parityID) {};
        ~DPA();

        Parity parity();
        std::vector<Edge> successors(int state);
    };
}

#endif // OWL_AUTOMATON_H