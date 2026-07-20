<template>
  <div>
    <!-- 右上角按钮 -->
    <button class="ai-settings-trigger" @click="openModal" title="AI 设置">
      <span class="ai-icon">🤖</span>
      <span v-if="hasToken" class="ai-dot"></span>
      <span v-else class="ai-dot off"></span>
    </button>

    <!-- 弹窗 -->
    <div v-if="visible" class="modal-overlay" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>🤖 AI 对账设置</h3>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>

        <div class="modal-body">
          <!-- AI 模型选择 -->
          <div class="form-group">
            <label>AI 模型 <span class="required">*</span></label>
            <select v-model="localConfig.model" class="form-select">
              <option value="">-- 选择模型 --</option>
              <optgroup label="OpenAI">
                <option value="gpt-4o">GPT-4o</option>
                <option value="gpt-4o-mini">GPT-4o-mini</option>
                <option value="gpt-4-turbo">GPT-4 Turbo</option>
              </optgroup>
              <optgroup label="DeepSeek">
                <option value="deepseek-chat">DeepSeek-V3</option>
                <option value="deepseek-reasoner">DeepSeek-R1</option>
              </optgroup>
              <optgroup label="Moonshot｜Kimi">
                <option value="moonshot-v1-8k">Kimi (8K)</option>
                <option value="moonshot-v1-32k">Kimi (32K)</option>
                <option value="moonshot-v1-128k">Kimi (128K)</option>
              </optgroup>
              <optgroup label="智谱｜GLM">
                <option value="glm-4">GLM-4</option>
                <option value="glm-4v">GLM-4V (视觉)</option>
                <option value="glm-3-turbo">GLM-3 Turbo</option>
              </optgroup>
              <optgroup label="MiniMax">
                <option value="MiniMax-M3">MiniMax-M3（原生多模态·1M上下文）</option>
                <option value="MiniMax-M2.7">MiniMax-M2.7（模型自我迭代）</option>
                <option value="MiniMax-M2.7-highspeed">MiniMax-M2.7-highspeed（效果不变·速度翻倍）</option>
              </optgroup>
              <optgroup label="通义千问">
                <option value="qwen-plus">Qwen-Plus</option>
                <option value="qwen-turbo">Qwen-Turbo</option>
                <option value="qwen-max">Qwen-Max</option>
                <option value="qwen-vl-plus">Qwen-VL-Plus (视觉)</option>
              </optgroup>
              <optgroup label="自定义">
                <option value="custom">自定义模型</option>
              </optgroup>
            </select>
          </div>

          <!-- 自定义模型名称 -->
          <div v-if="localConfig.model === 'custom'" class="form-group">
            <label>自定义模型名称</label>
            <input v-model="localConfig.customModel" type="text" class="form-input"
              placeholder="如：qwen-plus、glm-4 等" />
          </div>

          <!-- API 地址 -->
          <div class="form-group">
            <label>API 地址 <span class="required">*</span></label>
            <input v-model="localConfig.baseUrl" type="text" class="form-input"
              placeholder="请输入 API 地址" />
            <span class="form-hint">{{ defaultBaseUrlHint }}</span>
          </div>

          <!-- API Token -->
          <div class="form-group">
            <label>API Token <span class="required">*</span></label>
            <div class="input-with-btn">
              <input
                :type="showToken ? 'text' : 'password'"
                v-model="localConfig.token"
                class="form-input"
                placeholder="输入 API Token"
              />
              <button class="btn btn-outline btn-sm" @click="showToken = !showToken">
                {{ showToken ? '🙈' : '👁' }}
              </button>
            </div>
          </div>

          <!-- 快捷预设 -->
          <div class="preset-buttons">
            <button class="btn btn-outline btn-sm" @click="setPreset('openai')">OpenAI</button>
            <button class="btn btn-outline btn-sm" @click="setPreset('deepseek')">DeepSeek</button>
            <button class="btn btn-outline btn-sm" @click="setPreset('moonshot')">Kimi</button>
            <button class="btn btn-outline btn-sm" @click="setPreset('glm')">GLM</button>
            <button class="btn btn-outline btn-sm" @click="setPreset('minimax')">MiniMax</button>
          </div>
        </div>

        <div class="modal-footer">
          <div v-if="statusMsg" class="status-msg" :class="statusType">{{ statusMsg }}</div>
          <div style="display:flex;gap:8px">
            <button class="btn btn-outline" @click="testConnection" :disabled="testing">
              {{ testing ? '测试中...' : '🔗 测试连接' }}
            </button>
            <button class="btn btn-outline" @click="closeModal">取消</button>
            <button class="btn btn-primary" @click="saveConfig">💾 保存设置</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { testAIConnection } from '../api/index.js'

