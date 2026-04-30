<template>
  <div class="ai-page">
    <!-- 顶部条 -->
    <header class="ai-topbar">
      <div class="flex items-center gap-3 min-w-0">
        <div class="ai-brand-icon">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <div class="min-w-0">
          <div class="ai-brand-title">小智 · AI 投资助手</div>
          <div class="ai-brand-sub">
            <span class="status-dot"></span>
            <span>基于通义千问 · 投资问答 · 技术指标 · 交易常识</span>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <el-button v-if="streaming" type="danger" plain size="small" @click="stopStream">
          <el-icon class="mr-1"><CircleClose /></el-icon>停止生成
        </el-button>
        <el-button :disabled="streaming || !messages.length" plain size="small" @click="clearChat">
          <el-icon class="mr-1"><Delete /></el-icon>清空对话
        </el-button>
      </div>
    </header>

    <!-- 消息列表 -->
    <div ref="scrollRef" class="ai-scroll">
      <div class="ai-thread">
        <!-- 空状态 -->
        <div v-if="!messages.length" class="ai-empty">
          <div class="ai-empty-hero">
            <div class="ai-empty-orb">
              <el-icon class="text-3xl"><ChatDotRound /></el-icon>
            </div>
            <h3>你好，我是小智 👋</h3>
            <p>问我关于股票、技术指标、交易常识的任何问题</p>
          </div>
          <div class="ai-suggest-grid">
            <button v-for="(s, idx) in suggestions" :key="idx"
                    class="ai-suggest" @click="askQuick(s.q)">
              <div class="ai-suggest-icon" :style="{ background: s.bg, color: s.color }">
                <el-icon><component :is="s.icon" /></el-icon>
              </div>
              <div class="ai-suggest-text">
                <div class="ai-suggest-title">{{ s.title }}</div>
                <div class="ai-suggest-q">{{ s.q }}</div>
              </div>
              <el-icon class="ai-suggest-arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>

        <!-- 对话气泡 -->
        <div v-for="(m, i) in messages" :key="i"
             class="ai-row" :class="m.role === 'user' ? 'is-user' : 'is-bot'">
          <div class="ai-avatar" :class="m.role === 'user' ? 'is-user' : 'is-bot'">
            <span v-if="m.role === 'user'">{{ userChar }}</span>
            <el-icon v-else><ChatDotRound /></el-icon>
          </div>
          <div class="ai-bubble-wrap">
            <div class="ai-meta">
              <span class="ai-meta-name">{{ m.role === 'user' ? userName : '小智' }}</span>
              <span v-if="m.streaming" class="ai-meta-streaming">生成中…</span>
            </div>
            <div class="ai-bubble" :class="m.role === 'user' ? 'is-user' : 'is-bot'">
              <!-- 思考过程（仅思考型模型有） -->
              <details v-if="m.reasoning" class="ai-reasoning">
                <summary>
                  <el-icon class="mr-1"><Cpu /></el-icon>
                  <span>思考过程</span>
                  <span class="ai-reasoning-len">{{ m.reasoning.length }} 字</span>
                </summary>
                <div class="ai-reasoning-body" v-html="renderMarkdown(m.reasoning)"></div>
              </details>

              <!-- 主要内容 -->
              <div v-if="m.content" class="ai-content"
                   :class="{ 'is-streaming': m.streaming }"
                   v-html="renderMarkdown(m.content)"></div>
              <div v-else-if="m.streaming && !m.reasoning" class="ai-thinking">
                <span class="ai-dot"></span><span class="ai-dot"></span><span class="ai-dot"></span>
                <span class="ai-thinking-text">正在思考</span>
              </div>

              <div v-if="m.error" class="ai-error">
                <el-icon><Warning /></el-icon>{{ m.error }}
              </div>
            </div>

            <!-- 助手消息底部操作栏 -->
            <div v-if="m.role === 'assistant' && !m.streaming && m.content" class="ai-actions">
              <button class="ai-action-btn" @click="copyText(m.content)" title="复制全文">
                <el-icon><CopyDocument /></el-icon>
                <span>复制</span>
              </button>
              <button v-if="i === messages.length - 1"
                      class="ai-action-btn" @click="regenerate" title="基于上一条问题重新生成">
                <el-icon><RefreshRight /></el-icon>
                <span>重新生成</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入栏 -->
    <footer class="ai-inputbar">
      <div class="ai-input-wrap">
        <el-input
          v-model="input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          :placeholder="streaming ? '正在生成中…' : '输入你的问题（Enter 发送，Shift+Enter 换行）'"
          :disabled="streaming"
          resize="none"
          class="ai-input"
          @keydown.enter.exact.prevent="send"
          @keydown.enter.shift.exact="onShiftEnter"
        />
        <div class="ai-input-meta">
          <span class="ai-counter" :class="{ 'is-warn': input.length > 1500 }">
            {{ input.length }} 字
          </span>
          <button class="ai-send-btn" :disabled="!input.trim() || streaming" @click="send">
            <el-icon v-if="!streaming"><Promotion /></el-icon>
            <el-icon v-else class="is-spin"><Loading /></el-icon>
            <span>{{ streaming ? '生成中' : '发送' }}</span>
          </button>
        </div>
      </div>
      <div class="ai-disclaimer">
        AI 生成内容仅供参考，不构成投资建议
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound, Delete, CircleClose, Promotion, Cpu, ArrowRight,
  Warning, CopyDocument, RefreshRight, Loading,
  TrendCharts, Aim, DataAnalysis, Compass
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const STORAGE_KEY = 'ai_chat_messages_v1'

