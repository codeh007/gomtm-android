package com.gomtm.android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gomtm.android.swarm.SwarmRuntime
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private val swarmRuntime = SwarmRuntime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.refreshButton).setOnClickListener {
            render()
        }

        render()
    }

    private fun render() {
        val status = swarmRuntime.probe()
        val logs = swarmRuntime.drainLogs()

        setText(R.id.summaryValue, if (status.aarDetected) {
            getString(R.string.status_summary_bound)
        } else {
            getString(R.string.status_summary_unbound)
        })
        setText(R.id.modeValue, status.integrationMode)
        setText(R.id.bridgeValue, status.bridgeClassName ?: getString(R.string.value_not_available))
        setText(R.id.lifecycleValue, status.lifecycleSurface)
        setText(R.id.stateValue, status.state)
        setText(R.id.peerValue, status.peerId ?: getString(R.string.value_not_available))
        setText(R.id.bootstrapValue, status.bootstrapAddress ?: getString(R.string.value_not_available))
        setText(R.id.errorValue, status.lastError ?: getString(R.string.value_none))
        setText(R.id.logsValue, logs.ifBlank { getString(R.string.value_no_logs_yet) })
    }

    private fun setText(viewId: Int, value: String) {
        findViewById<TextView>(viewId).text = value
    }
}
