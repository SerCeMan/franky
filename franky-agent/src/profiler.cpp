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

#include <stdio.h>
#include <stdlib.h>
#include <cstdint>
#include <string.h>
#include <signal.h>
#include <string>
#include <sys/time.h>

#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <proto/protocol.pb.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <unordered_set>
#include <thread>

#include "logging.h"
#include "profiler.h"
#include "vmEntry.h"

using namespace me::serce::franky;

Profiler Profiler::_instance;


static void sigprofHandler(int signo, siginfo_t *siginfo, void *ucontext) {
    Profiler::_instance.recordSample(ucontext);
}


MethodName::MethodName(jmethodID method) {
    jclass method_class;
    jvmtiEnv *jvmti = VM::jvmti();
    jvmti->GetMethodName(method, &_name, &_sig, NULL);
    jvmti->GetMethodDeclaringClass(method, &method_class);
    jvmti->GetClassSignature(method_class, &_class_sig, NULL);

    char *s;
    for (s = _class_sig; *s; s++) {
        if (*s == '/') *s = '.';
    }
    s[-1] = 0;
}

MethodName::~MethodName() {
    jvmtiEnv *jvmti = VM::jvmti();
    jvmti->Deallocate((unsigned char *) _name);
    jvmti->Deallocate((unsigned char *) _sig);
    jvmti->Deallocate((unsigned char *) _class_sig);
}


void CallTraceSample::assign(ASGCT_CallTrace *trace) {
    _call_count = 1;
    _num_frames = trace->num_frames;
    for (int i = 0; i < trace->num_frames; i++) {
        _frames[i] = trace->frames[i];
    }
}


uint64_t  Profiler::hashCallTrace(ASGCT_CallTrace *trace) {
    const uint64_t M = 0xc6a4a7935bd1e995LL;
    const int R = 47;

    uint64_t h = trace->num_frames * M;

    for (int i = 0; i < trace->num_frames; i++) {
        uint64_t k = ((uint64_t) trace->frames[i].bci << 32) ^(uint64_t) trace->frames[i].method_id;
        k *= M;
        k ^= k >> R;
        k *= M;
        h ^= k;
        h *= M;
    }

    h ^= h >> R;
    h *= M;
    h ^= h >> R;

    return h;
}

void Profiler::storeCallTrace(ASGCT_CallTrace *trace) {
    uint64_t hash = hashCallTrace(trace);
    int bucket = (int) (hash % MAX_CALLTRACES);
    int i = bucket;

    do {
        if (_hashes[i] == hash && _traces[i]._call_count > 0) {
            _traces[i]._call_count++;
            return;
        } else if (_hashes[i] == 0) {
            break;
        }
        if (++i == MAX_CALLTRACES) i = 0;
    } while (i != bucket);

    _hashes[i] = hash;
    _traces[i].assign(trace);
}

uint64_t  Profiler::hashMethod(jmethodID method) {
    const uint64_t M = 0xc6a4a7935bd1e995LL;
    const int R = 17;

    uint64_t h = (uint64_t) method;

    h ^= h >> R;
    h *= M;
    h ^= h >> R;

    return h;
}

void Profiler::storeMethod(jmethodID method) {
    uint64_t hash = hashMethod(method);
    int bucket = (int) (hash % MAX_CALLTRACES);
    int i = bucket;

    do {
        if (_methods[i]._method == method) {
            _methods[i]._call_count++;
            return;
        } else if (_methods[i]._method == NULL) {
            break;
        }
        if (++i == MAX_CALLTRACES) i = 0;
    } while (i != bucket);

    _methods[i]._call_count = 1;
    _methods[i]._method = method;
}

void Profiler::recordSample(void *ucontext) {
    _calls_total++;

    JNIEnv *jni = VM::jni();
    if (jni == NULL) {
        _calls_non_java++;
        return;
    }

    ASGCT_CallFrame frames[MAX_FRAMES];
    ASGCT_CallTrace trace = {jni, MAX_FRAMES, frames};
    AsyncGetCallTrace(&trace, trace.num_frames, ucontext);

    if (trace.num_frames > 0) {
        storeCallTrace(&trace);
        storeMethod(frames[0].method_id);
    } else if (trace.num_frames == -2) {
        _calls_gc++;
    } else if (trace.num_frames == -9) {
        _calls_deopt++;
    } else {
        _calls_unknown++;
    }
}

void Profiler::setTimer(long sec, int usec) {
    bool enabled = sec != 0 || usec != 0;

    struct sigaction sa;
    sa.sa_handler = enabled ? NULL : SIG_IGN;
    sa.sa_sigaction = enabled ? &sigprofHandler : NULL;
    sa.sa_flags = SA_RESTART | SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGPROF, &sa, NULL);

    struct itimerval itv = {{sec, usec},
                            {sec, usec}};
    setitimer(ITIMER_PROF, &itv, NULL);
}

