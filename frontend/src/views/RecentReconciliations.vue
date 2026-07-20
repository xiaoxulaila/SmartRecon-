<template>
  <div class="page-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <h1>📋 最近对账</h1>
      <p>查看历史对账记录，点击详情可查看完整结果</p>
    </div>

    <!-- 操作栏 -->
    <div v-if="historyList.length > 0" class="card" style="margin-bottom:16px;padding:12px 20px;display:flex;align-items:center;justify-content:space-between">
      <span style="font-size:14px;color:var(--text-secondary)">
        共 <strong style="color:var(--primary)">{{ historyList.length }}</strong> 条对账记录
      </span>
      <button class="btn btn-outline" style="font-size:12px;padding:6px 14px;color:var(--danger);border-color:var(--danger-light)" @click="handleClearAll">
        🗑 清空全部
      </button>
    </div>

    <!-- 历史表格 -->
    <div class="card" v-if="historyList.length > 0">
      <div style="overflow-x:auto">
        <table class="data-table">
          <thead>
            <tr>
              <th>对账时间</th>
              <th>数据源 A</th>
              <th>数据源 B</th>
              <th>记录数 (A/B)</th>
              <th>匹配数</th>
              <th>匹配率</th>
              <th>未匹配A</th>
              <th>未匹配B</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in historyList" :key="record.id">
              <td style="white-space:nowrap">{{ record.createdAt }}</td>
              <td style="max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="record.sourceFileName">
                {{ record.sourceFileName }}
              </td>
              <td style="max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="record.targetFileName">
                {{ record.targetFileName }}
              </td>
              <td>{{ record.sourceRecordCount }} / {{ record.targetRecordCount }}</td>
              <td style="color:var(--success);font-weight:600">{{ record.matchedCount }}</td>
              <td>
                <span :class="matchRateClass(record.matchRate)">{{ record.matchRate }}%</span>
              </td>
              <td>
                <span v-if="record.onlyInSourceCount > 0" class="badge badge-danger">{{ record.onlyInSourceCount }}</span>
                <span v-else style="color:var(--text-muted)">0</span>
              </td>
              <td>
                <span v-if="record.onlyInTargetCount > 0" class="badge badge-warning">{{ record.onlyInTargetCount }}</span>
                <span v-else style="color:var(--text-muted)">0</span>
              </td>
              <td>
                <span v-if="record.onlyInSourceCount === 0 && record.onlyInTargetCount === 0" class="badge badge-success">✅ 一致</span>
                <span v-else-if="record.matchRate >= 80" class="badge badge-warning">⚠ 有差异</span>
                <span v-else class="badge badge-danger">⚠ 差异大</span>
              </td>
              <td>
                <button class="btn btn-primary" style="font-size:12px;padding:5px 12px" @click="openDetail(record)">
                  📄 详情
                </button>
                <button class="btn btn-outline" style="font-size:12px;padding:5px 8px;margin-left:6px" @click="handleDelete(record.id)">
                  🗑
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="card empty-state">
      <div class="empty-state-icon">📭</div>
      <div style="font-size:16px;font-weight:600;margin-bottom:8px">暂无对账记录</div>
      <div style="font-size:14px;margin-bottom:20px">去首页上传两份账单，完成对账后记录会自动保存在这里</div>
      <router-link to="/" class="btn btn-primary">前往对账</router-link>
    </div>

    <!-- ========= 详情弹窗 ========= -->
    <div v-if="detailRecord" class="modal-overlay" @click.self="detailRecord = null">
      <div class="modal-content">
        <div class="modal-header">
          <div>
            <h2>📊 对账详情</h2>
            <div style="font-size:13px;color:var(--text-secondary);margin-top:4px">
              {{ detailRecord.sourceFileName }} ↔ {{ detailRecord.targetFileName }}
              <span style="margin-left:12px">{{ detailRecord.createdAt }}</span>
            </div>
          </div>
          <button class="modal-close" @click="detailRecord = null">✕</button>
        </div>
        <div class="modal-body">
          <ResultTable :result="detailRecord.result" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import ResultTable from '../components/ResultTable.vue'
import { getHistory, deleteHistory, clearHistory } from '../utils/history.js'

export default {
  name: 'RecentReconciliations',
  components: { ResultTable },
  data() {
    return {
      historyList: [],
      detailRecord: null
    }
  },
  created() {
    this.loadHistory()
  },
  methods: {
    loadHistory() {
      this.historyList = getHistory()
    },
    openDetail(record) {
      this.detailRecord = record
    },
    handleDelete(id) {
      if (confirm('确定要删除这条对账记录吗？')) {
        deleteHistory(id)
        this.loadHistory()
      }
    },
    handleClearAll() {
      if (confirm('确定要清空所有对账记录吗？此操作不可恢复！')) {
        clearHistory()
        this.loadHistory()
      }
    },
    matchRateClass(rate) {
      if (rate >= 95) return 'badge badge-success'
      if (rate >= 70) return 'badge badge-warning'
      return 'badge badge-danger'
    }
  }
}
</script>
