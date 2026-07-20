/**
 * 对账历史记录管理（基于 localStorage）
 */
const HISTORY_KEY = 'reconciliation_history'
const MAX_HISTORY = 50

export function getHistory() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export function saveHistory(record) {
  const list = getHistory()
  list.unshift(record)
  if (list.length > MAX_HISTORY) list.length = MAX_HISTORY
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list))
}

export function getHistoryById(id) {
  return getHistory().find(r => r.id === id) || null
}

export function deleteHistory(id) {
  const list = getHistory().filter(r => r.id !== id)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list))
}

export function clearHistory() {
  localStorage.removeItem(HISTORY_KEY)
}

export function buildHistoryRecord(sourceInfo, targetInfo, result) {
  return {
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    createdAt: new Date().toLocaleString('zh-CN'),
    sourceFileName: sourceInfo.fileName || '未知文件',
    targetFileName: targetInfo.fileName || '未知文件',
    sourceRecordCount: sourceInfo.recordCount || 0,
    targetRecordCount: targetInfo.recordCount || 0,
    matchedCount: result.matchedCount,
    matchRate: result.matchRate,
    onlyInSourceCount: result.onlyInSource?.length || 0,
    onlyInTargetCount: result.onlyInTarget?.length || 0,
    result
  }
}
