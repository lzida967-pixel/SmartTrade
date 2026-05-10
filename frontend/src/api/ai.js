/**
 * AI 聊天助手 API
 *
 * /ai/chat 是 SSE 流式接口，前端用原生 fetch 手动处理事件流，
 * 所以这里只导出 URL 构造器，真正的请求在 AiAssistant.vue 里发起。
 */

import request from '../utils/request'

/** AI 聊天 SSE 端点（走网关 /api 前缀） */
export const aiChatStreamUrl = () => '/api/ai/chat'

/** ========== 会话管理 ========== */
export const listSessions = () => request.get('/ai/sessions')
export const createSession = (title = '新对话') => request.post('/ai/sessions', { title })
export const renameSession = (id, title) => request.patch(`/ai/sessions/${id}/title`, { title })
export const deleteSession = (id) => request.delete(`/ai/sessions/${id}`)

/** ========== 消息管理 ========== */
export const getMessages = (sessionId) => request.get(`/ai/sessions/${sessionId}/messages`)
/** messages: [{ role, content, reasoning? }] */
export const saveMessages = (sessionId, messages) =>
  request.post(`/ai/sessions/${sessionId}/messages`, messages)
