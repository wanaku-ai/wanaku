// Regex patterns for OpenAI-style conversation IDs
const THREAD_ID_REGEX = /^thread_[a-zA-Z0-9]{20,40}$/
const CONVERSATION_ID_REGEX = /^conv_[a-zA-Z0-9]{10,40}$/
const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/

// Regex pattern for OpenAI-style conversation IDs - partial string
const FRAGMENT_REGEX = /^[a-zA-Z0-9_\\-]*$/

/**
 * Validates whether a given string matches OpenAI-style conversation ID formats
 * (Assistants API `thread_*`, Realtime `conv_*`, or Web/Compatible `UUIDv4`)
 */
export function isValidConversationId(id: string): boolean {
  return THREAD_ID_REGEX.test(id) || CONVERSATION_ID_REGEX.test(id) || UUID_REGEX.test(id)
}

export function isValidConversationIdFragment(id: string): boolean {
  return FRAGMENT_REGEX.test(id)
}