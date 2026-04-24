package com.gomtm.swarm.shell

import android.content.Context
import java.io.File

data class NodeRuntimePaths(
    val userspaceRoot: File,
    val prefixDir: File,
    val homeDir: File,
    val tmpDir: File,
    val runtimeRoot: File,
    val runtimeBinDir: File,
    val nodeRoot: File,
    val nodeWrapper: File,
    val nodeReal: File,
    val compatShim: File,
    val glibcRoot: File,
    val glibcLibDir: File,
    val glibcLinker: File,
)

object NodeRuntimePathsResolver {
    fun resolve(context: Context): NodeRuntimePaths {
        val userspaceRoot = File(context.filesDir, "gomtm-nodejs")
        val prefixDir = File(userspaceRoot, "usr")
        val homeDir = File(userspaceRoot, "home")
        val tmpDir = File(userspaceRoot, "tmp")
        val runtimeRoot = File(homeDir, ".gomtm-nodejs")
        val runtimeBinDir = File(runtimeRoot, "bin")
        val nodeRoot = File(runtimeRoot, "node")
        val glibcRoot = File(prefixDir, "glibc")
        val glibcLibDir = File(glibcRoot, "lib")
        return NodeRuntimePaths(
            userspaceRoot = userspaceRoot,
            prefixDir = prefixDir,
            homeDir = homeDir,
            tmpDir = tmpDir,
            runtimeRoot = runtimeRoot,
            runtimeBinDir = runtimeBinDir,
            nodeRoot = nodeRoot,
            nodeWrapper = File(runtimeBinDir, "node"),
            nodeReal = File(nodeRoot, "bin/node.real"),
            compatShim = File(runtimeRoot, "patches/glibc-compat.js"),
            glibcRoot = glibcRoot,
            glibcLibDir = glibcLibDir,
            glibcLinker = File(glibcLibDir, "ld-linux-aarch64.so.1"),
        )
    }
}
