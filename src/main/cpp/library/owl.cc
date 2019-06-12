#include "owl.h"
#include "owl-private.h"

#include <sstream>
#include <string>

namespace owl {
    FormulaFactory OwlThread::createFormulaFactory() const {
        return FormulaFactory(env);
    }

    DecomposedDPA OwlThread::createAutomaton(const Formula &formula, bool simplify, bool monolithic, int firstOutputVariable) const {
        jclass clazz = lookup_class(env, "owl/cinterface/DecomposedDPA");
        jmethodID split_method = get_static_methodID(env, clazz, "of", "(Lowl/ltl/Formula;ZZI)Lowl/cinterface/DecomposedDPA;");
        auto java_tree = call_static_method<jobject, jobject, jboolean, jboolean, jint>(env, clazz, split_method, formula.handle, static_cast<jboolean>(simplify), static_cast<jboolean>(monolithic), static_cast<jint>(firstOutputVariable));

        deref(env, clazz);
        return copy_from_java(env, java_tree);
    }

    Formula OwlThread::adoptFormula(const Formula &formula) const {
        return Formula(env, ref(env, formula.handle));
    }

    Automaton OwlThread::adoptAutomaton(const Automaton &automaton) const {
        return Automaton(env, automaton.handle);
    }

    OwlJavaVM::OwlJavaVM(const char *classpath, bool debug, int initial_heap_size_gb, int max_heap_size_gb, bool aggressive_heap_optimisation) {
        JavaVMInitArgs vm_args = JavaVMInitArgs();
        JavaVMOption *options;

        std::vector<std::string> args = std::vector<std::string>();
        args.emplace_back(std::string(classpath));

        if (debug) {
            args.emplace_back(std::string("-Xcheck:jni"));
            args.emplace_back(std::string("-enableassertions"));
        } else {
            args.emplace_back(std::string("-disableassertions"));
        }

        if (aggressive_heap_optimisation) {
            args.emplace_back(std::string("-XX:MaxHeapFreeRatio=20"));
            args.emplace_back(std::string("-XX:MinHeapFreeRatio=10"));
            args.emplace_back(std::string("-XX:-ShrinkHeapInSteps"));
        }

        if (initial_heap_size_gb > 0) {
            std::stringstream ss;
            ss << "-XX:InitialHeapSize=" << initial_heap_size_gb << "G";
            args.emplace_back(ss.str());
        }

        if (max_heap_size_gb > 0) {
            std::stringstream ss;
            ss << "-XX:MaxHeapSize=" << max_heap_size_gb << "G";
            args.emplace_back(ss.str());
        }

        options = new JavaVMOption[args.size()];

        for (size_t i = 0; i < args.size(); i++) {
            options[i].optionString = const_cast<char *>(args[i].c_str());
        }

        vm_args.nOptions = static_cast<jint>(args.size());
        vm_args.version = JNI_VERSION_10;
        vm_args.options = options;
        vm_args.ignoreUnrecognized = JNI_FALSE;

        JNIEnv* env;
        // Construct a VM
        jint res = JNI_CreateJavaVM(&vm, (void **)&env, &vm_args);

        delete []options;
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
