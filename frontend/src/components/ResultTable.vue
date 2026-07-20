<template>
  <div class="result-section">
    <!-- 统计概览 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-value" style="color:var(--primary)">{{ result.sourceCount }}</div>
        <div class="stat-label">数据源A 记录数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:var(--primary)">{{ result.targetCount }}</div>
        <div class="stat-label">数据源B 记录数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:var(--success)">{{ result.matchedCount }}</div>
        <div class="stat-label">匹配成功</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:var(--danger)">{{ result.onlyInSource?.length || 0 }}</div>
        <div class="stat-label">仅数据源A（未匹配）</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color:var(--warning)">{{ result.onlyInTarget?.length || 0 }}</div>
        <div class="stat-label">仅数据源B（未匹配）</div>
      </div>
    </div>

    <!-- 匹配率进度条 -->
    <div class="card" style="margin-bottom:24px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span style="font-weight:600">匹配率</span>
        <span style="font-weight:700;font-size:20px;color:var(--success)">{{ result.matchRate }}%</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: result.matchRate + '%' }"></div>
      </div>
    </div>

    <!-- 对账明细 tabs -->
    <div class="card" style="overflow:visible">
      <div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap;border-bottom:1px solid var(--border);padding-bottom:12px">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="btn"
          :class="activeTab === tab.key ? 'btn-primary' : 'btn-outline'"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <span v-if="tab.count !== undefined" class="badge" :class="tab.badgeClass" style="margin-left:6px">{{ tab.count }}</span>
        </button>
      </div>

      <!-- 🔴 调试：原始数据总览（始终可见） -->
      <div style="margin-bottom:12px;padding:10px;background:#FFF8E6;border-radius:6px;font-size:11px;font-family:monospace;max-height:200px;overflow:auto;white-space:pre-wrap;word-break:break-all;color:#333">
        <strong>🔍 数据总览:</strong>
        <!-- matchedPairs数量: {{ safeMatchedPairs.length }} | displayCols数量: {{ displayCols.length }} -->
        <!-- displayCols内容: {{ JSON.stringify(displayCols) }} -->
        <!-- 第一条pair的source: {{ safeMatchedPairs[0] ? JSON.stringify(safeMatchedPairs[0].source).slice(0,500) : '无数据' }} -->
      </div>

      <div style="overflow-x:auto;max-height:500px">
        <!-- 匹配成功：统一用一个表格 -->
        <table v-if="activeTab === 'matched'" class="data-table" style="width:100%">
          <thead>
            <tr>
              <th>#</th>
              <th v-for="c in displayCols" :key="'sc_'+c" style="color:#3B82F6;min-width:80px">{{ c }} (A)</th>
              <th v-for="c in displayCols" :key="'tc_'+c" style="color:#e67e00;min-width:80px">{{ c }} (B)</th>
              <th>差异</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(pair, idx) in safeMatchedPairs" :key="'m_'+idx" :class="pair && pair.hasDifference ? 'unmatch-target-row' : 'match-row'">
              <td>{{ idx + 1 }}</td>
              <td v-for="c in displayCols" :key="'sa_'+c+'_'+idx">{{ colRawVal(pair && pair.source, c) }}</td>
              <td v-for="c in displayCols" :key="'sb_'+c+'_'+idx">{{ colRawVal(pair && pair.target, c) }}</td>
              <td>
                <span v-if="pair && pair.hasDifference" class="badge badge-warning">有差异</span>
                <span v-else class="badge badge-success">一致</span>
              </td>
            </tr>
            <tr v-if="safeMatchedPairs.length === 0">
              <td :colspan="displayCols.length * 2 + 2" style="text-align:center;padding:40px;color:var(--text-muted)">暂无匹配数据</td>
            </tr>
          </tbody>
        </table>

        <!-- 仅数据源A -->
        <table v-if="activeTab === 'onlySource'" class="data-table" style="width:100%">
          <thead>
            <tr><th>#</th><th v-for="c in displayCols" :key="'sha_'+c">{{ c }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="(rec, idx) in (result.onlyInSource || [])" :key="'os_'+idx" class="unmatch-source-row">
              <td>{{ idx + 1 }}</td>
              <td v-for="c in displayCols" :key="'osa_'+c+'_'+idx">{{ colRawVal(rec, c) }}</td>
            </tr>
            <tr v-if="!result.onlyInSource || result.onlyInSource.length === 0">
              <td :colspan="displayCols.length + 1" style="text-align:center;padding:40px;color:var(--text-muted)">数据源A的记录已全部匹配 ✅</td>
            </tr>
          </tbody>
        </table>

        <!-- 仅数据源B -->
        <table v-if="activeTab === 'onlyTarget'" class="data-table" style="width:100%">
          <thead>
            <tr><th>#</th><th v-for="c in displayCols" :key="'shb_'+c">{{ c }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="(rec, idx) in (result.onlyInTarget || [])" :key="'ot_'+idx" class="unmatch-target-row">
              <td>{{ idx + 1 }}</td>
              <td v-for="c in displayCols" :key="'otb_'+c+'_'+idx">{{ colRawVal(rec, c) }}</td>
            </tr>
            <tr v-if="!result.onlyInTarget || result.onlyInTarget.length === 0">
              <td :colspan="displayCols.length + 1" style="text-align:center;padding:40px;color:var(--text-muted)">数据源B的记录已全部匹配 ✅</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ResultTable',
  props: {
    result: { type: Object, required: true },
    matchColumns: { type: Array, default: () => [] }
  },
  data() {
    return {
      activeTab: 'matched'
    }
  },
  errorCaptured(err) {
    console.error('ResultTable 渲染错误:', err)
    return false // 阻止错误冒泡
  },
  mounted() {
    console.log('=== ResultTable 已挂载 ===')
    console.log('result:', this.result ? Object.keys(this.result) : 'null')
    console.log('matchedPairs:', this.result?.matchedPairs?.length)
    console.log('matchColumns:', this.matchColumns?.length)
    console.log('displayCols 结果:', JSON.stringify(this.displayCols))
  },
  watch: {
    result: {
      handler(newVal) {
        console.log('=== ResultTable result prop 变化 ===')
        if (!newVal) {
          console.warn('⚠️ result 变为空值!')
          return
        }
        console.log('新值 keys:', Object.keys(newVal))
        console.log('matchedPairs长度:', newVal?.matchedPairs?.length)
        this.$nextTick(() => {
          console.log('displayCols:', JSON.stringify(this.displayCols))
          console.log('safeMatchedPairs:', this.safeMatchedPairs.length)
        })
      },
      immediate: true,
      deep: true
    }
  },
  computed: {
    tabs() {
      const r = this.result || {}
      return [
        { key: 'matched', label: '匹配成功', count: r.matchedCount || 0, badgeClass: 'badge-success' },
        { key: 'onlySource', label: '仅数据源A', count: (r.onlyInSource || []).length, badgeClass: 'badge-danger' },
        { key: 'onlyTarget', label: '仅数据源B', count: (r.onlyInTarget || []).length, badgeClass: 'badge-warning' }
      ]
    },
    /** 返回纯字符串列名数组：规则模式用matchColumns，AI模式从rawData自动收集 */
    displayCols() {
      // 规则对账：用自定义列
      const customCols = (this.matchColumns || []).filter(c => c && c.sourceColumn && c.targetColumn)
      if (customCols.length > 0) {
        return customCols.map(c => c.sourceColumn)
      }
      // AI模式 / 兜底：直接从所有数据中收集列名
      const keySet = new Set()
      const addKeys = (rec) => {
        if (!rec) return
        if (rec.rawData && typeof rec.rawData === 'object') {
          Object.keys(rec.rawData).forEach(k => keySet.add(k))
        }
      }
      const pairs = (this.result && this.result.matchedPairs) || []
      for (const p of pairs) { addKeys(p.source); addKeys(p.target) }
      for (const r of (this.result && this.result.onlyInSource) || []) { addKeys(r) }
      for (const r of (this.result && this.result.onlyInTarget) || []) { addKeys(r) }
      if (keySet.size > 0) return Array.from(keySet)
      // 终极兜底
      const firstPair = pairs[0]
      if (firstPair && firstPair.source) {
        const keys = Object.keys(firstPair.source).filter(k => k !== 'rawData' && k !== 'index')
        if (keys.length > 0) return keys
      }
      return ['金额', '日期', '描述']
    },
    /** 安全获取匹配对数组 */
    safeMatchedPairs() {
      const pairs = (this.result && this.result.matchedPairs) || []
      return Array.isArray(pairs) ? pairs : []
    }
  },
  methods: {
    /** 直接从 record.rawData 取值，超级简单 */
    colRawVal(record, colName) {
      if (!record || !colName) return '-'
      // 优先 rawData
      if (record.rawData && record.rawData[colName] !== undefined && record.rawData[colName] !== null) {
        const val = String(record.rawData[colName])
        return val || '-'
      }
      // 兜底：直接取对象属性
      if (record[colName] !== undefined && record[colName] !== null) {
        return String(record[colName])
      }
      return '-'
    }
  }
}
</script>

<style>
/* 表格滚动 */
.result-section .data-table {
  width: 100%;
  table-layout: auto;
}
.result-section .data-table td {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