const messages = ref([])     // [{ role, content, reasoning?, streaming?, error? }]
const input = ref('')
const streaming = ref(false)
const scrollRef = ref(null)
let abortCtrl = null

const userName = computed(() =>
  userStore.userInfo?.nickname || userStore.userInfo?.username || '我'
)
const userChar = computed(() => String(userName.value).charAt(0).toUpperCase())

const suggestions = [
  {
    title: '技术指标', q: '什么是 MACD 指标？怎么用它判断买卖点？',
    icon: markRaw(TrendCharts), bg: 'rgba(99,102,241,.15)', color: '#a5b4fc'
  },
  {
    title: '风险管理', q: '止损和止盈应该设置在哪里更合理？',
    icon: markRaw(Aim), bg: 'rgba(244,63,94,.15)', color: '#fda4af'
  },
  {
    title: '基础概念', q: '解释一下"换手率"这个指标，多少算正常？',
    icon: markRaw(DataAnalysis), bg: 'rgba(16,185,129,.15)', color: '#6ee7b7'
  },
  {
    title: '策略思考', q: '近期 A 股震荡，普通投资者该如何应对？',
    icon: markRaw(Compass), bg: 'rgba(245,158,11,.15)', color: '#fcd34d'
  }
]

// ==================== Markdown 渲染 ====================
marked.setOptions({
  breaks: true,    // 单换行 = <br>
  gfm: true        // GitHub flavor (表格 / 删除线 / 任务列表)
})

const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    const html = marked.parse(String(text))
    return DOMPurify.sanitize(html)
  } catch (_) {
    return DOMPurify.sanitize(String(text).replace(/\n/g, '<br>'))
  }
}

// ==================== 持久化 ====================
const loadFromStorage = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) messages.value = JSON.parse(raw)
  } catch (_) { messages.value = [] }
}
const saveToStorage = () => {
  try {
    const clean = messages.value
      .filter(m => m.content)
      .map(m => ({ role: m.role, content: m.content, reasoning: m.reasoning }))
    localStorage.setItem(STORAGE_KEY, JSON.stringify(clean))
  } catch (_) { /* ignore quota */ }
}

