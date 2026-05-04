import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

/**
 * 通用 SSE 流式报告 composable
 *
 * 协议：
 *   event: delta  / data: <markdown 片段>
 *   event: error  / data: <错误消息>
 *
 * 用法：
 *   const { content, streaming, errorMsg, start, stop, copy } = usePredictionSse({
 *     buildUrl: (code) => `/api/prediction/lgbm/${code}/report?model=lgbm`,
 *     errorHint: 'AI 报告生成失败'
 *   })
 *   start('600519')
 */
export function usePredictionSse(options = {}) {
  const {
    buildUrl,            // (code) => string  必填
    errorHint = 'AI 报告生成失败',
    onBeforeStart,       // 可选：开始前回调
  } = options

  const content = ref('')
  const streaming = ref(false)
  const errorMsg = ref('')
  let abortCtrl = null

  const userStore = useUserStore()

  const stop = () => {
    if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }
  }

  const reset = () => {
    stop()
    content.value = ''
    errorMsg.value = ''
  }

  const start = async (code) => {
    if (!code) { ElMessage.warning('缺少股票代码'); return }
    if (streaming.value) return
    if (typeof onBeforeStart === 'function') {
      const ok = onBeforeStart()
      if (ok === false) return
    }

    content.value = ''
    errorMsg.value = ''
    streaming.value = true
    abortCtrl = new AbortController()

    try {
      const resp = await fetch(buildUrl(code), {
        method: 'GET',
        headers: {
          'Accept': 'text/event-stream',
          'Authorization': 'Bearer ' + (userStore.token || '')
        },
        signal: abortCtrl.signal
      })
      if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status)

      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buf = ''
      let currentEvent = 'message'
      let dataLines = []

      const dispatch = () => {
        if (!dataLines.length) { currentEvent = 'message'; return }
        const data = dataLines.join('\n')
        dataLines = []
        if (currentEvent === 'delta') content.value += data
        else if (currentEvent === 'error') errorMsg.value = data
        currentEvent = 'message'
      }

      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buf.indexOf('\n')) >= 0) {
          const line = buf.slice(0, idx).replace(/\r$/, '')
          buf = buf.slice(idx + 1)
          if (!line) { dispatch(); continue }
          if (line.startsWith('event:')) currentEvent = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
        }
      }
      dispatch()
    } catch (e) {
      if (e.name !== 'AbortError') {
        errorMsg.value = e.message || errorHint
        ElMessage.error(`${errorHint}：${e.message || e}`)
      }
    } finally {
      streaming.value = false
      abortCtrl = null
    }
  }

  const copy = async () => {
    if (!content.value) return
    try {
      await navigator.clipboard.writeText(content.value)
      ElMessage.success('报告已复制到剪贴板')
    } catch (_) {
      ElMessage.warning('复制失败，请手动选择文本复制')
    }
  }

  return { content, streaming, errorMsg, start, stop, reset, copy }
}
