#include "owl-automaton.h"
#include "owl-private.h"

namespace owl {
    Automaton::Automaton(JNIEnv *env, jobject handle) :
            ManagedJObject(env, "owl/jni/JniAutomaton", handle),
            acceptanceID(get_methodID(env, clazz, "acceptance", "()I")),
            acceptanceSetCountID(get_methodID(env, clazz, "acceptanceSetCount", "()I")),
            edgesID(get_methodID(env, clazz, "edges", "(I)[I")),
            qualityScoreID(get_methodID(env, clazz, "qualityScore", "(II)D")) {}

    Automaton::Automaton(Automaton &&automaton) noexcept :
            ManagedJObject(std::move(automaton)),
            acceptanceID(automaton.acceptanceID),
            acceptanceSetCountID(automaton.acceptanceSetCountID),
            edgesID(automaton.edgesID),
            qualityScoreID(automaton.qualityScoreID) {}

    Acceptance Automaton::acceptance() const {
        return Acceptance(call_int_method<>(env, handle, acceptanceID));
    }

    int Automaton::acceptance_set_count() const {
        return call_int_method<>(env, handle, acceptanceSetCountID);
    }

    EdgeTree Automaton::edges(int state) const {
        auto result = call_method<jintArray>(env, handle, edgesID, state);

        // Provide an array to copy into...
        jsize length = env->GetArrayLength(result);
        std::vector<int32_t> edges = std::vector<int32_t>(static_cast<unsigned long>(length));
        env->GetIntArrayRegion(result, 0, length, &edges[0]);

        deref(env, result);
        return edges;
    }

    double Automaton::quality_score(int successor, int colour) const {
        return call_double_method<>(env, handle, qualityScoreID, successor, colour);
    }

    std::vector<Automaton> EmersonLeiAutomaton::automata() {
        return copy_from_java(env, get_object_field<jobject>(env, clazz, handle, "automata", "Ljava/util/List;"));
    }

    std::unique_ptr<LabelledTree<Tag, Reference>> EmersonLeiAutomaton::structure() {
        return copy_from_java(env, get_object_field<jobject>(env, clazz, handle, "structure", "Lowl/collections/LabelledTree;"));
    }
}
