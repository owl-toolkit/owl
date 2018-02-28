#include <jni.h>
#include <vector>

#include "owl-automaton.h"
#include "owl-formula.h"

#ifndef OWL_H
#define OWL_H

namespace owl {
    class OwlThread {
    private:
        JavaVM* vm;
        JNIEnv* env;

        explicit OwlThread(JavaVM* _vm, JNIEnv* _env) : vm(_vm), env(_env) {};
        friend class OwlJavaVM;

    public:
        OwlThread(const OwlThread &owl) = delete;
        OwlThread(const OwlThread &&owl) noexcept : vm(owl.vm), env(owl.env) {};
        ~OwlThread();

        Formula adoptFormula(const Formula &formula) const;
        Automaton adoptAutomaton(const Automaton &automaton) const;

        FormulaFactory createFormulaFactory() const;
        FormulaRewriter createFormulaRewriter() const;
        EmersonLeiAutomaton createAutomaton(const Formula &formula, bool simplify, bool monolithic, SafetySplitting safety_splitting, bool on_the_fly) const;
    };

    class OwlJavaVM {
    private:
        JavaVM* vm;

    public:
        OwlJavaVM(const char* classpath, bool debug);
        ~OwlJavaVM();

        OwlThread attachCurrentThread() const;
    };
}

#endif // OWL_H