void Profiler::start(long interval) {
    if (_running) return;
    _running = true;

    _calls_total = _calls_non_java = _calls_gc = _calls_deopt = _calls_unknown = 0;
    memset(_hashes, 0, sizeof(_hashes));
    memset(_traces, 0, sizeof(_traces));
    memset(_methods, 0, sizeof(_methods));

    setTimer(interval / 1000, interval * 1000);
}

void Profiler::stop() {
    if (!_running) return;
    _running = false;

    setTimer(0, 0);
}

int error(const char *msg, int error = -1) {
    LOG(ERROR) << msg;
    return error;
}

int sendResponse(int sockfd, me::serce::franky::Response &message) {
    using namespace google::protobuf;
    using namespace google::protobuf::io;

    FileOutputStream raw_output(sockfd);
    google::protobuf::io::CodedOutputStream output(&raw_output);

    // Write the size.
    const int size = message.ByteSize();
    output.WriteVarint32((uint32) size);

    uint8_t *buffer = output.GetDirectBufferForNBytesAndAdvance(size);
    if (buffer != NULL) {
        // Optimization:  The message fits in one buffer, so use the faster
        // direct-to-array serialization path.
        message.SerializeWithCachedSizesToArray(buffer);
    } else {
        // Slightly-slower path when the message is multiple buffers.
        message.SerializeWithCachedSizes(&output);
        if (output.HadError()) {
            return error("output has errors");
        }
    }
    return 0;
}

/**
 * Our agent enables -XX:+DebugNonSafepoints, but when we connect the agent this flag will be working only for
 * methods compiled after an agent connection.
 *
 * We want to call CodeCache::mark_all_nmethods_for_deoptimization, but we can't without playing with offsets which isn't
 * safe. Another way is to use WhiteBox API, but we can't enable it at runtime.
 *
 * So our choice is to call RedefineClasses which will invoke deoptimization of the world because agent is connected
 * after JVM started.
 *
 * @see VM_RedefineClasses::redefine_single_class and VM_RedefineClasses::flush_dependent_code
 */
int performWorldDeopt() {
    jvmtiEnv *jvmti = VM::jvmti();
    JNIEnv *env = VM::jni();
    jvmtiClassDefinition jcd;
    const char *className = "java/io/Serializable";
    const unsigned char classBytes[] = {202, 254, 186, 190, 0, 0, 0, 52, 0, 7, 7, 0, 5, 7, 0, 6, 1, 0, 10, 83, 111, 117,
                                        114, 99, 101, 70, 105, 108, 101, 1, 0, 17, 83, 101, 114, 105, 97, 108, 105, 122,
                                        97, 98, 108, 101, 46, 106, 97, 118, 97, 1, 0, 20, 106, 97, 118, 97, 47, 105,
                                        111, 47, 83, 101, 114, 105, 97, 108, 105, 122, 97, 98, 108, 101, 1, 0, 16, 106,
                                        97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 6, 1, 0, 1,
                                        0, 2, 0, 0, 0, 0, 0, 0, 0, 1, 0, 3, 0, 0, 0, 2, 0, 4};
    jcd.klass = env->FindClass(className);
    if (jcd.klass == NULL) {
        return error("klass is NULL");
    }
    jcd.class_byte_count = sizeof(classBytes);
    jcd.class_bytes = classBytes;

    jvmtiError err = jvmti->RedefineClasses(1, &jcd);
    return err;
}

int Profiler::handleRequestLoop() {
    LOG(INFO) << "Sending init response";
    int err;
    Response response;
    response.set_id(getpid());
    response.set_type(Response_ResponseType_INIT);
    try {
        err = sendResponse(sockfd, response);
        if (err != 0) {
            return error("Unable to send init response");
        }
        LOG(INFO) << "Init response has benn sent";
        while (true) {
            Request request;
            int res = readRequest(&request);
            if (res < 0) {
                if (res == -2) {
                    // exit
                    return 0;
                }
                LOG(ERROR) << "Unable to read request " << res;
                return res;
            }

            switch (request.type()) {
                case Request_RequestType_START_PROFILING:
                    LOG(INFO) << "Starting profiling";
                    start(DEFAULT_INTERVAL);
                    break;
                case Request_RequestType_STOP_PROFILING:
                    LOG(INFO) << "Stopping profiling";
                    stop();
                    res = writeResult();
                    if (res != 0) {
                        return error("Unable to write result");
                    }
                    break;
                case Request_RequestType_DETACH:
                    LOG(INFO) << "Detaching...";
                    close(sockfd);
                    return 0;
                default:
                    continue;
            }
        }
    } catch (const google::protobuf::FatalException &fe) {
        // shouldn't happen
        LOG(ERROR) << fe.message();
        return -1;
    }

}


