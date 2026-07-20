<template>
  <div>
    <div
      class="upload-zone"
      :class="{ dragover: isDragover, 'has-file': uploaded, 'ai-processing': aiProcessing }"
      @dragover.prevent="isDragover = true"
      @dragleave.prevent="isDragover = false"
      @drop.prevent="handleDrop"
      @click="triggerInput"
    >
      <input
        ref="fileInput"
        type="file"
        :accept="accept"
        style="display:none"
        @change="handleFileChange"
      />
      <!-- AI 处理中 -->
      <div v-if="aiProcessing" class="ai-thinking">
        <div class="ai-brain">🧠</div>
        <div class="ai-thinking-text">AI 正在思考中{{ dots }}</div>
        <div class="ai-thinking-sub">正在识别图片中的表格数据，请稍候...</div>
      </div>
      <!-- 未上传 -->
      <div v-else-if="!uploaded">
        <div class="upload-icon">📎</div>
        <div class="upload-text">{{ title }}</div>
        <div class="upload-hint">
          <template v-if="isImageHint">
            <span v-if="aiReady">🤖 AI 智能识别表格</span>
            <span v-else style="color:var(--warning)">⚠️ 需先配置AI Token才能上传图片</span>
          </template>
          <span v-else>{{ hint }}</span>
        </div>
      </div>
      <!-- 已上传 -->
      <div v-else>
        <div class="upload-icon">✅</div>
        <div class="upload-text">{{ fileName }}</div>
        <div class="upload-hint">
          {{ sourceType === 'image-ai' ? '🤖 AI识别' : '' }} {{ recordCount }} 条记录 · 点击重新上传
        </div>
      </div>
    </div>

    <!-- 数据预览 -->
    <div v-if="uploaded && previewData.length > 0" class="card" style="margin-top:16px">
      <div class="card-header">
        <span class="card-title">数据预览（前{{ previewData.length }}条）</span>
        <button class="btn btn-outline" @click="clear" style="font-size:12px;padding:4px 12px">
          清除
        </button>
      </div>
      <div style="overflow-x:auto;max-height:300px;overflow-y:auto;max-width: 480px;">
        <table class="data-table">
          <thead>
            <tr>
              <th>#</th>
              <th v-for="h in headers" :key="h">{{ h }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in previewData" :key="i">
              <td>{{ i + 1 }}</td>
              <td v-for="h in headers" :key="h">
                {{ row.rawData?.[h] || (h === '金额' ? row.amount : h === '日期' ? row.date : h === '描述' ? row.description : '') || '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
import { uploadFile as uploadApi, uploadFileWithAI } from '../api/index.js'

const IMAGE_EXTS = new Set(['.png', '.jpg', '.jpeg', '.bmp', '.gif', '.tiff', '.webp'])

export default {
  name: 'FileUpload',
  props: {
    title: { type: String, default: '点击或拖拽上传文件' },
    hint: { type: String, default: '支持 Excel(.xlsx/.xls)、CSV(.csv) 及图片(.png/.jpg)' },
    accept: { type: String, default: '.xlsx,.xls,.csv,.png,.jpg,.jpeg,.bmp' },
    label: { type: String, default: 'source' }
  },
  emits: ['uploaded', 'remove'],
  data() {
    return {
      isDragover: false,
      uploaded: false,
      uploading: false,
      aiProcessing: false,
      dots: '',
      dotTimer: null,
      fileName: '',
      recordCount: 0,
      headers: [],
      previewData: [],
      dataId: '',
      sourceType: ''
    }
  },
  computed: {
    /** 是否已配置AI */
    aiReady() {
      try {
        const cfg = JSON.parse(localStorage.getItem('ai_config') || '{}')
        return !!(cfg.token && cfg.token.trim())
      } catch {
        return false
      }
    },
    /** 是否为图片上传提示 */
    isImageHint() {
      return this.accept && (this.accept.includes('.png') || this.accept.includes('.jpg'))
    }
  },
  methods: {
    triggerInput() {
      this.$refs.fileInput.click()
    },
    handleFileChange(e) {
      const file = e.target.files?.[0]
      if (file) this.uploadFile(file)
    },
    handleDrop(e) {
      this.isDragover = false
      const file = e.dataTransfer?.files?.[0]
      if (file) this.uploadFile(file)
    },
    /** 判断文件是否为图片 */
    isImageFile(file) {
      const name = file.name || ''
      const dotIdx = name.lastIndexOf('.')
      if (dotIdx < 0) return false
      const ext = name.substring(dotIdx).toLowerCase()
      return IMAGE_EXTS.has(ext)
    },
    startDotsAnimation() {
      this.dots = ''
      let count = 0
      this.dotTimer = setInterval(() => {
        count = (count + 1) % 4
        this.dots = '.'.repeat(count)
      }, 500)
    },
    stopDotsAnimation() {
      if (this.dotTimer) {
        clearInterval(this.dotTimer)
        this.dotTimer = null
      }
      this.dots = ''
    },
    async uploadFile(file) {
      try {
        this.uploading = true

        // 图片文件 → 一律走 AI 视觉识别
        if (this.isImageFile(file)) {
          const cfg = this.getAIConfig()
          if (!cfg || !cfg.token) {
            alert('⚠️ 请先在右上角 🤖 AI设置 中配置API Token，才能使用图片识别功能')
            return
          }
          this.aiProcessing = true
          this.startDotsAnimation()
          this.showToast?.(`🤖 AI正在识别 ${file.name}...`, 'info')
          const res = await uploadFileWithAI(file, {
            model: cfg.model || 'gpt-4o-mini',
            token: cfg.token,
            baseUrl: cfg.baseUrl || 'https://api.openai.com/v1'
          })
          this.setResult(res)
          this.aiProcessing = false
          this.stopDotsAnimation()
          this.showToast?.(`AI识别成功，共 ${res.recordCount} 条记录`, 'success')
        } else {
          // Excel/CSV → 普通上传
          this.showToast?.(`正在解析 ${file.name}...`, 'info')
          const res = await uploadApi(file)
          this.setResult(res)
          this.showToast?.(`解析成功，共 ${res.recordCount} 条记录`, 'success')
        }
      } catch (err) {
        this.aiProcessing = false
        this.stopDotsAnimation()
        const msg = err.message || ''
        if (this.isImageFile(file)) {
          alert('❌ AI图片识别失败\n\nAI服务暂不可用或Token可能已用完，请检查：\n1. Token是否正确\n2. Token余额是否充足\n3. 网络是否正常\n\n错误详情：' + msg)
        } else {
          alert('上传失败: ' + msg)
        }
      } finally {
        this.uploading = false
      }
    },
    /** 获取AI配置 */
    getAIConfig() {
      try {
        return JSON.parse(localStorage.getItem('ai_config') || '{}')
      } catch {
        return {}
      }
    },
    setResult(res) {
      this.fileName = res.fileName
      this.recordCount = res.recordCount
      this.headers = res.headers
      this.previewData = res.previewData
      this.dataId = res.dataId
      this.sourceType = res.sourceType || ''
      this.uploaded = true
      this.$emit('uploaded', {
        dataId: res.dataId,
        fileName: res.fileName,
        recordCount: res.recordCount,
        headers: res.headers,
        sourceType: res.sourceType
      })
    },
    clear() {
      this.uploaded = false
      this.fileName = ''
      this.recordCount = 0
      this.headers = []
      this.previewData = []
      this.dataId = ''
      this.sourceType = ''
      this.$emit('remove')
    },
    showToast(msg, type) {
      this.$root?.showToast?.(msg, type)
      this.$emit('toast', { message: msg, type })
    }
  },
  beforeUnmount() {
    this.stopDotsAnimation()
  }
}
</script>

<style scoped>
.ai-thinking {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
}
.ai-brain {
  font-size: 42px;
  animation: brainPulse 1.5s ease-in-out infinite;
}
@keyframes brainPulse {
  0%, 100% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.15); opacity: 1; }
}
.ai-thinking-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--primary);
  min-height: 24px;
}
.ai-thinking-sub {
  font-size: 12px;
  color: var(--text-muted);
}
.upload-zone.ai-processing {
  border-color: var(--primary);
  background: linear-gradient(135deg, rgba(43,91,253,0.04), rgba(124,58,237,0.04));
  cursor: default;
  pointer-events: none;
}
</style>
