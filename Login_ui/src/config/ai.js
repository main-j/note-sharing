const DEFAULT_AI_BFF_ORIGIN = 'http://localhost:8003'

export const AI_PROTOCOL_VERSION = '1.0'

export const AI_STORAGE_KEYS = {
  enabled: 'folio.ai.enabled',
  panelOpen: 'folio.ai.panel.open',
  shellMode: 'folio.ai.shell.mode',
  panelPinned: 'folio.ai.panel.pinned'
}

export const AI_MESSAGE_TYPES = {
  READY: 'ai.ready',
  CHAT: 'ai.chat',
  CHAT_DELTA: 'ai.chat.delta',
  CHAT_DONE: 'ai.chat.done',
  CHAT_ERROR: 'ai.chat.error',
  ROUTE: 'ai.route',
  CONTEXT: 'ai.context'
}

export function getAiBffOrigin() {
  return process.env.VUE_APP_AI_BFF_ORIGIN || DEFAULT_AI_BFF_ORIGIN
}

export function getAiShellUrl() {
  const origin = getAiBffOrigin()
  return `${origin.replace(/\/$/, '')}/shell`
}

export function getAiTargetOrigin() {
  try {
    return new URL(getAiBffOrigin()).origin
  } catch (error) {
    return DEFAULT_AI_BFF_ORIGIN
  }
}

export function isValidAiOrigin(origin) {
  try {
    return new URL(origin).origin === new URL(getAiBffOrigin()).origin
  } catch (error) {
    return false
  }
}
