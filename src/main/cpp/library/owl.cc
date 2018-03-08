#include "owl.h"
#include "owl-private.h"

namespace owl {
    FormulaFactory OwlThread::createFormulaFactory() const {
        return FormulaFactory(env);
    }

    FormulaRewriter OwlThread::createFormulaRewriter() const {
        return FormulaRewriter(env);
    }

    EmersonLeiAutomaton OwlThread::createAutomaton(const Formula &formula, bool simplify, bool monolithic, SafetySplitting safety_splitting, bool on_the_fly) const {
        jclass clazz = lookup_class(env, "owl/jni/JniEmersonLeiAutomaton");
        jmethodID split_method = get_static_methodID(env, clazz, "of",
                                                     "(Lowl/ltl/Formula;ZZIZ)Lowl/jni/JniEmersonLeiAutomaton;");
        auto java_tree = call_static_method<jobject, jobject, jboolean, jboolean, jint, jboolean>
                (env, clazz, split_method, formula.handle, static_cast<jboolean>(simplify), static_cast<jboolean>(monolithic), static_cast<jint>(safety_splitting), static_cast<jboolean>(on_the_fly));

        deref(env, clazz);
        return copy_from_java(env, java_tree);
    }

    Formula OwlThread::adoptFormula(const Formula &formula) const {
        return Formula(env, ref(env, formula.handle));
    }

    Automaton OwlThread::adoptAutomaton(const Automaton &automaton) const {
        return Automaton(env, automaton.handle);
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