#include "owl.h"
#include "owl-private.h"

namespace owl {
    Owl::Owl(const char* classpath, bool debug) : debug(debug) {
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

        // Construct a VM
        jint res = JNI_CreateJavaVM(&vm, (void **)&env, &vm_args);

        if (res) {
            throw std::runtime_error("Failed to create JavaVM.");
        }
    }

    Owl::~Owl() {
        // Shutdown the VM.
        vm->DestroyJavaVM();
    }

    FormulaFactory Owl::createFormulaFactory() const {
        return FormulaFactory(env);
    }

    FormulaRewriter Owl::createFormulaRewriter() const {
        return FormulaRewriter(env);
    }

    Automaton Owl::createAutomaton(const Formula &formula, const bool on_the_fly) const {
        return Automaton(env, formula, on_the_fly);
    }

    LabelledTree <Tag, Automaton> Owl::createAutomatonTree(const Formula &formula, bool on_the_fly,
                                                           SafetySplitting safety_splitting) const {
        jclass splitter_class = lookup_class(env, "owl/jni/Splitter");
        jmethodID split_method = get_static_methodID(env, splitter_class, "split", "(Lowl/ltl/Formula;ZI)Lowl/collections/LabelledTree;");
        auto java_tree = call_static_method<jobject, jobject, jboolean, jint>(env,
                                                                                  splitter_class,
                                                                                  split_method,
                                                                                  formula.formula,
                                                                                  static_cast<jboolean>(on_the_fly),
                                                                                  static_cast<jint>(safety_splitting));
        deref(env, splitter_class);
        return copy_from_java(env, java_tree);
    }
}