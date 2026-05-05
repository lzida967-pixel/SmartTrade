/**
 * AI 聊天助手 API
 *
 * /ai/chat 是 SSE 流式接口，前端用原生 fetch 手动处理事件流，
 * 所以这里只导出 URL 构造器，真正的请求在 AiAssistant.vue 里发起。
 */

/** AI 聊天 SSE 端点（走网关 /api 前缀） */
export const aiChatStreamUrl = () => '/api/ai/chat'
