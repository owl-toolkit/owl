#pragma once

#include <jni.h>

namespace owl {
    class ManagedJObject {
    protected:
        JNIEnv* env;
        jclass clazz;
        jobject handle;

        ManagedJObject(JNIEnv* env, jobject handle);
        ManagedJObject(JNIEnv* env, const char* class_name, jobject handle);
        ManagedJObject(const ManagedJObject &object);
        ManagedJObject(ManagedJObject &&object) noexcept;
        ~ManagedJObject();

        friend class OwlThread;
        friend class FormulaFactory;
        friend class FormulaRewriter;
    };
}