const STORAGE_KEY = 'ai_config'

export default {
  name: 'AISettings',
  data() {
    const saved = this.loadConfig()
    return {
      visible: false,
      localConfig: { ...saved },
      showToken: false,
      testing: false,
      statusMsg: '',
      statusType: ''
    }
  },
  computed: {
    hasToken() {
      const cfg = this.loadConfig()
      return !!(cfg && cfg.token)
    },
    defaultBaseUrl() {
      const m = this.localConfig.model || ''
      if (m.startsWith('gpt-')) return 'https://api.openai.com/v1'
      if (m.startsWith('deepseek-')) return 'https://api.deepseek.com/v1'
      if (m.startsWith('moonshot-')) return 'https://api.moonshot.cn/v1'
      if (m.startsWith('glm-')) return 'https://open.bigmodel.cn/api/paas/v4'
      if (m.startsWith('MiniMax-') || m.startsWith('abab')) return 'https://api.minimaxi.com/anthropic'
      if (m.startsWith('qwen-')) return 'https://dashscope.aliyuncs.com/compatible-mode/v1'
      return 'https://api.openai.com/v1'
    },
    defaultBaseUrlHint() {
      const m = this.localConfig.model || ''
      if (m.startsWith('gpt-')) return 'OpenAI 官方接口'
      if (m.startsWith('deepseek-')) return 'DeepSeek 官方接口'
      if (m.startsWith('moonshot-')) return 'Moonshot (Kimi) 官方接口'
      if (m.startsWith('glm-')) return '智谱 (GLM) 官方接口'
      if (m.startsWith('MiniMax-') || m.startsWith('abab')) return 'MiniMax Anthropic兼容接口'
      if (m.startsWith('qwen-')) return '阿里云通义千问接口'
      return '支持 OpenAI 兼容接口'
    }
  },
  mounted() {
    this._onOpenAISettings = () => this.openModal()
    window.addEventListener('open-ai-settings', this._onOpenAISettings)
  },
  beforeUnmount() {
    window.removeEventListener('open-ai-settings', this._onOpenAISettings)
  },
  methods: {
    loadConfig() {
      try {
        const raw = localStorage.getItem(STORAGE_KEY)
        return raw ? JSON.parse(raw) : { model: '', token: '', baseUrl: '', customModel: '' }
      } catch {
        return { model: '', token: '', baseUrl: '', customModel: '' }
      }
    },
    saveToStorage(config) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(config))
    },
    openModal() {
      this.localConfig = { ...this.loadConfig() }
      this.statusMsg = ''
      this.visible = true
    },
    closeModal() {
      this.visible = false
      this.statusMsg = ''
    },
    setPreset(type) {
      if (type === 'openai') {
        this.localConfig.model = 'gpt-4o-mini'
        this.localConfig.baseUrl = 'https://api.openai.com/v1'
      } else if (type === 'deepseek') {
        this.localConfig.model = 'deepseek-chat'
        this.localConfig.baseUrl = 'https://api.deepseek.com/v1'
      } else if (type === 'moonshot') {
        this.localConfig.model = 'moonshot-v1-32k'
        this.localConfig.baseUrl = 'https://api.moonshot.cn/v1'
      } else if (type === 'glm') {
        this.localConfig.model = 'glm-4'
        this.localConfig.baseUrl = 'https://open.bigmodel.cn/api/paas/v4'
      } else if (type === 'minimax') {
        this.localConfig.model = 'MiniMax-M3'
        this.localConfig.baseUrl = 'https://api.minimaxi.com/anthropic'
      }
    },
    saveConfig() {
      if (!this.localConfig.model) {
        this.statusMsg = '请选择 AI 模型'
        this.statusType = 'error'
        return
      }
      if (this.localConfig.model === 'custom' && !this.localConfig.customModel) {
        this.statusMsg = '请输入自定义模型名称'
        this.statusType = 'error'
        return
      }
      if (!this.localConfig.baseUrl) {
        this.statusMsg = '请输入 API 地址'
        this.statusType = 'error'
        return
      }
      if (!this.localConfig.token) {
        this.statusMsg = '请输入 API Token'
        this.statusType = 'error'
        return
      }
      const config = { ...this.localConfig }
      if (config.model === 'custom') {
        config.model = config.customModel
      }
      this.saveToStorage(config)
      this.statusMsg = '保存成功！'
      this.statusType = 'success'
      this.$emit('config-changed', config)
      setTimeout(() => this.closeModal(), 800)
    },
    async testConnection() {
      if (!this.localConfig.model) {
        this.statusMsg = '请先选择 AI 模型'
        this.statusType = 'error'
        return
      }
      if (this.localConfig.model === 'custom' && !this.localConfig.customModel) {
        this.statusMsg = '请输入自定义模型名称'
        this.statusType = 'error'
        return
      }
      if (!this.localConfig.baseUrl) {
        this.statusMsg = '请先输入 API 地址'
        this.statusType = 'error'
        return
      }
      if (!this.localConfig.token) {
        this.statusMsg = '请先输入 API Token'
        this.statusType = 'error'
        return
      }
      this.testing = true
      this.statusMsg = '测试中...'
      this.statusType = ''
      try {
        const baseUrl = this.localConfig.baseUrl.replace(/\/+$/, '')
        const model = this.localConfig.model === 'custom'
          ? this.localConfig.customModel : this.localConfig.model

        // 通过后端代理测试，避免浏览器 CORS 限制
        const result = await testAIConnection(baseUrl, this.localConfig.token, model)

        if (result.success) {
          this.statusMsg = result.message || '连接成功！AI 服务可用 ✅'
          this.statusType = 'success'
        } else {
          this.statusMsg = '连接失败: ' + (result.message || '未知错误')
          this.statusType = 'error'
        }
      } catch (e) {
        this.statusMsg = '网络错误: ' + e.message
        this.statusType = 'error'
      } finally {
        this.testing = false
      }
    }
  }
}
</script>

