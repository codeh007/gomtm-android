package com.gomtm.swarm.platform.python

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.zip.GZIPOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeInstallerTest {
    @Test
    fun inspectReportsNotInstalledBeforeBootstrap() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)

        val status = installer.inspect()

        assertFalse(status.installed)
        assertTrue(status.message.isNotBlank())
    }

    @Test
    fun inspectReportsInstalledWhenCurrentPythonExists() {
        val context = FakeContext()
        val currentDir = File(context.filesDir, "runtime/python/current/bin")
        currentDir.mkdirs()
        val pythonFile = File(currentDir, "python")
        pythonFile.writeText("#!/system/bin/sh\nexit 0\n")
        pythonFile.setExecutable(true)

        val installer = PythonRuntimeInstaller(context)
        val status = installer.inspect()

        assertTrue(status.installed)
        assertTrue(status.pythonPath?.endsWith("/runtime/python/current/bin/python") == true)
        assertTrue(status.message.contains("ready"))
    }

    @Test
    fun inspectUsesReleaseDirectoryNameWhenCurrentPointsAtRelease() {
        val context = FakeContext()
        val releaseDir = File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/bin")
        releaseDir.mkdirs()
        val pythonFile = File(releaseDir, "python")
        pythonFile.writeText("#!/system/bin/sh\nexit 0\n")
        pythonFile.setExecutable(true)

        val currentLink = File(context.filesDir, "runtime/python/current")
        currentLink.parentFile?.mkdirs()
        Files.createSymbolicLink(currentLink.toPath(), File(context.filesDir, "runtime/python/releases/3.14.4-aarch64").toPath())

        val installer = PythonRuntimeInstaller(context)
        val status = installer.inspect()

        assertEquals("3.14.4-aarch64", status.activeRelease)
    }

    @Test
    fun ensureDirectoriesCreatesCanonicalRuntimeRoots() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)

        installer.ensureDirectories()

        val root = File(context.filesDir, "runtime/python")
        assertTrue(File(root, "releases").isDirectory)
        assertTrue(File(root, "overlays").isDirectory)
        assertTrue(File(root, "wheelhouse").isDirectory)
        assertTrue(File(root, "logs").isDirectory)
        assertTrue(File(root, "run").isDirectory)
    }

    @Test
    fun runtimeReleaseIdMatchesPinnedPythonVersionAndAbi() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)

        assertEquals("3.14.4-aarch64", installer.runtimeReleaseId())
    }

    @Test
    fun archiveUrlPointsAtOfficialPythonAndroidEmbeddablePackage() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)

        assertEquals(
            "https://www.python.org/ftp/python/3.14.4/python-3.14.4-aarch64-linux-android.tar.gz",
            installer.runtimeArchiveUrl(),
        )
    }

    @Test
    fun writeWrapperScriptsCreatesAppProcessBasedEntrypoints() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)
        val releaseRoot = File(context.filesDir, "runtime/python/releases/3.14.4-aarch64")
        val prefixDir = File(releaseRoot, "prefix")
        prefixDir.mkdirs()

        installer.writeWrapperScripts(
            releaseRoot = releaseRoot,
            prefixDir = prefixDir,
            sourceApkPath = "/data/app/com.gomtm.swarm/base.apk",
            nativeLibraryDir = "/data/app/com.gomtm.swarm/lib/arm64",
        )

        val pythonReal = File(releaseRoot, "bin/python-real")
        val python = File(releaseRoot, "bin/python")
        val pip = File(releaseRoot, "bin/pip")

        assertTrue(pythonReal.exists())
        assertTrue(python.exists())
        assertTrue(pip.exists())
        assertTrue(pythonReal.readText().contains("export CLASSPATH=/data/app/com.gomtm.swarm/base.apk"))
        assertTrue(pythonReal.readText().contains("/system/bin/app_process"))
        assertTrue(pythonReal.readText().contains("com.gomtm.swarm.platform.python.PythonRuntimeMain"))
        assertTrue(pythonReal.readText().contains("--python-home ${prefixDir.absolutePath}"))
    }

    @Test
    fun stageBaseLayoutCreatesStdlibAndBasePackageRoots() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)
        val releaseRoot = File(context.filesDir, "runtime/python/releases/3.14.4-aarch64")

        installer.stageBaseLayout(releaseRoot)

        assertTrue(File(releaseRoot, "prefix/lib/python3.14").isDirectory)
        assertTrue(File(releaseRoot, "site-packages-base/gomtm_python_base/__init__.py").isFile)
    }

    @Test
    fun ensureInstalledCreatesCurrentRuntimeSkeleton() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)

        val status = installer.ensureInstalled(
            sourceApkPath = "/data/app/com.gomtm.swarm/base.apk",
            nativeLibraryDir = "/data/app/com.gomtm.swarm/lib/arm64",
        )

        assertTrue(status.installed)
        assertEquals("3.14.4-aarch64", status.activeRelease)
        assertTrue(File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/bin/python").isFile)
        assertTrue(File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/bin/python-real").isFile)
    }

    @Test
    fun ensureInstalledCopiesBundledLibpythonIntoReleaseWhenSourceTreeExists() {
        val context = FakeContext()
        val bundledRoot = File(context.filesDir, "bundled/python/releases/3.14.4-aarch64")
        val bundledLib = File(bundledRoot, "prefix/lib/libpython3.14.so")
        bundledLib.parentFile?.mkdirs()
        bundledLib.writeText("libpython")

        val installer = PythonRuntimeInstaller(context)
        val status = installer.ensureInstalled(
            sourceApkPath = "/data/app/com.gomtm.swarm/base.apk",
            nativeLibraryDir = "/data/app/com.gomtm.swarm/lib/arm64",
            bundledRuntimeRoot = bundledRoot,
        )

        assertTrue(status.installed)
        assertTrue(File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/prefix/lib/libpython3.14.so").isFile)
    }

    @Test
    fun ensureInstalledUsesDefaultBundledRuntimeRootWhenPresent() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)
        val bundledRoot = installer.bundledRuntimeRoot()
        val bundledLib = File(bundledRoot, "prefix/lib/libpython3.14.so")
        bundledLib.parentFile?.mkdirs()
        bundledLib.writeText("libpython")

        val status = installer.ensureInstalled(
            sourceApkPath = "/data/app/com.gomtm.swarm/base.apk",
            nativeLibraryDir = "/data/app/com.gomtm.swarm/lib/arm64",
        )

        assertTrue(status.installed)
        assertTrue(File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/prefix/lib/libpython3.14.so").isFile)
    }

    @Test
    fun extractTarGzDropsLeadingDotSlashPrefix() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)
        val archive = File(context.filesDir, "python.tar.gz")
        val dest = File(context.filesDir, "dest")

        writeTinyTarGz(
            archive,
            mapOf(
                "./prefix/lib/libpython3.14.so" to "libpython",
                "./prefix/lib/python3.14/os.py" to "print('os')\n",
            ),
        )

        installer.extractTarGzForTest(archive, dest)

        assertTrue(File(dest, "prefix/lib/libpython3.14.so").isFile)
        assertTrue(File(dest, "prefix/lib/python3.14/os.py").isFile)
    }

    @Test
    fun stageBundledRuntimeCopiesPrefixTreeIntoRelease() {
        val context = FakeContext()
        val installer = PythonRuntimeInstaller(context)
        val sourceRoot = File(context.filesDir, "source/python/releases/3.14.4-aarch64")
        val sourceStdlib = File(sourceRoot, "prefix/lib/python3.14")
        sourceStdlib.mkdirs()
        File(sourceStdlib, "os.py").writeText("print('os')\n")
        File(sourceRoot, "prefix/lib/libpython3.14.so").apply {
            parentFile?.mkdirs()
            writeText("placeholder")
        }

        val releaseRoot = File(context.filesDir, "runtime/python/releases/3.14.4-aarch64")
        installer.stageBundledRuntime(sourceRoot, releaseRoot)

        assertTrue(File(releaseRoot, "prefix/lib/python3.14/os.py").isFile)
        assertTrue(File(releaseRoot, "prefix/lib/libpython3.14.so").isFile)
    }

    private class FakeContext : ContextWrapper(null) {
        private val filesRoot = createTempDir(prefix = "gomtm-python-runtime-")

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesRoot
    }

    private fun writeTinyTarGz(target: File, files: Map<String, String>) {
        val tmpDir = createTempDir(prefix = "gomtm-python-tar-")
        files.forEach { (path, content) ->
            val file = File(tmpDir, path.removePrefix("./"))
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        val tarFile = File(tmpDir.parentFile, "payload.tar")
        val escapedRoot = tmpDir.absolutePath.replace("'", "'\"'\"'")
        val escapedTar = tarFile.absolutePath.replace("'", "'\"'\"'")
        val escapedTarget = target.absolutePath.replace("'", "'\"'\"'")
        val cmd = "tar -cf '$escapedTar' -C '$escapedRoot' . && gzip -c '$escapedTar' > '$escapedTarget'"
        val process = ProcessBuilder("bash", "-lc", cmd).start()
        val exit = process.waitFor()
        check(exit == 0) { process.errorStream.bufferedReader().readText() }
    }
}
