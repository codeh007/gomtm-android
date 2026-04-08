(function () {
  const bridgeName = 'GomtmAndroid'
  const refreshIntervalMs = 1500
  const activeRuntimeStates = new Set(['starting', 'connecting', 'connected', 'registered'])
  const logState = []
  let inputsDirty = false
  let formInitialized = false
  let lastSnapshot = null

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
      console_url: value('consoleUrlInput'),
    }
  }

  function value(id) {
    return document.getElementById(id).value.trim()
  }

  function setValue(id, nextValue) {
    document.getElementById(id).value = nextValue || ''
  }

  function setPreText(id, nextValue, extraClass) {
    const element = document.getElementById(id)
    element.textContent = nextValue || ''
    element.className = extraClass ? `pre ${extraClass}` : 'pre'
  }

  function setInlineFeedback(id, text, tone) {
    const element = document.getElementById(id)
    element.textContent = text || ''
    element.dataset.tone = tone || 'muted'
  }

  function setHidden(id, hidden) {
    document.getElementById(id).hidden = Boolean(hidden)
  }

  function withBridge(action) {
    const host = bridge()
    if (!host || typeof host.getRuntimeSnapshot !== 'function') {
      renderDiagnostics({
        peersText: '',
        peersCount: 0,
        errorText: '当前页面没有可用的原生宿主 bridge。请返回初始化页。',
        logText: '',
        logCount: 0,
      })
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
      setValue('consoleUrlInput', snapshot.config.console_url || '')
      formInitialized = true
      inputsDirty = false
    }
  }

  function renderPeers(peers) {
    if (!Array.isArray(peers) || peers.length === 0) {
      return ''
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

  function stateTone(state, hasError) {
    if (hasError) {
      return 'error'
    }

    const normalized = (state || '').trim().toLowerCase()
    if (['connected', 'registered', 'ready'].includes(normalized)) {
      return 'connected'
    }
    if (['starting', 'connecting'].includes(normalized)) {
      return 'starting'
    }
    if (['error', 'failed'].includes(normalized)) {
      return 'error'
    }
    return 'neutral'
  }

  function normalizedValue(value) {
    return String(value || '').trim().toLowerCase()
  }

  function validateConsoleUrl(rawValue) {
    const trimmed = String(rawValue || '').trim()
    if (!trimmed) {
      return {
        ok: true,
        configured: false,
        canonical_url: '',
      }
    }

    const host = bridge()
    if (!host || typeof host.validateConsoleUrl !== 'function') {
      return {
        ok: false,
        configured: true,
        error: 'bridge_unavailable',
        message: '当前页面无法校验 gomtmui URL。',
      }
    }

    return parseJson(host.validateConsoleUrl(trimmed), {
      ok: false,
      configured: true,
      error: 'invalid_console_url',
      message: '请输入可访问的 http 或 https gomtmui URL。',
    })
  }

  function resolveConfigValidation() {
    const bootstrapInput = value('bootstrapInput')
    const consoleInput = value('consoleUrlInput')
    const consoleValidation = validateConsoleUrl(consoleInput)
    const bootstrapValidation = bootstrapInput
      ? {
          ok: true,
          configured: true,
          message: '当前 Bootstrap 地址可用于保存并启动节点。',
          tone: 'connected',
        }
      : {
          ok: false,
          configured: false,
          message: '请输入 Bootstrap 地址后再保存或启动节点。',
          tone: 'error',
        }

    const consoleFeedback = !consoleInput
      ? {
          ok: true,
          configured: false,
          message: '未配置 gomtmui URL，保存后可在 App 内打开控制台。',
          tone: 'muted',
          canonicalUrl: '',
        }
      : consoleValidation.ok
        ? {
            ok: true,
            configured: true,
            message: `将打开 ${consoleValidation.canonical_url}`,
            tone: 'connected',
            canonicalUrl: consoleValidation.canonical_url || '',
          }
        : {
            ok: false,
            configured: true,
            message: consoleValidation.message || '请输入可访问的 http 或 https gomtmui URL。',
            tone: 'error',
            canonicalUrl: '',
          }

    return {
      bootstrap: bootstrapValidation,
      console: consoleFeedback,
      canSave: bootstrapValidation.ok && consoleFeedback.ok,
      canStart: bootstrapValidation.ok && consoleFeedback.ok,
      canOpenConsole: consoleFeedback.ok && consoleFeedback.configured,
    }
  }

  function renderConfigValidation() {
    const validation = resolveConfigValidation()
    setInlineFeedback('bootstrapValidationText', validation.bootstrap.message, validation.bootstrap.tone)
    setInlineFeedback('consoleUrlValidationText', validation.console.message, validation.console.tone)
    return validation
  }

  function setButtonAvailability(id, options) {
    const button = document.getElementById(id)
    const disabled = Boolean(options?.disabled)
    const emphasis = disabled ? 'disabled' : (options?.emphasis || 'default')

    button.disabled = disabled
    button.dataset.availability = emphasis
    button.setAttribute('aria-disabled', disabled ? 'true' : 'false')
  }

  function setActionStatus(id, text, tone) {
    const element = document.getElementById(id)
    element.textContent = text
    element.dataset.stateTone = tone || 'neutral'
  }

  function renderSaveDirtyState(validation) {
    const effectiveValidation = validation || resolveConfigValidation()
    const dirty = Boolean(formInitialized && inputsDirty)
    const dirtyBar = document.getElementById('configDirtyBar')
    const saveButton = document.getElementById('saveButton')
    const saveButtonText = document.getElementById('saveButtonText')
    const canSave = dirty && effectiveValidation.canSave

    dirtyBar.dataset.dirty = !dirty ? 'false' : (canSave ? 'true' : 'invalid')
    setActionStatus(
      'configDirtyText',
      !dirty ? '配置已保存' : (canSave ? '有未保存更改' : '修正输入后再保存'),
      !dirty ? 'connected' : (canSave ? 'starting' : 'error'),
    )
    saveButton.hidden = !dirty
    saveButton.disabled = !canSave
    saveButton.dataset.availability = !dirty ? 'secondary' : (canSave ? 'default' : 'secondary')
    saveButton.setAttribute('aria-disabled', canSave ? 'false' : 'true')
    saveButtonText.textContent = canSave ? '保存更改' : '修正后保存'
  }

  function renderActionAvailability(snapshot, validation) {
    const effectiveSnapshot = snapshot || lastSnapshot
    if (!effectiveSnapshot) {
      return
    }

    lastSnapshot = effectiveSnapshot
    const effectiveValidation = validation || resolveConfigValidation()
    const runtimeState = normalizedValue(effectiveSnapshot?.runtime?.state)
    const runtimeActive = activeRuntimeStates.has(runtimeState)
    const accessibility = normalizedValue(effectiveSnapshot?.runtime?.permissions?.accessibility)
    const screenCapture = normalizedValue(effectiveSnapshot?.runtime?.permissions?.screen_capture)

    setButtonAvailability('startButton', {
      disabled: !effectiveValidation.canStart || runtimeActive,
    })
    setButtonAvailability('stopButton', {
      disabled: !runtimeActive,
    })
    setButtonAvailability('openAccessibilityButton', {
      emphasis: accessibility === 'granted' ? 'secondary' : 'default',
    })
    setButtonAvailability('requestScreenCaptureButton', {
      disabled: screenCapture === 'granted' || screenCapture === 'unsupported',
      emphasis: screenCapture === 'granted' ? 'secondary' : 'default',
    })
    setButtonAvailability('openConsoleButton', {
      disabled: !effectiveValidation.canOpenConsole,
    })

    if (!effectiveValidation.bootstrap.configured) {
      setActionStatus('runtimeActionStatus', '未配置', 'neutral')
    } else if (runtimeActive) {
      setActionStatus('runtimeActionStatus', '运行中', 'connected')
    } else if (!effectiveValidation.canStart) {
      setActionStatus('runtimeActionStatus', '待修正', 'error')
    } else {
      setActionStatus('runtimeActionStatus', '可启动', 'starting')
    }

    if (accessibility === 'granted' && screenCapture === 'granted') {
      setActionStatus('permissionActionStatus', '已就绪', 'connected')
    } else if (accessibility === 'granted' && screenCapture === 'unsupported') {
      setActionStatus('permissionActionStatus', '部分就绪', 'starting')
    } else {
      setActionStatus('permissionActionStatus', '需授权', 'neutral')
    }

    if (!effectiveValidation.console.configured) {
      setActionStatus('consoleActionStatus', '未配置', 'neutral')
    } else if (!effectiveValidation.console.ok) {
      setActionStatus('consoleActionStatus', '待修正', 'error')
    } else {
      setActionStatus('consoleActionStatus', '已配置', 'connected')
    }
  }

  function renderDiagnostics(details) {
    const peersCount = details?.peersCount || 0
    const errorText = details?.errorText || ''
    const hasError = Boolean(errorText)
    const logText = details?.logText || ''
    const logCount = details?.logCount || 0
    const hasLogs = Boolean(logText)
    const hasPeers = peersCount > 0
    const hasDiagnostics = hasPeers || hasError || hasLogs
    const summaryParts = []

    setPreText('peersValue', details?.peersText || '')
    setPreText('errorValue', errorText, hasError ? 'error' : '')
    setPreText('logsValue', logText)
    setHidden('diagnosticsBody', !hasDiagnostics)
    setHidden('diagnosticsPeersSection', !hasPeers)
    setHidden('diagnosticsErrorSection', !hasError)
    setHidden('diagnosticsLogsSection', !hasLogs)
    document.getElementById('diagnosticsCard').dataset.empty = hasDiagnostics ? 'false' : 'true'

    if (hasPeers) {
      summaryParts.push(`节点 ${peersCount}`)
    }
    if (hasError) {
      summaryParts.push('错误 1')
    }
    if (hasLogs) {
      summaryParts.push(`日志 ${logCount}`)
    }

    document.getElementById('diagnosticsSummaryText').textContent = hasDiagnostics
      ? summaryParts.join(' · ')
      : '当前还没有新的节点、错误或运行日志。'
  }

  function renderSnapshot(snapshot, options) {
    const forceSync = options && options.forceSync
    lastSnapshot = snapshot
    syncForm(snapshot, forceSync)
    const validation = renderConfigValidation()
    renderSaveDirtyState(validation)
    appendLogs(snapshot?.runtime?.recent_logs)

    const actionError = snapshot?.runtime?.action_error || ''
    const runtimeError = snapshot?.runtime?.last_error || ''
    const errorText = actionError || runtimeError || ''
    const nextState = snapshot?.runtime?.state || 'Unknown'
    const stateElement = document.getElementById('stateValue')

    stateElement.textContent = nextState
    stateElement.dataset.stateTone = stateTone(nextState, Boolean(actionError || runtimeError))
    document.getElementById('peerValue').textContent = snapshot?.runtime?.peer_id || 'Not available'
    document.getElementById('bootstrapValue').textContent = snapshot?.runtime?.bootstrap_address || 'Not available'
    document.getElementById('bridgeValue').textContent = snapshot?.runtime?.bridge_class_name || 'Not available'
    document.getElementById('accessibilityValue').textContent = snapshot?.runtime?.permissions?.accessibility || 'unknown'
    document.getElementById('screenCaptureValue').textContent = snapshot?.runtime?.permissions?.screen_capture || 'unknown'
    renderActionAvailability(snapshot, validation)
    renderDiagnostics({
      peersText: renderPeers(snapshot?.runtime?.discovered_peers || []),
      peersCount: Array.isArray(snapshot?.runtime?.discovered_peers) ? snapshot.runtime.discovered_peers.length : 0,
      errorText,
      logText: logState.join('\n\n'),
      logCount: logState.length,
    })
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
    for (const id of ['bootstrapInput', 'consoleUrlInput']) {
      document.getElementById(id).addEventListener('input', () => {
        inputsDirty = true
        const validation = renderConfigValidation()
        renderSaveDirtyState(validation)
        renderActionAvailability(lastSnapshot, validation)
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

    document.getElementById('requestScreenCaptureButton').addEventListener('click', () => {
      withBridge((host) => host.requestScreenCapturePermission())
    })

    document.getElementById('openConsoleButton').addEventListener('click', () => {
      withBridge((host) => {
        const result = parseJson(host.openConsoleUrl(value('consoleUrlInput')), { ok: false, error: 'invalid_console_url' })
        if (!result.ok) {
          renderDiagnostics({
            peersText: renderPeers(lastSnapshot?.runtime?.discovered_peers || []),
            peersCount: Array.isArray(lastSnapshot?.runtime?.discovered_peers) ? lastSnapshot.runtime.discovered_peers.length : 0,
            errorText: '请先填写可访问的 gomtmui URL。',
            logText: logState.join('\n\n'),
            logCount: logState.length,
          })
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
