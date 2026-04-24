package com.gomtm.swarm.shell

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.json.JSONObject

data class NodeRuntimeInstallResult(
    val ok: Boolean,
    val message: String,
    val checks: List<String> = emptyList(),
)

object NodeRuntimeInstaller {
    private const val nodeVersion = "22.22.0"
    private const val nodeArchive = "node-v22.22.0-linux-arm64.tar.gz"
    private const val nodeURL = "https://nodejs.org/dist/v22.22.0/$nodeArchive"
    private const val glibcArchive = "glibc-2.42-0-aarch64.pkg.tar.xz"
    private const val glibcURL = "https://service.termux-pacman.dev/gpkg/aarch64/$glibcArchive"
    private const val gccLibsArchive = "gcc-libs-glibc-14.2.1-1-aarch64.pkg.tar.xz"
    private const val gccLibsURL = "https://service.termux-pacman.dev/gpkg/aarch64/$gccLibsArchive"

    fun ensure(context: Context): NodeRuntimeInstallResult {
        val paths = NodeRuntimePathsResolver.resolve(context)
        paths.userspaceRoot.mkdirs()
        paths.prefixDir.mkdirs()
        paths.homeDir.mkdirs()
        paths.tmpDir.mkdirs()
        paths.runtimeBinDir.mkdirs()
        paths.nodeRoot.mkdirs()
        paths.compatShim.parentFile?.mkdirs()
        paths.glibcLibDir.mkdirs()

        if (!paths.compatShim.exists()) {
            paths.compatShim.writeText(minimalCompatShim)
        }

        if (!paths.glibcLinker.exists()) {
            val glibcArchiveFile = File(paths.userspaceRoot, glibcArchive)
            if (!glibcArchiveFile.exists()) {
                downloadTo(glibcURL, glibcArchiveFile)
            }
            extractPacmanPkgTarXz(glibcArchiveFile, paths.glibcRoot)

            val gccArchiveFile = File(paths.userspaceRoot, gccLibsArchive)
            if (!gccArchiveFile.exists()) {
                downloadTo(gccLibsURL, gccArchiveFile)
            }
            extractPacmanPkgTarXz(gccArchiveFile, paths.glibcRoot)

            val hostsFile = File(paths.glibcRoot, "etc/hosts")
            hostsFile.parentFile?.mkdirs()
            if (!hostsFile.exists()) {
                hostsFile.writeText(
                    "127.0.0.1 localhost localhost.localdomain\n::1 localhost ip6-localhost ip6-loopback\n",
                )
            }
        }

        if (!paths.glibcLinker.exists()) {
            return NodeRuntimeInstallResult(
                ok = false,
                message = "glibc linker missing at ${paths.glibcLinker.absolutePath} after bootstrap",
                checks = listOf("userspace_dirs_ready", "compat_shim_ready", "glibc_download_attempted"),
            )
        }

        if (!paths.nodeReal.exists()) {
            val archiveFile = File(paths.userspaceRoot, nodeArchive)
            if (!archiveFile.exists()) {
                downloadTo(nodeURL, archiveFile)
            }
            extractTarGzStripOne(archiveFile, paths.nodeRoot)
            val extractedNode = File(paths.nodeRoot, "bin/node")
            if (extractedNode.exists() && !paths.nodeReal.exists()) {
                extractedNode.renameTo(paths.nodeReal)
            }
        }

        if (!paths.nodeReal.exists()) {
            return NodeRuntimeInstallResult(
                ok = false,
                message = "node.real missing after install",
                checks = listOf("userspace_dirs_ready", "compat_shim_ready", "node_download_attempted"),
            )
        }

        if (!paths.nodeWrapper.exists()) {
            paths.nodeWrapper.writeText(
                """
                #!/system/bin/sh
                export _OA_WRAPPER_PATH="${paths.nodeWrapper.absolutePath}"
                export HOME="${paths.homeDir.absolutePath}"
                export TMPDIR="${paths.tmpDir.absolutePath}"
                export PREFIX="${paths.prefixDir.absolutePath}"
                export PATH="${paths.runtimeBinDir.absolutePath}:${paths.prefixDir.absolutePath}/bin"
                if [ -f "${paths.compatShim.absolutePath}" ]; then
                  case "${'$'}{NODE_OPTIONS:-}" in
                    *"${paths.compatShim.absolutePath}"*) ;;
                    *) export NODE_OPTIONS="${'$'}{NODE_OPTIONS:+${'$'}NODE_OPTIONS }-r ${paths.compatShim.absolutePath}" ;;
                  esac
                fi
                exec "${paths.glibcLinker.absolutePath}" --library-path "${paths.glibcLibDir.absolutePath}" "${paths.nodeReal.absolutePath}" "${'$'}@"
                """.trimIndent() + "\n",
            )
            paths.nodeWrapper.setExecutable(true)
        }

        return NodeRuntimeInstallResult(
            ok = true,
            message = "node runtime ready",
            checks = listOf(
                "userspace_dirs_ready",
                "compat_shim_ready",
                "glibc_linker_ready",
                "node_real_ready",
                "node_wrapper_ready",
            ),
        )
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

    private fun extractTarGzStripOne(archive: File, destDir: File) {
        archive.inputStream().use { fileInput ->
            GzipCompressorInputStream(fileInput).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
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
                        target.setExecutable(relative.startsWith("bin/"))
                    }
                }
            }
        }
    }

    private fun extractPacmanPkgTarXz(archive: File, glibcRoot: File) {
        archive.inputStream().use { fileInput ->
            XZCompressorInputStream(fileInput).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val marker = "data/data/com.termux/files/usr/glibc/"
                        if (!entry.name.startsWith(marker)) {
                            continue
                        }
                        val relative = entry.name.removePrefix(marker)
                        if (relative.isBlank()) {
                            continue
                        }
                        val target = File(glibcRoot, relative)
                        if (entry.isDirectory) {
                            target.mkdirs()
                            continue
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            tar.copyTo(output)
                        }
                        if (relative.contains("/bin/") || relative.endsWith(".so") || relative.contains(".so.")) {
                            target.setExecutable(true)
                        }
                    }
                }
            }
        }
    }

    fun runtimeSummaryJson(context: Context): String {
        val paths = NodeRuntimePathsResolver.resolve(context)
        val install = ensure(context)
        return JSONObject()
            .put("step", "runtime")
            .put("ready", install.ok)
            .put("message", install.message)
            .put("platform", "android")
            .put("node_version_expected", nodeVersion)
            .put("node_path", paths.nodeWrapper.absolutePath)
            .put("node_real_path", paths.nodeReal.absolutePath)
            .put("userspace_root", paths.userspaceRoot.absolutePath)
            .put("prefix_dir", paths.prefixDir.absolutePath)
            .put("home_dir", paths.homeDir.absolutePath)
            .put("tmp_dir", paths.tmpDir.absolutePath)
            .put("glibc_linker_path", paths.glibcLinker.absolutePath)
            .put("compat_shim_path", paths.compatShim.absolutePath)
            .put("checks", install.checks)
            .toString()
    }

    private val minimalCompatShim = """
'use strict';
const os = require('os');
const fs = require('fs');
const child = require('child_process');
const wrapper = process.env._OA_WRAPPER_PATH;
if (wrapper && fs.existsSync(wrapper)) {
  try { Object.defineProperty(process, 'execPath', { value: wrapper, writable: true, configurable: true }); } catch {}
}
delete process.env.LD_PRELOAD;
delete process.env._OA_ORIG_LD_PRELOAD;
const originalCpus = os.cpus;
os.cpus = function cpus() {
  const result = originalCpus.call(os);
  return result && result.length > 0 ? result : [{ model: 'unknown', speed: 0, times: { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 } }];
};
const systemSh = '/system/bin/sh';
if (fs.existsSync(systemSh)) {
  const originalExec = child.exec;
  const originalExecSync = child.execSync;
  child.exec = function exec(command, options, callback) {
    if (typeof options === 'function') { callback = options; options = {}; }
    options = options || {};
    if (!options.shell) options.shell = systemSh;
    return originalExec.call(child, command, options, callback);
  };
  child.execSync = function execSync(command, options) {
    options = options || {};
    if (!options.shell) options.shell = systemSh;
    return originalExecSync.call(child, command, options);
  };
}
""".trimIndent()
}
