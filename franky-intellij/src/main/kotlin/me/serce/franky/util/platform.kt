package me.serce.franky.util

import com.intellij.openapi.projectRoots.ProjectJdkTable
import me.serce.franky.jvm.JVMAttachService
import sun.tools.attach.LinuxVirtualMachine
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
 * TODO: currently linux only
 *
 * Check that libattach.so is in libpath.
 * If it is not, try to add it from other jdk's in the system
 */
fun ensureLibattach() {
    /*
       Sadly, we have to do it or else LinuxVirtualMachine will be erroneous.
       If we try to load System.loadLibrary("attach") then we won't be able to load it by another classloader.
       TODO Maybe try to load it via reflection later (loadLibrary0)
     */
    for (jdk in ProjectJdkTable.getInstance().allJdks) {
        val libPath = "${jdk.homePath}/jre/lib/amd64/"
        val jdkPath = "$libPath/libattach.so"
        if (File(jdkPath).exists()) {
            addToLibPath(libPath)
            JVMAttachService.LOG.info("$jdkPath was added to lib path")
            //            try {
            //                Class.forName("sun.tools.attach.LinuxVirtualMachine")
            //                return
            //            } catch (ule: Error) {
            //                JVMAttachService.LOG.error("Error connecting to vm", ule)
            //            }
        }
    }
}