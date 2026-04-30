package com.gomtm.swarm.platform.python

object PythonRuntimeWorkerProbe {
    const val SCRIPT = "import json; print(json.dumps({'worker':'ok'}))"
}