const scrollToBottom = () => {
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ==================== 操作 ====================
const askQuick = (q) => {
  if (streaming.value) return
  input.value = q
  send()
}

const onShiftEnter = () => { /* default newline behavior */ }

const copyText = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (_) {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

const regenerate = async () => {
  if (streaming.value || messages.value.length < 2) return
  // 移除最后一条 assistant
  const last = messages.value[messages.value.length - 1]
  if (last.role !== 'assistant') return
  messages.value.pop()
  // 找到上一个 user 问题，作为重发输入
  const lastUser = [...messages.value].reverse().find(m => m.role === 'user')
  if (!lastUser) return
  // 移除最后一个 user 我们一会儿在 send 里重新 push
  const idx = messages.value.lastIndexOf(lastUser)
  if (idx >= 0) messages.value.splice(idx, 1)
  input.value = lastUser.content
  await nextTick()
  send()
}

const send = async () => {
  const q = input.value.trim()
  if (!q || streaming.value) return
  input.value = ''

  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  // 重要：从 reactive 数组取回代理，避免直接改原对象绕过响应式
  const botMsg = messages.value[messages.value.length - 1]
  saveToStorage()
  scrollToBottom()

  streaming.value = true
  abortCtrl = new AbortController()

  // 拼上下文：仅完成的消息
  const payload = {
    messages: messages.value
      .filter(m => m !== botMsg && m.content)
      .map(m => ({ role: m.role, content: m.content }))
  }

  try {
    const resp = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Authorization': 'Bearer ' + (userStore.token || '')
      },
      body: JSON.stringify(payload),
      signal: abortCtrl.signal
    })

    if (!resp.ok || !resp.body) {
      throw new Error('HTTP ' + resp.status)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    let currentEvent = 'message'
    let dataLines = []

    const dispatchEvent = () => {
      if (!dataLines.length) { currentEvent = 'message'; return }
      const data = dataLines.join('\n')
      dataLines = []
      if (currentEvent === 'delta') {
        botMsg.content += data
        scrollToBottom()
      } else if (currentEvent === 'reasoning') {
        if (!botMsg.reasoning) botMsg.reasoning = ''
        botMsg.reasoning += data
        scrollToBottom()
      } else if (currentEvent === 'error') {
        botMsg.error = data
      }
      currentEvent = 'message'
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buf.indexOf('\n')) >= 0) {
        const rawLine = buf.slice(0, idx)
        buf = buf.slice(idx + 1)
        const line = rawLine.replace(/\r$/, '')
        if (!line) { dispatchEvent(); continue }
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).replace(/^ /, ''))
        }
      }
    }
    dispatchEvent()
  } catch (e) {
    if (e.name !== 'AbortError') {
      botMsg.error = e.message || '请求失败'
      ElMessage.error('AI 调用失败：' + (e.message || e))
    } else {
      botMsg.error = '已停止'
    }
  } finally {
    botMsg.streaming = false
    streaming.value = false
    abortCtrl = null
    saveToStorage()
    scrollToBottom()
  }
}

const stopStream = () => {
  if (abortCtrl) abortCtrl.abort()
}

const clearChat = () => {
  messages.value = []
  saveToStorage()
}

onMounted(() => {
  loadFromStorage()
  scrollToBottom()
})
onUnmounted(() => {
  if (abortCtrl) abortCtrl.abort()
})
</script>

<style scoped>
/* ============ 主容器 ============ */
.ai-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at 0% 0%, rgba(99, 102, 241, 0.08), transparent 40%),
    radial-gradient(circle at 100% 100%, rgba(59, 130, 246, 0.06), transparent 40%),
    #0b0c10;
  font-feature-settings: 'tnum';
}

/* ============ 顶部条 ============ */
.ai-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(11, 12, 16, 0.6);
  backdrop-filter: blur(8px);
}
.ai-brand-icon {
  width: 40px; height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1 0%, #3b82f6 100%);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 18px;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
}
.ai-brand-title {
  color: #f3f4f6;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.ai-brand-sub {
  color: #9ca3af;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}
.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.7);
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1 }
  50% { opacity: 0.5 }
}

