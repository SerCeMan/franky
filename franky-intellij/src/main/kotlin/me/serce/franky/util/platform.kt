package me.serce.franky.util

import com.intellij.openapi.projectRoots.ProjectJdkTable
import me.serce.franky.jvm.JVMAttachService
import java.io.File
import java.io.File.pathSeparator
import java.lang.management.ManagementFactory

val currentPID: String
    get() {
        val jvm = ManagementFactory.getRuntimeMXBean().name
        return jvm.substring(0, jvm.indexOf('@'))
    }


enum class Platform {
    LINUX, WINDOWS, MAC, SOLARIS;

    companion object {
        fun current(): Platform {
            val os = System.getProperty("os.name").toLowerCase()
            return when {
                os.indexOf("win") >= 0 -> WINDOWS
                os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0 -> LINUX
                os.indexOf("mac") >= 0 -> MAC
                os.indexOf("sunos") >= 0 -> SOLARIS
                else -> throw RuntimeException("Unknown platform $os")
            }
        }

        fun is64Bit(): Boolean {
            val osArch = System.getProperty("os.arch")
            return "amd64" == osArch || "x86_64" == osArch
        }
    }
}

fun addToLibPath(path: String) {
    System.setProperty("java.library.path", path + (System.getProperty("java.library.path")?.let { "$pathSeparator$it" } ?: ""))
    // java.library.path is cached
    // use reflection to clear the cache
    val fieldSysPath = ClassLoader::class.java.getDeclaredField("sys_paths")
    fieldSysPath.isAccessible = true
    fieldSysPath.set(null, null)
}


/**
 * Check that libattach.so is in libpath.
 * If it is not, try to add it from other jdk's in the system
 */
fun ensureLibattach() {
    try {
        System.loadLibrary("attach")
    } catch (e: UnsatisfiedLinkError) {
        for (jdk in ProjectJdkTable.getInstance().allJdks) {
            val libPath = "${jdk.homePath}/jre/lib"
            val jdkPath = "$libPath/amd64/libattach.so"
            if (File(jdkPath).exists()) {
                addToLibPath(libPath)
                JVMAttachService.LOG.info("$jdkPath was added to lib path")
                try {
                    System.loadLibrary("attach")
                    break
                } catch (e: UnsatisfiedLinkError) {
                    JVMAttachService.LOG.error("Error connecting to vm", e)
                }
            }
        }
        throw RuntimeException("Unable connect VM. Please add libattach.so library to libpath", e)
    }
}