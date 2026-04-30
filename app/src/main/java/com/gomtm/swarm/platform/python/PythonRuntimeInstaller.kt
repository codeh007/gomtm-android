package com.gomtm.swarm.platform.python

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

class PythonRuntimeInstaller(private val context: Context) {
    private val paths = PythonRuntimePaths.from(context)

    fun bundledRuntimeRoot(): File {
        return File(context.filesDir, "bundled-python-runtime/${runtimeReleaseId()}")
    }

    fun runtimeArchiveUrl(): String = "https://www.python.org/ftp/python/3.14.4/python-3.14.4-aarch64-linux-android.tar.gz"

    fun runtimeReleaseId(): String = "3.14.4-aarch64"

    fun ensureDirectories() {
        listOf(
            paths.runtimeRoot,
            paths.releasesRoot,
            paths.overlaysRoot,
            paths.wheelhouseRoot,
            paths.logsRoot,
            paths.runRoot,
        ).forEach { File(it).mkdirs() }
    }

    fun writeWrapperScripts(
        releaseRoot: File,
        prefixDir: File,
        sourceApkPath: String,
        nativeLibraryDir: String,
    ) {
        val binDir = File(releaseRoot, "bin")
        binDir.mkdirs()

        val pythonReal = File(binDir, "python-real")
        pythonReal.writeText(
            """
            #!/system/bin/sh
            export CLASSPATH=$sourceApkPath
            exec /system/bin/app_process /system/bin com.gomtm.swarm.platform.python.PythonRuntimeMain \
              --python-home ${prefixDir.absolutePath} \
              --source-apk $sourceApkPath \
              --native-lib-dir $nativeLibraryDir \
              -- "${'$'}@"
            """.trimIndent() + "\n",
        )
        pythonReal.setExecutable(true)

        val python = File(binDir, "python")
        python.writeText(
            """
            #!/system/bin/sh
            exec "${pythonReal.absolutePath}" "${'$'}@"
            """.trimIndent() + "\n",
        )
        python.setExecutable(true)

        val pip = File(binDir, "pip")
        pip.writeText(
            """
            #!/system/bin/sh
            exec "${pythonReal.absolutePath}" -m pip "${'$'}@"
            """.trimIndent() + "\n",
        )
        pip.setExecutable(true)
    }

    private fun ensureWrapperScriptsExecutable(runtimeRoot: File) {
        listOf("python-real", "python", "pip").forEach { name ->
            File(runtimeRoot, "bin/$name").takeIf(File::exists)?.setExecutable(true)
        }
    }

    fun stageBaseLayout(releaseRoot: File) {
        val stdlibDir = File(releaseRoot, "prefix/lib/python3.14")
        stdlibDir.mkdirs()

        val basePackage = File(releaseRoot, "site-packages-base/gomtm_python_base/__init__.py")
        basePackage.parentFile?.mkdirs()
        if (!basePackage.exists()) {
            basePackage.writeText(
                """
                package_name = "gomtm_python_base"
                bootstrap_gate = "base-package"

                __all__ = ["bootstrap_gate", "package_name"]
                """.trimIndent() + "\n",
            )
        }
    }

    fun stageBundledRuntime(sourceRoot: File, releaseRoot: File) {
        if (!sourceRoot.exists()) {
            return
        }
        sourceRoot.walkTopDown().forEach { item ->
            val relative = item.relativeTo(sourceRoot)
            val target = File(releaseRoot, relative.path)
            if (item.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                item.copyTo(target, overwrite = true)
            }
        }
    }

    fun ensureInstalled(sourceApkPath: String, nativeLibraryDir: String, bundledRuntimeRoot: File? = null): PythonRuntimeStatus {
        ensureDirectories()
        val releaseRoot = File(paths.releasesRoot, runtimeReleaseId())
        val prefixDir = File(releaseRoot, "prefix")
        prefixDir.mkdirs()
        val bundledRoot = bundledRuntimeRoot ?: bundledRuntimeRoot().takeIf { it.exists() }
        if (bundledRoot != null) {
            stageBundledRuntime(bundledRoot, releaseRoot)
        } else if (!File(releaseRoot, "prefix/lib/libpython3.14.so").exists()) {
            val archiveFile = File(paths.wheelhouseRoot, "python-${runtimeReleaseId()}-linux-android.tar.gz")
            if (!archiveFile.exists()) {
                downloadTo(runtimeArchiveUrl(), archiveFile)
            }
            extractTarGz(archiveFile, releaseRoot)
        }
        stageBaseLayout(releaseRoot)
        writeWrapperScripts(releaseRoot, prefixDir, sourceApkPath, nativeLibraryDir)

        val current = File(paths.currentLink)
        if (current.exists()) {
            current.deleteRecursively()
        }
        releaseRoot.copyRecursively(current, overwrite = true)
        ensureWrapperScriptsExecutable(current)
        return inspect()
    }

    private fun downloadTo(source: String, dest: File) {
        dest.parentFile?.mkdirs()
        val connection = URL(source).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractTarGz(archive: File, destDir: File) {
        archive.inputStream().use { fileInput ->
            GzipCompressorInputStream(fileInput).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val relative = entry.name.removePrefix("./")
                        if (relative.isBlank()) {
                            continue
                        }
                        val target = File(destDir, relative)
                        if (entry.isDirectory) {
                            target.mkdirs()
                            continue
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            tar.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun extractTarGzForTest(archive: File, destDir: File) {
        extractTarGz(archive, destDir)
    }

    fun inspect(): PythonRuntimeStatus {
        ensureDirectories()
        val current = File(paths.currentLink)
        val python = File(current, "bin/python")
        return if (python.exists()) {
            PythonRuntimeStatus(
                installed = true,
                activeRelease = runtimeReleaseId(),
                pythonPath = python.absolutePath,
                message = "python runtime ready",
            )
        } else {
            PythonRuntimeStatus(installed = false, message = "python runtime not installed")
        }
    }
}
