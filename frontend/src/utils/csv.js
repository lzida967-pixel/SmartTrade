/**
 * CSV 导出工具
 * - 自动加 UTF-8 BOM，避免 Excel 打开中文乱码
 * - 单元格内含 ", \n, , 时自动转义并加引号
 */

const escapeCell = (val) => {
  if (val === null || val === undefined) return ''
  const s = String(val)
  // 含特殊字符（逗号 / 引号 / 换行）需要用双引号包裹，并把内部双引号 → 两个双引号
  if (/[",\r\n]/.test(s)) {
    return '"' + s.replace(/"/g, '""') + '"'
  }
  return s
}

/**
 * @param {Array<{key:string,label:string,format?:(row,val)=>any}>} columns
 * @param {Array<Object>} rows
 * @returns {string} csv 文本（不含 BOM）
 */
export const toCSV = (columns, rows) => {
  const header = columns.map(c => escapeCell(c.label)).join(',')
  const body = rows.map(row => {
    return columns.map(c => {
      const raw = row[c.key]
      const v = c.format ? c.format(row, raw) : raw
      return escapeCell(v)
    }).join(',')
  }).join('\r\n')
  return header + '\r\n' + body
}

/**
 * 触发浏览器下载
 * @param {string} filename
 * @param {string} csvText 不带 BOM 的纯 CSV 文本
 * @param {Array<string>} extraHeaderLines 可选：在 CSV 前面追加几行（比如导出时间、汇总）
 */
export const downloadCSV = (filename, csvText, extraHeaderLines = []) => {
  const BOM = '\uFEFF'
  const prefix = extraHeaderLines.length
    ? extraHeaderLines.map(l => escapeCell(l)).join('\r\n') + '\r\n\r\n'
    : ''
  const blob = new Blob([BOM + prefix + csvText], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 释放
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/**
 * 生成时间戳字符串：20260430-152030
 */
export const tsForFilename = () => {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}
