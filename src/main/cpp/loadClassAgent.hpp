#include "jvmti.h"

class AgentException {
public:
  AgentException(jvmtiError err) {
    m_error = err;
  }

  const char* what() const throw() {
    return "AgentException";
  }

  jvmtiError ErrCode() const throw() {
    return m_error;
  }

private:
  jvmtiError m_error;
};


class LoadClassAgent {
public:
  LoadClassAgent() {}

  ~LoadClassAgent();

  void Init(JavaVM *vm) const;

  void RegisterEvent() const;

  static void JNICALL HandleLoadClass(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread, jobject cld);

private:
  static void CheckException(jvmtiError error) {
    if (error != JVMTI_ERROR_NONE) {
      throw AgentException(error);
    }
  }

  static jvmtiEnv * m_jvmti;
};
