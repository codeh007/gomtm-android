package com.gomtm.swarm.shell

import android.content.Context
import java.io.File
import java.lang.Thread.sleep
import org.json.JSONObject

object NodeRuntimeProbe {
    private const val timeoutMs = 90_000L

    private const val scriptBasic = "console.log(JSON.stringify({step:'basic',message:'hello from gomtm node probe',version:process.version,platform:process.platform,execPath:process.execPath}));"
    private const val scriptStdlib = "const fs=require('fs');const os=require('os');const path=require('path');const tmpRoot=process.env.TMPDIR||os.tmpdir();const probeDir=path.join(tmpRoot,'gomtm-node-probe');fs.mkdirSync(probeDir,{recursive:true});const filePath=path.join(probeDir,'stdlib.txt');fs.writeFileSync(filePath,'gomtm');console.log(JSON.stringify({step:'stdlib',tmpRoot,filePath,fileContent:fs.readFileSync(filePath,'utf8'),cpus:os.cpus().length,networkInterfaces:Object.keys(os.networkInterfaces()).sort()}));"
    private const val scriptSpawn = "const {execSync,spawnSync}=require('child_process');const shellOut=execSync('echo gomtm-node-spawn',{encoding:'utf8'}).trim();const child=spawnSync(process.execPath,['-e','process.stdout.write(JSON.stringify({child:true,version:process.version}))'],{encoding:'utf8'});console.log(JSON.stringify({step:'spawn',shellOut,spawnStatus:child.status,spawnStdout:child.stdout.trim(),spawnStderr:(child.stderr||'').trim()}));"
    private const val scriptHttp = "const http=require('http');const port=31337;const responseBody='gomtm-node-http-ok';const server=http.createServer((req,res)=>{res.writeHead(200,{'content-type':'text/plain'});res.end(responseBody);});server.listen(port,'127.0.0.1',()=>{http.get({host:'127.0.0.1',port,path:'/'},(res)=>{let data='';res.on('data',(chunk)=>data+=chunk);res.on('end',()=>{console.log(JSON.stringify({step:'http',statusCode:res.statusCode,body:data}));server.close(()=>process.exit(0));});}).on('error',(err)=>{console.error(err.stack||String(err));server.close(()=>process.exit(1));});});"

    fun runtime(context: Context): String = NodeRuntimeInstaller.runtimeSummaryJson(context)

    fun runBasic(context: Context): String = runScript(context, "basic", scriptBasic)

    fun runStdlib(context: Context): String = runScript(context, "stdlib", scriptStdlib)

    fun runSpawn(context: Context): String = runScript(context, "spawn", scriptSpawn)

    fun runHttp(context: Context): String = runScript(context, "http", scriptHttp)

    private fun runScript(context: Context, step: String, script: String): String {
        val install = NodeRuntimeInstaller.ensure(context)
        val paths = NodeRuntimePathsResolver.resolve(context)
        if (!install.ok) {
            return JSONObject()
                .put("step", step)
                .put("success", false)
                .put("exit_code", -1)
                .put("stderr", install.message)
                .put("duration_ms", 0)
                .toString()
        }
        val startAt = System.currentTimeMillis()
        val process = ProcessBuilder(paths.nodeWrapper.absolutePath, "-e", script)
            .directory(paths.homeDir)
            .apply {
                environment().clear()
                environment()["PREFIX"] = paths.prefixDir.absolutePath
                environment()["HOME"] = paths.homeDir.absolutePath
                environment()["TMPDIR"] = paths.tmpDir.absolutePath
                environment()["PATH"] = paths.runtimeBinDir.absolutePath + ":" + File(paths.prefixDir, "bin").absolutePath
            }
            .start()
        val stdout = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()
        val finished = waitForProcess(process, timeoutMs)
        val durationMs = System.currentTimeMillis() - startAt
        if (!finished) {
            process.destroy()
            return JSONObject()
                .put("step", step)
                .put("success", false)
                .put("exit_code", -1)
                .put("stderr", "timed out after ${timeoutMs}ms")
                .put("duration_ms", durationMs)
                .toString()
        }
        return JSONObject()
            .put("step", step)
            .put("success", process.exitValue() == 0)
            .put("exit_code", process.exitValue())
            .put("stdout", stdout)
            .put("stderr", stderr)
            .put("duration_ms", durationMs)
            .toString()
    }

    private fun waitForProcess(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (hasProcessExited(process)) {
                return true
            }
            sleep(100)
        }
        return hasProcessExited(process)
    }

    private fun hasProcessExited(process: Process): Boolean {
        return try {
            process.exitValue()
            true
        } catch (_: IllegalThreadStateException) {
            false
        }
    }
}
