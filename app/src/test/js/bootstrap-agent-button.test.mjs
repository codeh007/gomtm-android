import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'

const scriptSource = readFileSync(new URL('../../main/assets/bootstrap/app.js', import.meta.url), 'utf8')

test('agent button follows peer and console availability', () => {
  let snapshot = buildSnapshot({
    state: 'Ready',
    peerId: '',
    bootstrapAddress: '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap',
    consoleUrl: 'https://gomtm.example.com/dash/p2p',
    accessibility: 'granted',
    screenCapture: 'granted',
  })

  const { elements, click } = bootstrapPageHarness(() => snapshot)

  assert.equal(elements.openAgentConsoleButton.disabled, true)

  snapshot = buildSnapshot({
    state: 'Connected',
    peerId: '12D3KooW-agent',
    bootstrapAddress: '/ip4/156.233.234.137/tcp/4101/p2p/bootstrap',
    consoleUrl: 'https://gomtm.example.com/dash/p2p',
    accessibility: 'granted',
    screenCapture: 'granted',
  })

  click('refreshButton')

  assert.equal(elements.openAgentConsoleButton.disabled, false)
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
      openPeerAgentConsole() {
        return JSON.stringify({ ok: true, url: 'https://gomtm.console.invalid/dash/p2p/12D3KooW-agent/android' })
      },
      validateConsoleUrl(raw) {
        const trimmed = String(raw || '').trim()
        if (!trimmed) {
          return JSON.stringify({ ok: true, configured: false, canonical_url: '' })
        }
        const candidate = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(trimmed) ? trimmed : `https://${trimmed}`
        return JSON.stringify({ ok: true, configured: true, canonical_url: candidate })
      },
    },
  }

  vm.runInNewContext(scriptSource, { window, document, JSON })
  for (const listener of domContentLoadedListeners) {
    listener()
  }

  return {
    elements,
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
    'openAgentConsoleButton',
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
  peerId,
  bootstrapAddress,
  consoleUrl,
  accessibility,
  screenCapture,
}) {
  return {
    config: {
      bootstrap_address: bootstrapAddress,
      console_url: consoleUrl,
    },
    host: {
      available: true,
    },
    runtime: {
      action_error: '',
      bootstrap_address: bootstrapAddress,
      bridge_class_name: 'io.nekohasekai.p2pandroid.P2pandroid',
      discovered_peers: [],
      last_error: '',
      peer_id: peerId,
      permissions: {
        accessibility,
        screen_capture: screenCapture,
      },
      recent_logs: '',
      state,
    },
  }
}
