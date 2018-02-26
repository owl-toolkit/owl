#include "owl-automaton.h"
#include "owl-private.h"

namespace owl {
    Automaton::Automaton(JNIEnv *env, jobject handle) :
            ManagedJObject(env, "owl/jni/JniAutomaton", handle),
            acceptanceID(get_methodID(env, clazz, "acceptance", "()I")),
            acceptanceSetCountID(get_methodID(env, clazz, "acceptanceSetCount", "()I")),
            edgesID(get_methodID(env, clazz, "edges", "(I)[I")),
            successorsID(get_methodID(env, clazz, "successors", "(I)[I")) {}

    Automaton::Automaton(Automaton &&automaton) noexcept :
            ManagedJObject(std::move(automaton)),
            acceptanceID(automaton.acceptanceID),
            acceptanceSetCountID(automaton.acceptanceSetCountID),
            edgesID(automaton.edgesID),
            successorsID(automaton.successorsID) {}

    Acceptance Automaton::acceptance() const {
        return Acceptance(call_int_method<>(env, handle, acceptanceID));
    }

    int Automaton::acceptance_set_count() const {
        return call_int_method<>(env, handle, acceptanceSetCountID);
    }

    std::vector<Edge> Automaton::edges(int state) const {
        auto result = call_method<jintArray>(env, handle, edgesID, state);

        // Provide an array to copy into...
        jsize length = env->GetArrayLength(result);
        std::vector<Edge> edges = std::vector<Edge>((size_t) length / 2);
        env->GetIntArrayRegion(result, 0, length, reinterpret_cast<jint *>(&edges[0]));

        deref(env, result);
        return edges;
    }

    std::vector<int> Automaton::successors(int state) const {
        auto result = call_method<jintArray>(env, handle, successorsID, state);

        // Provide an array to copy into...
        jsize length = env->GetArrayLength(result);
        std::vector<int> successors = std::vector<int>((size_t) length);
        env->GetIntArrayRegion(result, 0, length, &successors[0]);

        deref(env, result);
        return successors;
    }

    std::vector<Automaton> EmersonLeiAutomaton::automata() {
        return copy_from_java(env, get_object_field<jobject>(env, clazz, handle, "automata", "Ljava/util/List;"));
    }

    std::unique_ptr<LabelledTree<Tag, Reference>> EmersonLeiAutomaton::structure() {
        return copy_from_java(env, get_object_field<jobject>(env, clazz, handle, "structure", "Lowl/collections/LabelledTree;"));
    }
}