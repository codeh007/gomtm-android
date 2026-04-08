import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const scriptSource = readFileSync(new URL('../../main/assets/bootstrap/app.js', import.meta.url), 'utf8')

test('buttons follow runtime snapshot availability and grouping status', () => {
  let snapshot = buildSnapshot({
    state: 'Unknown',
    bootstrapAddress: '',
    consoleUrl: '',
    accessibility: 'not_granted',
    screenCapture: 'not_granted',
  })

  const { elements, triggerInput, click } = bootstrapPageHarness(() => snapshot)

  assert.equal(elements.startButton.disabled, true)
  assert.equal(elements.stopButton.disabled, true)
  assert.equal(elements.openConsoleButton.disabled, true)
  assert.equal(elements.openAccessibilityButton.disabled, false)
  assert.equal(elements.requestScreenCaptureButton.disabled, false)
  assert.equal(elements.runtimeActionStatus.textContent, '未配置')
  assert.equal(elements.permissionActionStatus.textContent, '需授权')
  assert.equal(elements.consoleActionStatus.textContent, '未配置')

  elements.bootstrapInput.value = '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap'
  triggerInput('bootstrapInput')

  elements.consoleUrlInput.value = 'https://gomtm.example.com/dash/p2p'
  triggerInput('consoleUrlInput')

  assert.equal(elements.startButton.disabled, false)
  assert.equal(elements.runtimeActionStatus.textContent, '可启动')

  snapshot = buildSnapshot({
    state: 'Connected',
    bootstrapAddress: '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap',
    consoleUrl: 'https://gomtm.example.com/dash/p2p',
    accessibility: 'granted',
    screenCapture: 'granted',
  })
  click('refreshButton')

  assert.equal(elements.startButton.disabled, true)
  assert.equal(elements.stopButton.disabled, false)
  assert.equal(elements.openAccessibilityButton.dataset.availability, 'secondary')
  assert.equal(elements.requestScreenCaptureButton.disabled, true)
  assert.equal(elements.openConsoleButton.disabled, false)
  assert.equal(elements.runtimeActionStatus.textContent, '运行中')
  assert.equal(elements.permissionActionStatus.textContent, '已就绪')
  assert.equal(elements.consoleActionStatus.textContent, '已配置')
})

test('save button stays quiet until config becomes dirty', () => {
  const snapshot = buildSnapshot({
    state: 'Unknown',
    bootstrapAddress: '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap',
    consoleUrl: 'https://gomtm.example.com/dash/p2p',
    accessibility: 'not_granted',
    screenCapture: 'not_granted',
  })

  const { elements, triggerInput, click } = bootstrapPageHarness(() => snapshot)

  assert.equal(elements.configDirtyBar.dataset.dirty, 'false')
  assert.equal(elements.configDirtyText.textContent, '配置已保存')
  assert.equal(elements.saveButton.hidden, true)
  assert.equal(elements.saveButton.disabled, true)

  elements.consoleUrlInput.value = 'https://gomtm.example.com/dash/p2p?tab=host'
  triggerInput('consoleUrlInput')

  assert.equal(elements.configDirtyBar.dataset.dirty, 'true')
  assert.equal(elements.configDirtyText.textContent, '有未保存更改')
  assert.equal(elements.saveButton.hidden, false)
  assert.equal(elements.saveButton.disabled, false)
  assert.equal(elements.saveButtonText.textContent, '保存更改')

  click('saveButton')

  assert.equal(elements.configDirtyBar.dataset.dirty, 'false')
  assert.equal(elements.configDirtyText.textContent, '配置已保存')
  assert.equal(elements.saveButton.hidden, true)
  assert.equal(elements.saveButton.disabled, true)
})

