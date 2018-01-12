#include "owl.h"
#include "owl-private.h"

namespace owl {
    OwlThread::~OwlThread() {
        // Detach from JavaVM.
        vm->DetachCurrentThread();
    }

    FormulaFactory OwlThread::createFormulaFactory() const {
        return FormulaFactory(env);
    }

    FormulaRewriter OwlThread::createFormulaRewriter() const {
        return FormulaRewriter(env);
    }

    Automaton OwlThread::createAutomaton(const Formula &formula, const bool on_the_fly) const {
        return Automaton(env, formula, on_the_fly);
    }

    LabelledTree<Tag, Automaton> OwlThread::createAutomatonTree(const Formula &formula, bool on_the_fly,
                                                           SafetySplitting safety_splitting) const {
        jclass splitter_class = lookup_class(env, "owl/jni/Splitter");
        jmethodID split_method = get_static_methodID(env, splitter_class, "split",
                                                     "(Lowl/ltl/Formula;ZI)Lowl/collections/LabelledTree;");
        auto java_tree = call_static_method<jobject, jobject, jboolean, jint>(env,
                                                                              splitter_class,
                                                                              split_method,
                                                                              formula.formula,
                                                                              static_cast<jboolean>(on_the_fly),
                                                                              static_cast<jint>(safety_splitting));
        deref(env, splitter_class);
        return copy_from_java(env, java_tree);
    }

    Formula OwlThread::adoptFormula(const Formula &formula) const {
        return Formula(env, ref(env, formula.formula));
    }

    Automaton OwlThread::adoptAutomaton(const Automaton &automaton) const {
        return Automaton(env, ref(env, automaton.handle));
    }

    OwlJavaVM::OwlJavaVM(const char *classpath, bool debug) {
        JavaVMInitArgs vm_args = JavaVMInitArgs();
        JavaVMOption *options;
        std::string string_classpath = std::string(classpath);

        if (debug) {
            options = new JavaVMOption[2];
            options[0].optionString = const_cast<char *>(string_classpath.c_str());
            options[1].optionString = const_cast<char *>("-Xcheck:jni");
            vm_args.nOptions = 2;
        } else {
            options = new JavaVMOption[1];
            options[0].optionString = const_cast<char *>(string_classpath.c_str());
            vm_args.nOptions = 1;
        }

        vm_args.version = JNI_VERSION_9;
        vm_args.options = options;
        vm_args.ignoreUnrecognized = JNI_FALSE;

        JNIEnv* env;
        // Construct a VM
        jint res = JNI_CreateJavaVM(&vm, (void **)&env, &vm_args);

        if (res) {
            throw std::runtime_error("Failed to create JavaVM.");
        }
    }

    OwlJavaVM::~OwlJavaVM() {
        // Shutdown the JavaVM.
        vm->DestroyJavaVM();
    }

    OwlThread OwlJavaVM::attachCurrentThread() const {
        JNIEnv* env;
        jint res = vm->AttachCurrentThread((void **) &env, nullptr);

        if (res) {
            throw std::runtime_error("Failed to attach current thread to JavaVM.");
        }

        return OwlThread(vm, env);
    }
}