int Profiler::init(int port) {
    LOG(INFO) << "INIT MESSAGAGE";
    portno = (uint16_t) port;
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        return error("opening socket");
    }
    server = gethostbyname("localhost");
    if (server == NULL) {
        return error("unable to connect to localhost");
    }
    struct sockaddr_in serv_addr;
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    bcopy(server->h_addr, (char *) &serv_addr.sin_addr.s_addr, server->h_length);
    serv_addr.sin_port = htons(portno);
    if (connect(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
        return error("connecting");
    }
    int err = performWorldDeopt();
    if (err < 0) {
        return error("Unable to deoptimize world.");
    }
    return handleRequestLoop();
}

int Profiler::readRequest(Request *message) {
    using namespace google::protobuf;
    using namespace google::protobuf::io;

    FileInputStream raw_input(sockfd);
    google::protobuf::io::CodedInputStream input(&raw_input);

    // Read the size.
    uint32_t size;
    if (!input.ReadVarint32(&size)) {
        return -2;
    }

    // Tell the stream not to read beyond that size.
    input.PushLimit(size);

    // Parse the message.
    if (!message->MergeFromCodedStream(&input)) {
        return error("Error paring message 1");
    }
    if (!input.ConsumedEntireMessage()) {
        return error("Error paring message 2");
    }
    return 0;
}

int64_t jMethodIdToId(const jmethodID &jmethod) {
    return (int64_t) jmethod;
}

MethodInfo *fillMethodInfo(MethodInfo *methodInfo, const jmethodID &jmethod) {
    MethodName mn(jmethod);
    methodInfo->set_jmethodid(jMethodIdToId(jmethod));
    methodInfo->set_name(mn.name());
    methodInfo->set_holder(mn.holder());
    methodInfo->set_sig(mn.signature());
    VM &vm = VM::get();
    CompileInfo *compiles_info = vm.compiles_info[jmethod];
    bool is_compiled = compiles_info != nullptr;
    methodInfo->set_compiled(is_compiled);
    if (is_compiled) {
        methodInfo->set_inlined(compiles_info->method != jmethod);
    }
    return methodInfo;
}

int Profiler::writeResult() {
    Response response;
    ProfilingInfo *info = new ProfilingInfo();

    info->set_calls_total(_calls_total);
    info->set_calls_non_java(_calls_non_java);
    info->set_calls_gc(_calls_gc);
    info->set_calls_deopt(_calls_deopt);
    info->set_calls_unknown(_calls_unknown);

    std::unordered_set<jmethodID> methodIds;
    saveMethods(info, methodIds);
    saveCallTraces(info, methodIds);
    saveMethodIds(info, methodIds);

    response.set_id(getpid());
    response.set_type(Response_ResponseType_PROF_INFO);
    response.set_allocated_prof_info(info);
    return sendResponse(sockfd, response);
}

void Profiler::saveCallTraces(ProfilingInfo *info, std::unordered_set<jmethodID> &methods) {
    qsort(_traces, MAX_CALLTRACES, sizeof(CallTraceSample), CallTraceSample::comparator);
    int max_traces = MAX_CALLTRACES;
    for (int i = 0; i < max_traces; i++) {
        const CallTraceSample &trace = _traces[i];
        int samples = trace._call_count;
        if (samples == 0) break;

        CallTraceSampleInfo *sampleInfo = info->add_samples();
        sampleInfo->set_call_count(trace._call_count);

        for (int j = 0; j < trace._num_frames; j++) {
            const ASGCT_CallFrame *frame = &trace._frames[j];
            jmethodID jmethod = frame->method_id;
            if (jmethod != NULL) {
                CallFrame *callFrame = sampleInfo->add_frame();
                callFrame->set_bci(frame->bci);
                callFrame->set_jmethodid(jMethodIdToId(jmethod));
                methods.insert(jmethod);
            }
        }
    }
}

void Profiler::saveMethods(ProfilingInfo *info, std::unordered_set<jmethodID> &methods) {
    qsort(_methods, MAX_CALLTRACES, sizeof(MethodSample), MethodSample::comparator);
    for (int i = 0; i < MAX_CALLTRACES; i++) {
        int samples = _methods[i]._call_count;
        if (samples == 0) break;

        jmethodID jmethod = _methods[i]._method;
        MethodSampleInfo *sample_info = info->add_methods();
        sample_info->set_call_count(samples);
        sample_info->set_jmethodid(jMethodIdToId(jmethod));
        methods.insert(jmethod);
    }
}

void Profiler::saveMethodIds(ProfilingInfo *info, std::unordered_set<jmethodID> &methodIds) {
    for (auto &jmethod: methodIds) {
        MethodInfo *methodInfo = info->add_methodinfos();
        fillMethodInfo(methodInfo, jmethod);
    }
}