/* ============ 消息流 ============ */
.ai-scroll {
  flex: 1;
  overflow-y: auto;
  scroll-behavior: smooth;
}
.ai-thread {
  max-width: 860px;
  margin: 0 auto;
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

/* ============ 空状态 ============ */
.ai-empty {
  padding: 40px 0 24px;
}
.ai-empty-hero {
  text-align: center;
  margin-bottom: 32px;
}
.ai-empty-orb {
  width: 64px; height: 64px;
  margin: 0 auto 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.3), rgba(59, 130, 246, 0.15));
  border: 1px solid rgba(255, 255, 255, 0.1);
  display: flex; align-items: center; justify-content: center;
  color: #a5b4fc;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.2);
}
.ai-empty-hero h3 {
  color: #f3f4f6;
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 6px;
}
.ai-empty-hero p {
  color: #9ca3af;
  font-size: 13px;
  margin: 0;
}
.ai-suggest-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-width: 720px;
  margin: 0 auto;
}
@media (max-width: 720px) {
  .ai-suggest-grid { grid-template-columns: 1fr }
}
.ai-suggest {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  cursor: pointer;
  transition: all .2s ease;
  text-align: left;
  color: inherit;
}
.ai-suggest:hover {
  background: rgba(99, 102, 241, 0.06);
  border-color: rgba(99, 102, 241, 0.35);
  transform: translateY(-1px);
}
.ai-suggest-icon {
  flex-shrink: 0;
  width: 36px; height: 36px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
}
.ai-suggest-text { min-width: 0; flex: 1 }
.ai-suggest-title {
  color: #e5e7eb;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}
.ai-suggest-q {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ai-suggest-arrow {
  color: #6b7280;
  flex-shrink: 0;
  transition: transform .2s ease;
}
.ai-suggest:hover .ai-suggest-arrow {
  color: #a5b4fc;
  transform: translateX(2px);
}

/* ============ 消息行 ============ */
.ai-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.ai-avatar {
  flex-shrink: 0;
  width: 34px; height: 34px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}
.ai-avatar.is-user {
  background: linear-gradient(135deg, #6366f1, #3b82f6);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.3);
}
.ai-avatar.is-bot {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.3);
  font-size: 16px;
}
.ai-bubble-wrap {
  flex: 1;
  min-width: 0;
}
.ai-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #9ca3af;
}
.ai-meta-name { font-weight: 600; color: #d1d5db }
.ai-meta-streaming {
  color: #818cf8;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.ai-meta-streaming::before {
  content: '';
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #818cf8;
  animation: pulse 1s infinite;
}

/* ============ 气泡 ============ */
.ai-bubble {
  padding: 14px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.75;
  color: #e5e7eb;
  word-break: break-word;
  border: 1px solid transparent;
}
.ai-bubble.is-user {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(59, 130, 246, 0.12));
  border-color: rgba(99, 102, 241, 0.25);
  color: #e0e7ff;
}
.ai-bubble.is-bot {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.06);
}

