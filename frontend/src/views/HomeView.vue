<template>
  <div class="page-container">
    <!-- Toast 通知 -->
    <div v-if="toast.show" class="toast" :class="'toast-' + toast.type">{{ toast.message }}</div>

    <!-- 页面头部 -->
    <div class="page-header">
      <h1>💰 智能对账系统</h1>
      <p>上传两份账目表格或拍照上传纸质账单，系统自动匹配对账，一目了然</p>
    </div>

    <!-- ===== 步骤 1：上传数据源 ===== -->
    <div class="card" style="margin-bottom:24px">
      <div class="card-header">
        <span class="card-title">📋 第1步：上传两份待对账的数据</span>
        <span class="badge badge-info" style="font-size:11px">支持 Excel · CSV · 拍照上传</span>
      </div>

      <div class="two-column">
        <!-- 数据源 A -->
        <div>
          <div style="margin-bottom:8px;font-weight:600;font-size:14px;color:var(--text-secondary)">
            🔵 数据源 A
            <span v-if="sourceA.fileName" class="badge badge-success" style="margin-left:8px">
              {{ sourceA.recordCount }} 条
            </span>
          </div>
          <FileUpload
            ref="uploadA"
            title="上传数据源 A"
            hint="上传银行账单、公司账本等"
            @uploaded="onSourceAUploaded"
            @remove="sourceA = {}; reconcileResult = null"
            @toast="showToastMsg"
          />
        </div>

        <!-- 数据源 B -->
        <div>
          <div style="margin-bottom:8px;font-weight:600;font-size:14px;color:var(--text-secondary)">
            🟠 数据源 B
            <span v-if="sourceB.fileName" class="badge badge-success" style="margin-left:8px">
              {{ sourceB.recordCount }} 条
            </span>
          </div>
          <FileUpload
            ref="uploadB"
            title="上传数据源 B"
            hint="上传另一份账单进行比对"
            @uploaded="onSourceBUploaded"
            @remove="sourceB = {}; reconcileResult = null"
            @toast="showToastMsg"
          />
        </div>
      </div>
    </div>

    <!-- ===== 步骤 2：对账配置 ===== -->
    <div class="card" style="margin-bottom:24px" v-show="bothReady">
      <div class="card-header" style="flex-wrap:wrap;gap:12px">
        <span class="card-title">⚙️ 第2步：选择对账方式</span>
      </div>

      <!-- Tab 切换栏 -->
      <div class="tab-bar">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'rule' }"
          @click="activeTab = 'rule'"
        >
          <span class="tab-icon">🔍</span>
          <span class="tab-label">系统智能识别对账</span>
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'ai' }"
          @click="activeTab = 'ai'"
        >
          <span class="tab-icon">🤖</span>
          <span class="tab-label">AI 智能对账</span>
          <span v-if="!hasAIToken" class="tab-badge">未配置</span>
        </button>
      </div>

      <!-- ===== Tab 面板：系统智能识别对账 ===== -->
      <div v-show="activeTab === 'rule'" class="tab-panel">
        <!-- 匹配模式选择 -->
        <div style="margin-bottom:20px;display:flex;align-items:center;gap:12px">
          <span style="font-size:14px;font-weight:600;color:var(--text-secondary)">匹配模式：</span>
          <label class="radio-label" :class="{ active: matchMode === 'ALL' }">
            <input type="radio" v-model="matchMode" value="ALL" />
            <span>全部匹配</span>
            <span style="font-size:11px;color:var(--text-muted);margin-left:4px">(所选列全部相同才算匹配)</span>
          </label>
          <label class="radio-label" :class="{ active: matchMode === 'ANY' }">
            <input type="radio" v-model="matchMode" value="ANY" />
            <span>任一匹配</span>
            <span style="font-size:11px;color:var(--text-muted);margin-left:4px">(所选列任一相同就算匹配)</span>
          </label>
        </div>

        <!-- 匹配列配置 -->
        <div style="margin-bottom:20px">
          <div style="font-size:14px;font-weight:600;color:var(--text-secondary);margin-bottom:12px">
            匹配列 (至少添加一列)
          </div>

          <div
            v-for="(col, idx) in matchColumns"
            :key="idx"
            style="display:flex;align-items:center;gap:10px;margin-bottom:10px;flex-wrap:wrap;padding:12px;background:var(--bg-secondary);border-radius:var(--radius-sm)"
          >
            <span style="font-size:12px;color:var(--text-muted);min-width:20px">列{{ idx + 1 }}</span>

            <div style="display:flex;flex-direction:column;gap:2px;min-width:140px">
              <span style="font-size:11px;color:var(--text-muted)">数据源A</span>
              <select v-model="col.sourceColumn" style="padding:6px 8px;border:1px solid var(--border);border-radius:4px;font-size:13px;background:white">
                <option value="">-- 选择列 --</option>
                <option v-for="h in sourceA.headers" :key="'a_'+h" :value="h">{{ h.length > 16 ? h.slice(0,16)+'...' : h }}</option>
              </select>
            </div>

            <div style="display:flex;flex-direction:column;gap:2px;min-width:130px">
              <span style="font-size:11px;color:var(--text-muted)">匹配方式</span>
              <select v-model="col.matchType" style="padding:6px 8px;border:1px solid var(--border);border-radius:4px;font-size:13px;background:white">
                <option value="EXACT">精确匹配</option>
                <option value="CONTAINS">包含匹配</option>
                <option value="NUMERIC_TOLERANCE">数值容差</option>
                <option value="DATE_TOLERANCE">日期容差</option>
              </select>
            </div>

            <div style="display:flex;flex-direction:column;gap:2px;min-width:140px">
              <span style="font-size:11px;color:var(--text-muted)">数据源B</span>
              <select v-model="col.targetColumn" style="padding:6px 8px;border:1px solid var(--border);border-radius:4px;font-size:13px;background:white">
                <option value="">-- 选择列 --</option>
                <option v-for="h in sourceB.headers" :key="'b_'+h" :value="h">{{ h.length > 16 ? h.slice(0,16)+'...' : h }}</option>
              </select>
            </div>

            <div v-if="col.matchType === 'NUMERIC_TOLERANCE'" style="display:flex;flex-direction:column;gap:2px;min-width:100px">
              <span style="font-size:11px;color:var(--text-muted)">容差值</span>
              <input v-model="col.numericTolerance" type="number" step="0.01" min="0" placeholder="0.01"
                style="padding:6px 8px;border:1px solid var(--border);border-radius:4px;font-size:13px;width:90px" />
            </div>

            <div v-if="col.matchType === 'DATE_TOLERANCE'" style="display:flex;flex-direction:column;gap:2px;min-width:100px">
              <span style="font-size:11px;color:var(--text-muted)">天数容差</span>
              <input v-model="col.dateToleranceDays" type="number" min="0" max="365" placeholder="3"
                style="padding:6px 8px;border:1px solid var(--border);border-radius:4px;font-size:13px;width:80px" />
            </div>

            <button
              v-if="matchColumns.length > 1"
              class="btn btn-outline"
              style="padding:4px 10px;font-size:12px;color:var(--danger);border-color:var(--danger);margin-top:18px"
              @click="removeMatchColumn(idx)"
            >✕</button>
          </div>

          <button class="btn btn-outline" style="font-size:13px;padding:6px 16px" @click="addMatchColumn">
            + 添加匹配列
          </button>
        </div>

        <!-- 旧版快捷模式 -->
        <details style="margin-bottom:16px">
          <summary style="cursor:pointer;font-size:13px;color:var(--text-muted);padding:8px 0">
            使用旧版金额+日期匹配（快捷模式）
          </summary>
          <div style="display:flex;flex-wrap:wrap;gap:16px;align-items:flex-end;padding:12px;background:var(--bg-secondary);border-radius:var(--radius-sm);margin-top:8px">
            <div>
              <label style="display:block;font-size:13px;color:var(--text-secondary);margin-bottom:4px">金额容差</label>
              <input v-model="amountTolerance" type="number" step="0.01" min="0"
                style="padding:8px 12px;border:1px solid var(--border);border-radius:var(--radius-sm);width:120px;font-size:14px" />
            </div>
            <div>
              <label style="display:block;font-size:13px;color:var(--text-secondary);margin-bottom:4px">日期容差（天）</label>
              <input v-model="dateTolerance" type="number" min="0" max="30"
                style="padding:8px 12px;border:1px solid var(--border);border-radius:var(--radius-sm);width:120px;font-size:14px" />
            </div>
            <span style="font-size:12px;color:var(--text-warning)">⚠ 仅金额+日期匹配，不执行上方自定义列匹配</span>
          </div>
        </details>

        <!-- 开始规则对账按钮 -->
        <button
          class="btn btn-success btn-lg"
          style="width:100%"
          :disabled="reconciling || aiReconciling || !canReconcile"
          @click="startReconciliation"
        >
          <template v-if="reconciling">
            <span style="display:inline-block;animation:spin 1s linear infinite">⏳</span>
            系统对账中...
          </template>
          <template v-else>
            🔍 开始系统智能对账
          </template>
        </button>

        <div v-if="!canReconcile" style="font-size:12px;color:var(--text-muted);margin-top:8px;text-align:center">
          请至少添加一列匹配规则，或使用旧版金额+日期模式
        </div>
      </div>

      <!-- ===== Tab 面板：AI 智能对账 ===== -->
      <div v-show="activeTab === 'ai'" class="tab-panel">
        <template v-if="hasAIToken">
          <!-- AI 对账模式选择 -->
          <div style="padding:12px 16px;background:var(--bg-secondary);border-radius:8px;margin-bottom:20px">
            <label style="font-size:13px;font-weight:600;color:var(--text-secondary);margin-bottom:8px;display:block">
              AI 对账模式
            </label>
            <div style="display:flex;gap:24px;margin-bottom:8px">
              <label style="display:flex;align-items:center;gap:6px;cursor:pointer;font-size:13px">
                <input type="radio" v-model="aiMode" value="rule" />
                规则对账
              </label>
              <label style="display:flex;align-items:center;gap:6px;cursor:pointer;font-size:13px">
                <input type="radio" v-model="aiMode" value="chat" />
                对话对账
              </label>
            </div>
            <p v-if="aiMode === 'rule'" style="font-size:12px;color:var(--text-muted);margin:0">
              AI 自动对比两份数据，智能匹配相似记录，适合标准对账场景
            </p>
            <div v-if="aiMode === 'chat'">
              <p style="font-size:12px;color:var(--text-muted);margin:0 0 8px 0">
                输入您自己的对账指令，如"请按姓名和金额匹配"、"帮我找出金额差异超过100元的记录"等
              </p>
              <textarea
                v-model="customPrompt"
                class="form-input"
                style="width:100%;min-height:60px;resize:vertical;box-sizing:border-box"
                placeholder="例如：请按姓名精确匹配，然后筛选出金额差异超过10元的记录，标注差异原因..."
              ></textarea>
            </div>
          </div>

          <!-- 开始 AI 对账按钮 -->
          <button
            class="btn btn-lg"
            style="width:100%;background:linear-gradient(135deg, #667eea 0%, #764ba2 100%);color:#fff;border:none;font-weight:600"
            :disabled="reconciling || aiReconciling"
            @click="startAIReconciliation"
          >
            <template v-if="aiReconciling">
              <span style="display:inline-block;animation:spin 1s linear infinite">🤖</span>
              AI 分析中...
            </template>
            <template v-else>
              🤖 开始 AI 智能对账
            </template>
          </button>
        </template>

        <!-- 未配置 AI Token 的引导 -->
        <div v-else class="ai-empty-state">
          <div style="font-size:48px;margin-bottom:12px">🔑</div>
          <div style="font-size:16px;font-weight:600;margin-bottom:8px">尚未配置 AI 服务</div>
          <div style="font-size:13px;color:var(--text-muted);margin-bottom:16px">
            AI 智能对账可以自动识别数据结构、智能匹配记录，无需手动选择匹配列
          </div>
          <button class="btn btn-primary" @click="openAISettings">
            ⚙️ 前往配置 AI 服务
          </button>
        </div>
      </div>
    </div>

    <!-- ===== 步骤 3：对账结果 ===== -->
    <div v-if="reconcileResult" class="card result-card" style="margin-bottom:24px">
      <div class="card-header">
        <span class="card-title">📊 对账结果</span>
        <div style="display:flex;gap:8px">
          <button class="btn btn-outline" @click="exportResult" style="font-size:12px;padding:6px 14px">
            📥 导出结果
          </button>
          <button class="btn btn-outline" @click="resetAll" style="font-size:12px;padding:6px 14px">
            🔄 重新对账
          </button>
        </div>
      </div>
      <!-- 可见调试面板 -->
      <div style="margin-bottom:16px;padding:10px 14px;background:#EEF2FF;border-radius:6px;font-size:12px;color:#3730A3;display:flex;flex-wrap:wrap;gap:8px 16px">
        <span>🔍 版本:v{{ resultVersion }}</span>
        <span>📋 类型:{{ reconType === 'ai' ? 'AI' : '规则' }}</span>
        <span>✅ 匹配:{{ reconcileResult.matchedCount || 0 }}对</span>
        <span>🔵 仅A:{{ (reconcileResult.onlyInSource || []).length }}条</span>
        <span>🟠 仅B:{{ (reconcileResult.onlyInTarget || []).length }}条</span>
        <span>📊 匹配率:{{ reconcileResult.matchRate || 0 }}%</span>
      </div>
      <!-- 🔴 应急回退：如果 ResultTable 没渲染出来，这里直接显示原始数据 -->
      <div v-if="reconType === 'ai'" style="margin-bottom:16px;padding:12px;background:#FFF3CD;border-radius:6px;border:1px solid #F59E0B">
        <details>
          <summary style="cursor:pointer;font-weight:600;font-size:12px;color:#92400E">🔧 原始数据检查（点击展开）</summary>
          <pre style="margin-top:8px;font-size:10px;max-height:300px;overflow:auto;white-space:pre-wrap;word-break:break-all">{{ rawDataPreview }}</pre>
        </details>
      </div>
      <!-- 关键：用 :key 确保每次新结果都强制重建组件 -->
      <ResultTable
        :key="resultVersion"
        :result="reconcileResult"
        :matchColumns="reconType === 'rule' ? matchColumns : []"
      />
    </div>

    <!-- ===== 空状态引导 ===== -->
    <div v-if="!sourceA.dataId && !sourceB.dataId && !reconcileResult" class="card" style="text-align:center;padding:60px 24px">
      <div style="font-size:64px;margin-bottom:16px">📑</div>
      <div style="font-size:18px;font-weight:600;margin-bottom:8px">开始您的第一次对账</div>
      <div style="font-size:14px;color:var(--text-secondary);margin-bottom:24px">
        上传两份账单表格，选择要匹配的列（如姓名、学号等），系统自动对账
      </div>
      <div style="display:flex;gap:16px;justify-content:center;color:var(--text-muted);font-size:13px">
        <div style="text-align:center">
          <div style="font-size:28px;margin-bottom:4px">📸</div>
          <div>拍照上传</div>
          <div style="font-size:11px">拍纸质账单自动识别</div>
        </div>
        <div style="text-align:center">
          <div style="font-size:28px;margin-bottom:4px">📊</div>
          <div>上传表格</div>
          <div style="font-size:11px">支持 .xlsx .csv</div>
        </div>
        <div style="text-align:center">
          <div style="font-size:28px;margin-bottom:4px">🔍</div>
          <div>灵活匹配</div>
          <div style="font-size:11px">选任意列自定义匹配</div>
        </div>
        <div style="text-align:center">
          <div style="font-size:28px;margin-bottom:4px">📥</div>
          <div>导出结果</div>
          <div style="font-size:11px">差异一目了然</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import FileUpload from '../components/FileUpload.vue'
