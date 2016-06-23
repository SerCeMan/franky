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

#include <jvmti.h>
#include <iostream>
#include "proto/protocol.pb.h"


#define MAX_FRAMES     64
#define MAX_CALLTRACES 32768

#define DEFAULT_INTERVAL       10
#define DEFAULT_TRACES_TO_DUMP 500


typedef struct {
    jint bci;
    jmethodID method_id;
} ASGCT_CallFrame;

typedef struct {
    JNIEnv *env;
    jint num_frames;
    ASGCT_CallFrame *frames;
} ASGCT_CallTrace;


class MethodName {
private:
    char *_name;
    char *_sig;
    char *_class_sig;

public:
    MethodName(jmethodID method);

    ~MethodName();

    char *holder() { return _class_sig + 1; }

    char *name() { return _name; }

    char *signature() { return _sig; }
};

class CallTraceSample {
private:
    int _call_count;
    int _num_frames;
    ASGCT_CallFrame _frames[MAX_FRAMES];

    void assign(ASGCT_CallTrace *trace);

public:
    static int comparator(const void *s1, const void *s2) {
        return ((CallTraceSample *) s2)->_call_count - ((CallTraceSample *) s1)->_call_count;
    }

    friend class Profiler;
};

class MethodSample {
private:
    int _call_count;
    jmethodID _method;

public:
    static int comparator(const void *s1, const void *s2) {
        return ((MethodSample *) s2)->_call_count - ((MethodSample *) s1)->_call_count;
    }

    friend class Profiler;
};

class Profiler {
private:
    bool _running;
    int _calls_total;
    int _calls_non_java;
    int _calls_gc;
    int _calls_deopt;
    int _calls_unknown;
    uint64_t _hashes[MAX_CALLTRACES];
    CallTraceSample _traces[MAX_CALLTRACES];
    MethodSample _methods[MAX_CALLTRACES];

    // network
    uint16_t portno;
    int sockfd;
    struct hostent *server;

    uint64_t hashCallTrace(ASGCT_CallTrace *trace);

    void storeCallTrace(ASGCT_CallTrace *trace);

    uint64_t hashMethod(jmethodID method);

    void storeMethod(jmethodID method);

    void setTimer(long sec, long usec);

    void readRequest(me::serce::franky::Request *message);

    void writeResult();

    void saveMethods(me::serce::franky::ProfilingInfo *info);

    void saveCallTraces(me::serce::franky::ProfilingInfo *info);

public:

    static Profiler _instance;

    Profiler() : _running(false) { }

    bool is_running() { return _running; }

    void start(long interval);

    void stop();

    void recordSample(void *ucontext);

    void init(int port);
};


extern "C" JNIIMPORT void JNICALL
        AsyncGetCallTrace(ASGCT_CallTrace *trace, jint depth, void *ucontext);
