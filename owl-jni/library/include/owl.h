#include <jni.h>
#include <vector>

#include "owl-automaton.h"
#include "owl-formula.h"

#ifndef OWL_H
#define OWL_H

namespace owl {
    class Owl {
    private:
        const bool debug;
        JNIEnv* env;
        JavaVM* vm;

    public:
        Owl(const char* classpath, bool debug);
        Owl(const Owl &owl) = delete;
        Owl(const Owl &&owl) noexcept : debug(owl.debug), env(owl.env), vm(owl.vm) {};
        ~Owl();

        FormulaFactory createFormulaFactory() const;
        FormulaRewriter createFormulaRewriter() const;
        DPA createDPA(const Formula &formula) const;
    };
}

#endif // OWL_H