test('diagnostics stay compact until real runtime output appears', () => {
  let snapshot = buildSnapshot({
    state: 'Ready',
    bootstrapAddress: '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap',
    consoleUrl: 'https://gomtm.example.com/dash/p2p',
    accessibility: 'granted',
    screenCapture: 'granted',
  })

  const { elements, click } = bootstrapPageHarness(() => snapshot)

  assert.equal(elements.diagnosticsCard.dataset.empty, 'true')
  assert.equal(elements.diagnosticsBody.hidden, true)
  assert.equal(elements.diagnosticsPeersSection.hidden, true)
  assert.equal(elements.diagnosticsErrorSection.hidden, true)
  assert.equal(elements.diagnosticsLogsSection.hidden, true)
  assert.equal(elements.diagnosticsSummaryText.textContent, '当前还没有新的节点、错误或运行日志。')

  snapshot = buildSnapshot({
    state: 'Connected',
    bootstrapAddress: '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap',
    consoleUrl: 'https://gomtm.example.com/dash/p2p',
    accessibility: 'granted',
    screenCapture: 'granted',
    actionError: '打开控制台失败',
    recentLogs: 'connected to relay',
    discoveredPeers: [
      {
        peer_id: '12D3KooW-android',
        name: 'android-host',
        state: 'Connected',
        last_seen_at: '2026-04-01T12:00:00.000Z',
      },
    ],
  })

  click('refreshButton')

  assert.equal(elements.diagnosticsCard.dataset.empty, 'false')
  assert.equal(elements.diagnosticsBody.hidden, false)
  assert.equal(elements.diagnosticsPeersSection.hidden, false)
  assert.equal(elements.diagnosticsErrorSection.hidden, false)
  assert.equal(elements.diagnosticsLogsSection.hidden, false)
  assert.match(elements.diagnosticsSummaryText.textContent, /节点 1/) 
  assert.match(elements.diagnosticsSummaryText.textContent, /错误 1/)
  assert.match(elements.diagnosticsSummaryText.textContent, /日志 1/)
})

test('validation feedback stays in sync with save start and console actions', () => {
  const snapshot = buildSnapshot({
    state: 'Ready',
    bootstrapAddress: '',
    consoleUrl: '',
    accessibility: 'not_granted',
    screenCapture: 'not_granted',
  })

  const { elements, triggerInput } = bootstrapPageHarness(() => snapshot)

  assert.equal(elements.bootstrapValidationText.textContent, '请输入 Bootstrap 地址后再保存或启动节点。')
  assert.equal(elements.bootstrapValidationText.dataset.tone, 'error')
  assert.equal(elements.consoleUrlValidationText.textContent, '未配置 gomtmui URL，保存后可在 App 内打开控制台。')
  assert.equal(elements.consoleUrlValidationText.dataset.tone, 'muted')
  assert.equal(elements.startButton.disabled, true)
  assert.equal(elements.openConsoleButton.disabled, true)

  elements.bootstrapInput.value = '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap'
  triggerInput('bootstrapInput')

  assert.equal(elements.bootstrapValidationText.textContent, '当前 Bootstrap 地址可用于保存并启动节点。')
  assert.equal(elements.bootstrapValidationText.dataset.tone, 'connected')
  assert.equal(elements.startButton.disabled, false)
  assert.equal(elements.saveButton.hidden, false)
  assert.equal(elements.saveButton.disabled, false)

  elements.consoleUrlInput.value = 'javascript:alert(1)'
  triggerInput('consoleUrlInput')

  assert.equal(elements.consoleUrlValidationText.textContent, '请输入可访问的 http 或 https gomtmui URL。')
  assert.equal(elements.consoleUrlValidationText.dataset.tone, 'error')
  assert.equal(elements.consoleActionStatus.textContent, '待修正')
  assert.equal(elements.startButton.disabled, true)
  assert.equal(elements.openConsoleButton.disabled, true)
  assert.equal(elements.saveButton.disabled, true)
  assert.equal(elements.configDirtyText.textContent, '修正输入后再保存')

  elements.consoleUrlInput.value = 'gomtm.example.com'
  triggerInput('consoleUrlInput')

  assert.equal(elements.consoleUrlValidationText.textContent, '将打开 https://gomtm.example.com/dash/p2p')
  assert.equal(elements.consoleUrlValidationText.dataset.tone, 'connected')
  assert.equal(elements.consoleActionStatus.textContent, '已配置')
  assert.equal(elements.startButton.disabled, false)
  assert.equal(elements.openConsoleButton.disabled, false)
  assert.equal(elements.saveButton.disabled, false)
  assert.equal(elements.configDirtyText.textContent, '有未保存更改')
})

