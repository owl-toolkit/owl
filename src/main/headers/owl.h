#pragma once

#include <jni.h>
#include <vector>

#include "owl-automaton.h"
#include "owl-formula.h"

namespace owl {
    class OwlThread {
    private:
        JavaVM* vm;
        JNIEnv* env;

        explicit OwlThread(JavaVM* _vm, JNIEnv* _env) : vm(_vm), env(_env) {};
        friend class OwlJavaVM;

    public:
        OwlThread(const OwlThread &owl) = delete;

        OwlThread(OwlThread &&owl) noexcept : vm(owl.vm), env(owl.env) {
            owl.vm = nullptr;
            owl.env = nullptr;
        };

        ~OwlThread() {
            if (vm != nullptr) {
                // Detach thread from JVM.
                vm->DetachCurrentThread();
            }
        }

        Formula adoptFormula(const Formula &formula) const;
        Automaton adoptAutomaton(const Automaton &automaton) const;

        FormulaFactory createFormulaFactory() const;
        DecomposedDPA createAutomaton(const Formula &formula, bool simplify, bool monolithic, int firstOutputVariable) const;
    };

    class OwlJavaVM {
    private:
        JavaVM* vm;

    public:
        explicit OwlJavaVM(const char *classpath, bool debug, int initial_heap_size_gb, int max_heap_size_gb, bool aggressive_heap_optimisation);
        ~OwlJavaVM();

        OwlThread attachCurrentThread() const;
    };
}