<style scoped>
.ai-settings-trigger {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all 0.2s;
}
.ai-settings-trigger:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(43,91,253,0.04);
}
.ai-icon {
  font-size: 18px;
}
.ai-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
}
.ai-dot.off {
  background: var(--text-muted);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  background: var(--card-bg);
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
  width: 480px;
  max-width: 90vw;
  max-height: 85vh;
  overflow-y: auto;
  animation: fadeIn 0.2s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border);
}
.modal-header h3 {
  margin: 0;
  font-size: 16px;
}
.modal-close {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-muted);
  padding: 4px 8px;
  border-radius: 4px;
}
.modal-close:hover { background: var(--bg-secondary); color: var(--text-primary); }
.modal-body {
  padding: 20px 24px;
}
.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.required { color: var(--danger); }
.form-select, .form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.form-select:focus, .form-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(43,91,253,0.1);
}
.form-hint {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}
.input-with-btn {
  display: flex;
  gap: 6px;
}
.input-with-btn .form-input { flex: 1; }
.input-with-btn .btn { flex-shrink: 0; padding: 8px 10px; font-size: 16px; }
.btn-sm { padding: 4px 12px; font-size: 12px; }
.preset-buttons {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.status-msg {
  font-size: 13px;
  flex: 1;
  margin-right: 12px;
}
.status-msg.success { color: var(--success); }
.status-msg.error { color: var(--danger); }
</style>