/* ============ Markdown 内容 ============ */
.ai-content { color: #e5e7eb; line-height: 1.75 }
.ai-content :deep(p) { margin: 0.6em 0 }
.ai-content :deep(p:first-child) { margin-top: 0 }
.ai-content :deep(p:last-child) { margin-bottom: 0 }

.ai-content :deep(h1),
.ai-content :deep(h2),
.ai-content :deep(h3),
.ai-content :deep(h4) {
  margin: 1.2em 0 0.6em;
  font-weight: 600;
  color: #f3f4f6;
  letter-spacing: 0.2px;
}
.ai-content :deep(h1) { font-size: 1.3em }
.ai-content :deep(h2) { font-size: 1.18em; padding-bottom: 6px; border-bottom: 1px solid rgba(255,255,255,.08) }
.ai-content :deep(h3) {
  font-size: 1.06em;
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-content :deep(h3::before) {
  content: '';
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, #818cf8, #6366f1);
  flex-shrink: 0;
}
.ai-content :deep(h4) { font-size: 1em; color: #c7d2fe }
.ai-content :deep(h1:first-child),
.ai-content :deep(h2:first-child),
.ai-content :deep(h3:first-child),
.ai-content :deep(h4:first-child) { margin-top: 0 }

.ai-content :deep(strong) { color: #fff; font-weight: 600 }
.ai-content :deep(em) { color: #c7d2fe }
.ai-content :deep(del) { color: #6b7280 }

.ai-content :deep(ul),
.ai-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.6em;
}
.ai-content :deep(li) { margin: 0.25em 0 }
.ai-content :deep(li::marker) { color: #818cf8 }

.ai-content :deep(blockquote) {
  margin: 0.6em 0;
  padding: 8px 14px;
  border-left: 3px solid #818cf8;
  background: rgba(99, 102, 241, 0.06);
  border-radius: 0 8px 8px 0;
  color: #c7d2fe;
}
.ai-content :deep(blockquote p) { margin: 0 }

.ai-content :deep(code) {
  background: rgba(0, 0, 0, 0.4);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #fbbf24;
}
.ai-content :deep(pre) {
  margin: 0.8em 0;
  padding: 12px 14px;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.6;
}
.ai-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #e5e7eb;
}

.ai-content :deep(table) {
  border-collapse: collapse;
  margin: 0.8em 0;
  font-size: 13px;
  width: 100%;
}
.ai-content :deep(th),
.ai-content :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 6px 10px;
  text-align: left;
}
.ai-content :deep(th) {
  background: rgba(255, 255, 255, 0.04);
  color: #f3f4f6;
  font-weight: 600;
}
.ai-content :deep(tr:nth-child(2n) td) {
  background: rgba(255, 255, 255, 0.015);
}

.ai-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin: 1.2em 0;
}

.ai-content :deep(a) {
  color: #93c5fd;
  text-decoration: none;
  border-bottom: 1px dashed rgba(147, 197, 253, 0.4);
}
.ai-content :deep(a:hover) {
  color: #bfdbfe;
  border-bottom-style: solid;
}

/* 流式时光标 */
.ai-content.is-streaming :deep(p:last-child)::after,
.ai-content.is-streaming :deep(li:last-child)::after {
  content: '▋';
  display: inline-block;
  margin-left: 2px;
  color: #818cf8;
  animation: blink 1s steps(2) infinite;
}
@keyframes blink {
  to { visibility: hidden }
}

/* ============ 思考过程 ============ */
.ai-reasoning {
  margin-bottom: 12px;
  border: 1px solid rgba(99, 102, 241, 0.25);
  background: rgba(99, 102, 241, 0.05);
  border-radius: 8px;
  overflow: hidden;
}
.ai-reasoning summary {
  padding: 8px 12px;
  color: #a5b4fc;
  cursor: pointer;
  user-select: none;
  list-style: none;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  transition: background .15s;
}
.ai-reasoning summary:hover { background: rgba(99, 102, 241, 0.08) }
.ai-reasoning summary::-webkit-details-marker { display: none }
.ai-reasoning-len {
  margin-left: auto;
  color: #6b7280;
  font-size: 11px;
}
.ai-reasoning-body {
  padding: 8px 12px 10px;
  border-top: 1px solid rgba(99, 102, 241, 0.18);
  color: #9ca3af;
  font-size: 12.5px;
  line-height: 1.7;
}

/* ============ 思考 / 错误 ============ */
.ai-thinking {
  display: flex; align-items: center; gap: 4px;
  color: #9ca3af; font-size: 13px;
}
.ai-thinking-text { margin-left: 6px; color: #6b7280; font-size: 12px }
.ai-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #818cf8;
  display: inline-block;
  animation: aiBlink 1.2s infinite ease-in-out;
}
.ai-dot:nth-child(2) { animation-delay: .15s }
.ai-dot:nth-child(3) { animation-delay: .3s }
@keyframes aiBlink {
  0%, 80%, 100% { opacity: .25; transform: scale(.7) }
  40% { opacity: 1; transform: scale(1) }
}

.ai-error {
  margin-top: 8px;
  padding: 6px 10px;
  background: rgba(244, 63, 94, 0.08);
  border: 1px solid rgba(244, 63, 94, 0.25);
  border-radius: 6px;
  color: #fda4af;
  font-size: 12.5px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ============ 助手消息底部操作 ============ */
.ai-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}
.ai-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  transition: all .15s;
}
.ai-action-btn:hover {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.08);
  color: #d1d5db;
}

/* ============ 输入栏 ============ */
.ai-inputbar {
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding: 14px 24px 12px;
  background: rgba(11, 12, 16, 0.6);
  backdrop-filter: blur(8px);
}
.ai-input-wrap {
  max-width: 860px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  transition: border-color .15s;
  padding: 4px 8px 8px;
}
.ai-input-wrap:focus-within {
  border-color: rgba(99, 102, 241, 0.5);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}
.ai-input :deep(.el-textarea__inner) {
  background: transparent !important;
  border: none !important;
  color: #e5e7eb !important;
  font-size: 14px;
  line-height: 1.6;
  padding: 8px 6px 4px;
  box-shadow: none !important;
  resize: none;
}
.ai-input :deep(.el-textarea__inner::placeholder) { color: #6b7280 }
.ai-input-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
}
.ai-counter {
  font-size: 11px;
  color: #6b7280;
  font-family: ui-monospace, monospace;
}
.ai-counter.is-warn { color: #fbbf24 }

.ai-send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: linear-gradient(135deg, #6366f1, #3b82f6);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all .15s;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}
.ai-send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
}
.ai-send-btn:disabled {
  background: rgba(255, 255, 255, 0.05);
  color: #6b7280;
  box-shadow: none;
  cursor: not-allowed;
}
.is-spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg) }
}

.ai-disclaimer {
  text-align: center;
  font-size: 10px;
  color: #4b5563;
  margin-top: 6px;
}

/* ============ 滚动条美化 ============ */
.ai-scroll::-webkit-scrollbar {
  width: 8px;
}
.ai-scroll::-webkit-scrollbar-track { background: transparent }
.ai-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
}
.ai-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.12);
}
</style>