import ResultTable from '../components/ResultTable.vue'
import { runReconciliation, runAIReconciliation } from '../api/index.js'
import { buildHistoryRecord, saveHistory } from '../utils/history.js'

export default {
  name: 'HomeView',
  components: { FileUpload, ResultTable },
  data() {
    return {
      sourceA: {},
      sourceB: {},
      // 新版：多列匹配
      matchMode: 'ALL',
      matchColumns: [],
      useLegacyMode: false,
      // 旧版
      amountTolerance: 0.01,
      dateTolerance: 3,
      activeTab: 'rule',
      reconType: 'rule',
      resultVersion: 0,
      reconciling: false,
      aiReconciling: false,
      aiMode: 'rule',
      customPrompt: '',
      reconcileResult: null,
      toast: { show: false, message: '', type: 'info' }
    }
  },
  computed: {
    bothReady() {
      return this.sourceA.dataId && this.sourceB.dataId
    },
    canReconcile() {
      if (this.useLegacyMode) return true
      return this.matchColumns.some(c => c.sourceColumn && c.targetColumn)
    },
    hasAIToken() {
      try {
        const cfg = JSON.parse(localStorage.getItem('ai_config') || '{}')
        return !!(cfg && cfg.token)
      } catch { return false }
    },
    rawDataPreview() {
      if (!this.reconcileResult) return '无数据'
      try {
        const pairs = (this.reconcileResult.matchedPairs || []).slice(0, 3)
        const r = {
          matchedCount: this.reconcileResult.matchedCount,
          onlyInSourceCount: (this.reconcileResult.onlyInSource || []).length,
          onlyInTargetCount: (this.reconcileResult.onlyInTarget || []).length,
          firstPairKeys: pairs[0] ? Object.keys(pairs[0]) : [],
          firstPairSourceKeys: pairs[0] && pairs[0].source ? Object.keys(pairs[0].source) : [],
          firstPairSourceHasRawData: pairs[0] && pairs[0].source ? !!pairs[0].source.rawData : false,
          firstPairRawDataKeys: pairs[0] && pairs[0].source && pairs[0].source.rawData ? Object.keys(pairs[0].source.rawData) : [],
          firstPairRawDataSample: pairs[0] && pairs[0].source && pairs[0].source.rawData ? JSON.stringify(pairs[0].source.rawData).slice(0, 300) : 'null'
        }
        return JSON.stringify(r, null, 2)
      } catch (e) {
        return '解析异常: ' + e.message
      }
    }
  },
  methods: {
    onSourceAUploaded(data) {
      this.sourceA = data
      this.reconcileResult = null
      // 自动初始化匹配列（如果还没选）
      this.initMatchColumns()
    },
    onSourceBUploaded(data) {
      this.sourceB = data
      this.reconcileResult = null
      this.initMatchColumns()
    },
    initMatchColumns() {
      // 只在第一次上传时自动给一列默认值
      if (this.matchColumns.length === 0) {
        this.addMatchColumn()
      }
    },
    addMatchColumn() {
      this.matchColumns.push({
        sourceColumn: '',
        targetColumn: '',
        matchType: 'EXACT',
        numericTolerance: '0.01',
        dateToleranceDays: '3'
      })
    },
    removeMatchColumn(idx) {
      this.matchColumns.splice(idx, 1)
    },
    async startReconciliation() {
      if (!this.bothReady) return
      try {
        this.reconType = 'rule'
        this.reconciling = true
        this.showToast('正在对账中，请稍候...', 'info')

        const params = {
          sourceDataId: this.sourceA.dataId,
          targetDataId: this.sourceB.dataId
        }

        if (this.useLegacyMode) {
          // 旧版模式
          params.amountTolerance = this.amountTolerance
          params.dateToleranceDays = this.dateTolerance
        } else {
          // 新版多列匹配模式
          params.matchMode = this.matchMode
          params.matchColumns = this.matchColumns
            .filter(c => c.sourceColumn && c.targetColumn)
            .map(c => ({
              sourceColumn: c.sourceColumn,
              targetColumn: c.targetColumn,
              matchType: c.matchType,
              numericTolerance: c.matchType === 'NUMERIC_TOLERANCE' ? parseFloat(c.numericTolerance) || 0.01 : null,
              dateToleranceDays: c.matchType === 'DATE_TOLERANCE' ? parseInt(c.dateToleranceDays) || 3 : null
            }))
        }

        const result = await runReconciliation(params)

        this.reconcileResult = result
        this.resultVersion++

        // 保存到对账历史
        const historyRecord = buildHistoryRecord(this.sourceA, this.sourceB, result)
        saveHistory(historyRecord)

        const msg = `对账完成！匹配率 ${result.matchRate}%，共 ${result.matchedCount} 条匹配`
        this.showToast(msg, result.onlyInSource?.length > 0 || result.onlyInTarget?.length > 0 ? 'warning' : 'success')

        this.$nextTick(() => {
          const resultEl = document.querySelector('.result-section')
          if (resultEl) resultEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
        })
      } catch (err) {
        this.showToast('对账失败: ' + err.message, 'error')
      } finally {
        this.reconciling = false
      }
    },
    async startAIReconciliation() {
      if (!this.bothReady) return
      if (!this.hasAIToken) {
        this.showToast('请先在右上角 🤖 配置 AI Token', 'error')
        return
      }
      try {
        this.reconType = 'ai'
        this.aiReconciling = true
        this.showToast('AI 正在分析中，可能需要数十秒...', 'info')

        const aiConfig = JSON.parse(localStorage.getItem('ai_config') || '{}')
        const result = await runAIReconciliation({
          sourceDataId: this.sourceA.dataId,
          targetDataId: this.sourceB.dataId,
          model: aiConfig.model || 'gpt-4o-mini',
          token: aiConfig.token,
          baseUrl: aiConfig.baseUrl || 'https://api.openai.com/v1',
          aiMode: this.aiMode,
          customPrompt: this.aiMode === 'chat' ? this.customPrompt : null
        })

        console.log('=== AI对账原始返回 ===')
        console.log('result类型:', typeof result)
        console.log('result keys:', result ? Object.keys(result) : 'null/undefined')
        console.log('matchedPairs:', result?.matchedPairs?.length, '条')
        console.log('onlyInSource:', result?.onlyInSource?.length, '条')
        console.log('onlyInTarget:', result?.onlyInTarget?.length, '条')
        console.log('matchRate:', result?.matchRate)
        console.log('完整result:', JSON.parse(JSON.stringify(result)))

        this.reconcileResult = result
        this.resultVersion++

        console.log('=== reconcileResult已赋值 ===')
        console.log('reconcileResult:', !!this.reconcileResult)
        console.log('resultVersion:', this.resultVersion)

        const historyRecord = buildHistoryRecord(this.sourceA, this.sourceB, { ...result, aiMode: true })
        saveHistory(historyRecord)

        const msg = `AI 对账完成！匹配率 ${result.matchRate}%，共 ${result.matchedCount} 条匹配`
        this.showToast(msg, result.onlyInSource?.length > 0 || result.onlyInTarget?.length > 0 ? 'warning' : 'success')

        // 强制等待 Vue 完成渲染后再滚动
        this.$nextTick(() => {
          this.$nextTick(() => {
            const resultEl = document.querySelector('.result-section')
            console.log('result-section元素:', resultEl ? '找到了' : '找不到!')
            if (resultEl) {
              resultEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
            } else {
              console.warn('⚠️ .result-section 元素不存在，检查DOM')
              console.warn('页面中.reconcileResult位置元素:', document.querySelector('[class*="card"]'))
            }
          })
        })
      } catch (err) {
        console.error('AI对账异常:', err)
        this.showToast('AI 对账失败: ' + err.message, 'error')
      } finally {
        this.aiReconciling = false
      }
    },
    resetAll() {
      this.sourceA = {}
      this.sourceB = {}
      this.matchColumns = [{
        sourceColumn: '',
        targetColumn: '',
        matchType: 'EXACT',
        numericTolerance: '0.01',
        dateToleranceDays: '3'
      }]
      this.matchMode = 'ALL'
      this.useLegacyMode = false
      this.reconcileResult = null
      this.$refs.uploadA?.clear()
      this.$refs.uploadB?.clear()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
    exportResult() {
      if (!this.reconcileResult) return
      let csv = '\uFEFF类型'

      // 确定导出列
      let exportCols = []
      if (this.reconType === 'ai') {
        // AI 模式：从原始数据中自动收集所有列名
        const keySet = new Set()
        const pairs = this.reconcileResult.matchedPairs || []
        for (const pair of pairs) {
          if (pair.source?.rawData) Object.keys(pair.source.rawData).forEach(k => keySet.add(k))
          if (pair.target?.rawData) Object.keys(pair.target.rawData).forEach(k => keySet.add(k))
        }
        for (const rec of (this.reconcileResult.onlyInSource || [])) {
          if (rec.rawData) Object.keys(rec.rawData).forEach(k => keySet.add(k))
        }
        for (const rec of (this.reconcileResult.onlyInTarget || [])) {
          if (rec.rawData) Object.keys(rec.rawData).forEach(k => keySet.add(k))
        }
        exportCols = Array.from(keySet).map(k => ({ sourceColumn: k, targetColumn: k }))
      } else {
        // 规则模式：使用 matchColumns
        exportCols = this.matchColumns.filter(c => c.sourceColumn && c.targetColumn)
      }

      const hasCustomCols = exportCols.length > 0
      if (hasCustomCols) {
        for (const c of exportCols) {
          csv += `,${c.sourceColumn}(A)`
        }
        for (const c of exportCols) {
          csv += `,${c.targetColumn}(B)`
        }
      } else {
        csv += ',金额(A),日期(A),描述(A),金额(B),日期(B),描述(B)'
      }
      csv += ',差异\n'

      // 匹配成功
      for (const pair of this.reconcileResult.matchedPairs || []) {
        csv += '匹配成功'
        if (hasCustomCols) {
          for (const c of exportCols) {
            csv += `,"${getRawVal(pair.source, c.sourceColumn)}"`
          }
          for (const c of exportCols) {
            csv += `,"${getRawVal(pair.target, c.targetColumn)}"`
          }
        } else {
          csv += `,${pair.source?.amount || ''},${pair.source?.date || ''},"${pair.source?.description || ''}",${pair.target?.amount || ''},${pair.target?.date || ''},"${pair.target?.description || ''}"`
        }
        csv += `,${pair.hasDifference ? '有差异' : '一致'}\n`
      }
      // 仅A
      for (const rec of this.reconcileResult.onlyInSource || []) {
        csv += '仅数据源A'
        if (hasCustomCols) {
          for (const c of exportCols) {
            csv += `,"${getRawVal(rec, c.sourceColumn)}"`
          }
          for (const c of exportCols) csv += ','
        } else {
          csv += `,${rec.amount || ''},${rec.date || ''},"${rec.description || ''}",,,`
        }
        csv += ',未匹配\n'
      }
      // 仅B
      for (const rec of this.reconcileResult.onlyInTarget || []) {
        csv += '仅数据源B'
        if (hasCustomCols) {
          for (const c of exportCols) csv += ','
          for (const c of exportCols) {
            csv += `,"${getRawVal(rec, c.targetColumn)}"`
          }
        } else {
          csv += `,,,,${rec.amount || ''},${rec.date || ''},"${rec.description || ''}"`
        }
        csv += ',未匹配\n'
      }

      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `对账结果_${new Date().toISOString().slice(0,10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
      this.showToast('导出成功！', 'success')
    },
    showToastMsg({ message, type }) {
      this.showToast(message, type)
    },
    openAISettings() {
      window.dispatchEvent(new CustomEvent('open-ai-settings'))
    },
    showToast(message, type = 'info') {
      this.toast = { show: true, message, type }
      setTimeout(() => { this.toast.show = false }, 3000)
    }
  }
}

function getRawVal(record, colName) {
  if (!record || !colName) return ''
  if (record.rawData && record.rawData[colName] !== undefined) return record.rawData[colName]
  if (colName === '金额') return record.amount || ''
  if (colName === '日期') return record.date || ''
  if (colName === '描述') return record.description || ''
  return ''
}
</script>

<style scoped>
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Tab 切换栏 */
.tab-bar {
  display: flex;
  gap: 0;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: 4px;
  margin-bottom: 24px;
}
.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  border: none;
  background: transparent;
  border-radius: calc(var(--radius-md) - 4px);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-muted);
  transition: all 0.25s ease;
  white-space: nowrap;
}
.tab-btn:hover {
  color: var(--text-primary);
}
.tab-btn.active {
  background: #fff;
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
.tab-icon {
  font-size: 16px;
}
.tab-label {
  letter-spacing: 0.3px;
}
.tab-badge {
  font-size: 11px;
  background: #fff3cd;
  color: #856404;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}
.tab-panel {
  animation: fadeSlideIn 0.3s ease;
}
@keyframes fadeSlideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* AI 未配置空状态 */
.ai-empty-state {
  text-align: center;
  padding: 40px 24px;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.radio-label.active {
  border-color: var(--primary);
  background: rgba(43, 91, 253, 0.05);
  color: var(--primary);
}
.radio-label input[type="radio"] {
  accent-color: var(--primary);
}
</style>