function bootstrapPageHarness(getSnapshot) {
  const domContentLoadedListeners = []
  const elements = createElements()

  const document = {
    getElementById(id) {
      const element = elements[id]
      if (!element) {
        throw new Error(`missing element: ${id}`)
      }
      return element
    },
    addEventListener(type, listener) {
      if (type === 'DOMContentLoaded') {
        domContentLoadedListeners.push(listener)
      }
    },
  }

  const window = {
    document,
    setInterval() {},
    GomtmAndroid: {
      getRuntimeSnapshot() {
        return JSON.stringify(getSnapshot())
      },
      saveHostSettings() {
        return JSON.stringify(getSnapshot())
      },
      startNode() {
        return JSON.stringify(getSnapshot())
      },
      stopNode() {
        return JSON.stringify(getSnapshot())
      },
      openAccessibilitySettings() {
        return true
      },
      requestScreenCapturePermission() {
        return true
      },
      openConsoleUrl() {
        return JSON.stringify({ ok: true, url: 'https://gomtm.console.invalid/dash/p2p' })
      },
      validateConsoleUrl(raw) {
        const trimmed = String(raw || '').trim()
        if (!trimmed) {
          return JSON.stringify({ ok: true, configured: false, canonical_url: '' })
        }
        if (trimmed.startsWith('javascript:')) {
          return JSON.stringify({
            ok: false,
            error: 'invalid_console_url',
            message: '请输入可访问的 http 或 https gomtmui URL。',
          })
        }
        const candidate = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(trimmed) ? trimmed : `https://${trimmed}`
        const canonical = new URL(candidate)
        const path = canonical.pathname && canonical.pathname !== '/' ? canonical.pathname : '/dash/p2p'
        return JSON.stringify({
          ok: true,
          configured: true,
          canonical_url: `${canonical.protocol}//${canonical.host}${path}${canonical.search}${canonical.hash}`,
        })
      },
    },
  }

  vm.runInNewContext(scriptSource, { window, document, JSON })
  for (const listener of domContentLoadedListeners) {
    listener()
  }

  return {
    elements,
    triggerInput(id) {
      elements[id].dispatch('input')
    },
    click(id) {
      elements[id].dispatch('click')
    },
  }
}

function createElements() {
  const elementIds = [
    'bootstrapInput',
    'consoleUrlInput',
    'saveButton',
    'refreshButton',
    'startButton',
    'stopButton',
    'openAccessibilityButton',
    'requestScreenCaptureButton',
    'openConsoleButton',
    'stateValue',
    'peerValue',
    'bootstrapValue',
    'bridgeValue',
    'accessibilityValue',
    'screenCaptureValue',
    'peersValue',
    'errorValue',
    'logsValue',
    'configDirtyBar',
    'configDirtyText',
    'saveButtonText',
    'bootstrapValidationText',
    'consoleUrlValidationText',
    'runtimeActionStatus',
    'permissionActionStatus',
    'consoleActionStatus',
    'diagnosticsCard',
    'diagnosticsSummaryText',
    'diagnosticsBody',
    'diagnosticsPeersSection',
    'diagnosticsErrorSection',
    'diagnosticsLogsSection',
  ]

  return Object.fromEntries(elementIds.map((id) => [id, createElement(id)]))
}

function createElement(id) {
  const listeners = new Map()
  return {
    id,
    value: '',
    textContent: '',
    className: '',
    disabled: false,
    hidden: false,
    dataset: {},
    attributes: {},
    addEventListener(type, listener) {
      listeners.set(type, listener)
    },
    setAttribute(name, value) {
      this.attributes[name] = value
    },
    dispatch(type) {
      const listener = listeners.get(type)
      if (!listener) {
        throw new Error(`missing ${type} listener for ${id}`)
      }
      listener()
    },
  }
}

function buildSnapshot({
  state,
  bootstrapAddress,
  consoleUrl,
  accessibility,
  screenCapture,
  actionError = '',
  recentLogs = '',
  discoveredPeers = [],
}) {
  return {
    host: {
      available: true,
      surface: 'android_webview',
      surface_version: 'v2',
      bridge_name: 'GomtmAndroid',
    },
    config: {
      bootstrap_address: bootstrapAddress,
      console_url: consoleUrl,
    },
    runtime: {
      state,
      peer_id: '12D3KooW-test',
      bootstrap_address: bootstrapAddress,
      last_error: '',
      action_error: actionError,
      recent_logs: recentLogs,
      bridge_class_name: 'io.nekohasekai.p2pandroid.P2pandroid',
      permissions: {
        accessibility,
        screen_capture: screenCapture,
      },
      discovered_peers: discoveredPeers,
    },
  }
}
