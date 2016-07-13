/*
 * Copyright 2016 Andrei Pangin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string.h>
#include <jvmti.h>
#include <jvmticmlr.h>
#include "profiler.h"
#include "vmEntry.h"

JavaVM *VM::_vm;
jvmtiEnv *VM::_jvmti;
std::ofstream VM::fout;


jint VM::init(JavaVM *vm) {
    _vm = vm;

    fout.open("agent.log");
    fout << "Agent loaded" << std::endl;

    jint result;
    result = _vm->GetEnv((void **) &_jvmti, JVMTI_VERSION_1_0);
    if (result != JNI_OK || jvmti == NULL) {
        printf("ERROR: Unable to access JVMTI Version 1 (0x%x),"
                       " is your J2SE a 1.5 or newer version? JNIEnv's GetEnv() returned %d which is wrong.\n",
               JVMTI_VERSION_1, (int) result);
        perror("AAAR");
        return result;
    }

    jvmtiError error;
    if ((error = set_capabilities()) != JVMTI_ERROR_NONE) {
        perror("AAAR2");
        return error;
    }
    if ((error = register_all_callback_functions()) != JVMTI_ERROR_NONE) {
        perror("AAAR3");
        return error;
    }

    _jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
    _jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD, NULL);
    _jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    _jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_LOAD, NULL);

    return JNI_OK;
}

jvmtiError VM::set_capabilities() {
    jvmtiCapabilities capabilities = {0};
    capabilities.can_generate_all_class_hook_events = 1;
    capabilities.can_get_bytecodes = 1;
    capabilities.can_get_constant_pool = 1;
    capabilities.can_get_source_file_name = 1;
    capabilities.can_get_line_numbers = 1;
    capabilities.can_generate_compiled_method_load_events = 1;
    capabilities.can_redefine_classes = 1;
    capabilities.can_redefine_any_class = 1;
    return _jvmti->AddCapabilities(&capabilities);
}

jvmtiError VM::register_all_callback_functions() {
    jvmtiEventCallbacks callbacks = {0};
    callbacks.VMInit = VMInit;
    callbacks.ClassLoad = ClassLoad;
    callbacks.ClassPrepare = ClassPrepare;
    callbacks.CompiledMethodLoad = CompiledMethodLoad;
    return _jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
}


void VM::loadMethodIDs(jvmtiEnv *jvmti, jclass klass) {
    jint method_count;
    jmethodID *methods;
    if (jvmti->GetClassMethods(klass, &method_count, &methods) == 0) {
        jvmti->Deallocate((unsigned char *) methods);
    }
}

void VM::loadAllMethodIDs(jvmtiEnv *jvmti) {
    jint class_count;
    jclass *classes;
    if (jvmti->GetLoadedClasses(&class_count, &classes) == 0) {
        for (int i = 0; i < class_count; i++) {
            loadMethodIDs(jvmti, classes[i]);
        }
        jvmti->Deallocate((unsigned char *) classes);
    }
}

void VM::close() {
    fout << "Closing VM" << std::endl;
    fout.close();
}


/**
 * Also needed to enable DebugNonSafepoints info by default
 */
void VM::CompiledMethodLoad(jvmtiEnv *jvmti, jmethodID method, jint code_size, const void *code_addr, jint map_length,
                            const jvmtiAddrLocationMap *map, const void *compile_info) {
    fout << "method compiled size=" << code_size << " addr=" << code_addr << std::endl;
    auto &compiles_info = VM::get().compiles_info;
    CompileInfo *info = compiles_info[method];
    if (info == nullptr) {
        compiles_info[method] = new CompileInfo(code_size, map_length, code_addr, compile_info);
    }

    /*if (compile_info != nullptr) {
        const jvmtiCompiledMethodLoadRecordHeader *curr = static_cast<const jvmtiCompiledMethodLoadRecordHeader *>(compile_info);
        fout << "Compiling info\n";
        while (curr != nullptr) {
            switch (curr->kind) {
                case JVMTI_CMLR_DUMMY: {
                    const jvmtiCompiledMethodLoadDummyRecord *dr = reinterpret_cast<const jvmtiCompiledMethodLoadDummyRecord *>(curr);
                    fout << "Dummy record" << dr->message << std::endl;
                    break;
                }
                case JVMTI_CMLR_INLINE_INFO: {
                    const jvmtiCompiledMethodLoadInlineRecord *ir = reinterpret_cast<const jvmtiCompiledMethodLoadInlineRecord *>(curr);
                    fout << "nInline Record numpcs=" << ir->numpcs << std::endl;
                    if (ir->pcinfo != nullptr) {
                        for (int i = 0; i < ir->numpcs; i++) {
                            PCStackInfo pcrecord = (ir->pcinfo[i]);

                            fout << "---------------------------------------\n";
                            fout << "PC Descriptor: i(" << i << ") (pc=" << (pcrecord.pc) << ")" << std::endl;

//                            PrintStackFrames(&pcrecord, jvmti, fp);

                        }
                        break;
                    }
                }
                default: {
                    fout << "Unrecognized Record: kind=" << curr->kind << std::endl;
                    break;
                }
            }
            curr = curr->next;
        }
    }*/

}


extern "C"
JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    return VM::init(vm);
}

extern "C"
JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
    VM::attach(vm);
    int port = atoi(options);
    Profiler::_instance.init(port);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    VM::attach(vm);
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm) {
    VM::close();
}
