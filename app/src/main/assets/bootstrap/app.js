(function () {
  const bridgeName = 'GomtmAndroid'
  const refreshIntervalMs = 1500
  const logState = []
  let inputsDirty = false
  let formInitialized = false

  function bridge() {
    return window[bridgeName]
  }

  function parseJson(raw, fallback) {
    try {
      return JSON.parse(raw)
    } catch (_error) {
      return fallback
    }
  }

  function currentConfig() {
    return {
      bootstrap_address: value('bootstrapInput'),
      node_name: value('nodeNameInput'),
      console_url: value('consoleUrlInput'),
    }
  }

  function value(id) {
    return document.getElementById(id).value.trim()
  }

  function setValue(id, nextValue) {
    document.getElementById(id).value = nextValue || ''
  }

  function setText(id, nextValue, extraClass) {
    const element = document.getElementById(id)
    element.textContent = nextValue || '-'
    element.className = extraClass ? `pre ${extraClass}` : 'pre'
  }

  function withBridge(action) {
    const host = bridge()
    if (!host || typeof host.getRuntimeSnapshot !== 'function') {
      setText('errorValue', '当前页面没有可用的原生宿主 bridge。请返回初始化页。', 'error')
      return null
    }
    return action(host)
  }

  function syncForm(snapshot, force) {
    if (!snapshot || !snapshot.config) {
      return
    }
    if (!formInitialized || force || !inputsDirty) {
      setValue('bootstrapInput', snapshot.config.bootstrap_address || '')
      setValue('nodeNameInput', snapshot.config.node_name || '')
      setValue('consoleUrlInput', snapshot.config.console_url || '')
      formInitialized = true
      inputsDirty = false
    }
  }

  function renderPeers(peers) {
    if (!Array.isArray(peers) || peers.length === 0) {
      return '当前会话还没有发现其他节点。'
    }
    return peers
      .map((peer) => {
        const name = peer.name || peer.peer_id
        return `${name}\npeer_id=${peer.peer_id}\nstate=${peer.state}\nlast_seen_at=${peer.last_seen_at || 'unknown'}`
      })
      .join('\n\n')
  }

  function appendLogs(nextLogs) {
    const trimmed = (nextLogs || '').trim()
    if (!trimmed) {
      return
    }
    logState.push(trimmed)
    if (logState.length > 20) {
      logState.splice(0, logState.length - 20)
    }
  }

  function renderSnapshot(snapshot, options) {
    const forceSync = options && options.forceSync
    syncForm(snapshot, forceSync)
    appendLogs(snapshot?.runtime?.recent_logs)

    document.getElementById('stateValue').textContent = snapshot?.runtime?.state || 'Unknown'
    document.getElementById('peerValue').textContent = snapshot?.runtime?.peer_id || 'Not available'
    document.getElementById('bootstrapValue').textContent = snapshot?.runtime?.bootstrap_address || 'Not available'
    document.getElementById('bridgeValue').textContent = snapshot?.runtime?.bridge_class_name || 'Not available'
    document.getElementById('accessibilityValue').textContent = snapshot?.runtime?.permissions?.accessibility || 'unknown'
    document.getElementById('screenCaptureValue').textContent = snapshot?.runtime?.permissions?.screen_capture || 'unknown'
    setText('peersValue', renderPeers(snapshot?.runtime?.discovered_peers || []))

    const actionError = snapshot?.runtime?.action_error || ''
    const runtimeError = snapshot?.runtime?.last_error || ''
    const errorText = actionError || runtimeError || 'None'
    setText('errorValue', errorText, errorText === 'None' ? '' : 'error')
    setText('logsValue', logState.join('\n\n') || 'No runtime logs yet.')
  }

  function refreshSnapshot(forceSync) {
    withBridge((host) => {
      const snapshot = parseJson(host.getRuntimeSnapshot(), null)
      if (snapshot) {
        renderSnapshot(snapshot, { forceSync })
      }
    })
  }

  function bindFormEvents() {
    for (const id of ['bootstrapInput', 'nodeNameInput', 'consoleUrlInput']) {
      document.getElementById(id).addEventListener('input', () => {
        inputsDirty = true
      })
    }
  }

  function bindButtons() {
    document.getElementById('saveButton').addEventListener('click', () => {
      withBridge((host) => {
        const snapshot = parseJson(host.saveHostSettings(JSON.stringify(currentConfig())), null)
        if (snapshot) {
          renderSnapshot(snapshot, { forceSync: true })
        }
      })
    })

    document.getElementById('refreshButton').addEventListener('click', () => refreshSnapshot(false))

    document.getElementById('startButton').addEventListener('click', () => {
      withBridge((host) => {
        const snapshot = parseJson(host.startNode(JSON.stringify(currentConfig())), null)
        if (snapshot) {
          renderSnapshot(snapshot, { forceSync: true })
        }
      })
    })

    document.getElementById('stopButton').addEventListener('click', () => {
      withBridge((host) => {
        const snapshot = parseJson(host.stopNode(), null)
        if (snapshot) {
          renderSnapshot(snapshot, { forceSync: false })
        }
      })
    })

    document.getElementById('openAccessibilityButton').addEventListener('click', () => {
      withBridge((host) => host.openAccessibilitySettings())
    })

    document.getElementById('openConsoleButton').addEventListener('click', () => {
      withBridge((host) => {
        const result = parseJson(host.openConsoleUrl(value('consoleUrlInput')), { ok: false, error: 'invalid_console_url' })
        if (!result.ok) {
          setText('errorValue', '请先填写可访问的 gomtmui URL。', 'error')
        }
      })
    })
  }

  document.addEventListener('DOMContentLoaded', () => {
    bindFormEvents()
    bindButtons()
    refreshSnapshot(true)
    window.setInterval(() => refreshSnapshot(false), refreshIntervalMs)
  })
})()
