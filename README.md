# 🔍 SmartRecon — 智能对账系统

> 一站式对账解决方案：支持 Excel/CSV 上传、图片 OCR 识别、AI 智能对账，让财务对账不再头疼。

[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4.0-4FC08D.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-5.0.10-646CFF.svg)](https://vitejs.dev/)

---

## 📖 目录

- [项目简介](#项目简介)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [项目截图](#项目截图)
- [快速开始](#快速开始)
- [详细配置](#详细配置)
- [API 接口文档](#api-接口文档)
- [项目结构](#项目结构)
- [对账匹配逻辑](#对账匹配逻辑)
- [AI 模型支持](#ai-模型支持)
- [常见问题](#常见问题)
- [后续计划](#后续计划)
- [联系作者](#联系作者)
- [许可证](#许可证)

---

## 项目简介

**智能对账系统**是一套前后端分离的 Web 应用，帮助财务人员、电商运营、数据分析师快速完成两方数据的对账比对工作。

传统的对账方式需要手动打开两个 Excel 文件，肉眼逐行查找匹配行，极其低效且容易出错。本系统提供了**三种数据录入方式**和**两种对账引擎**，覆盖 90% 以上的对账场景。

### 适用场景

| 场景 | 举例 |
|------|------|
| 🏦 财务对账 | 银行流水 vs 内部账本 |
| 🛒 电商对账 | 平台订单 vs ERP 发货单 |
| 📊 数据校验 | 两张表的客户名单去重比对 |
| 🧾 发票核验 | 纸质发票拍照 → OCR → 对账 |
| 🤖 AI 对话对账 | 上传两张表，说"按姓名和金额匹配"，AI 自动完成 |

---

## 核心功能

### 📥 三种数据录入方式

#### 1. Excel/CSV 文件上传
- 支持 `.xlsx`、`.xls`、`.csv` 格式
- 自动解析表头和数据行
- 保留原始字段名和所有列数据

#### 2. 图片 OCR 识别
- 基于 **Tesseract OCR** 引擎
- 支持中文 + 英文混合识别
- 内置图像预处理：灰度化、放大、Otsu 二值化、去表格线
- 适合将纸质报表、截图中的数据提取为结构化数据

#### 3. AI 视觉识别
- 调用多模态大模型（GPT-4o、GLM-4V 等）直接"看懂"图片
- 无需本地部署 OCR 引擎
- 识别准确率更高，尤其是复杂表格和手写内容

### ⚙️ 两种对账引擎

#### 规则对账（传统模式）
- **多列自定义匹配**：选择任意列作为匹配键
- **四种匹配类型**：
  - `精确匹配` — 字符串完全相同
  - `包含匹配` — A 列值包含 B 列值
  - `数值容差` — 金额类数值在一定误差内视为相等
  - `日期容差` — 日期相差 N 天以内视为相等
- **匹配模式**：`ALL`（全部列匹配成功才算）或 `ANY`（任一列匹配就算）
- 一键对账，结果即时返回

#### AI 智能对账
- **规则模式**：AI 自动分析两份数据，智能发现匹配关系
- **对话模式**：用自然语言描述对账规则，如：
  > "按客户姓名和订单金额匹配，金额差异在 1 元以内算一致"
- 支持 OpenAI 兼容 API 和 Anthropic 兼容 API
- 内置多种模型的快捷预设

### 📊 结果展示

- 统计概览卡片：数据源记录数、匹配成功数、仅 A 条数、仅 B 条数
- 匹配率进度条可视化
- 三 Tab 切换：匹配成功 / 仅数据源 A / 仅数据源 B
- 差异高亮标注（有差异 / 一致）
- 一键导出 CSV
- 对账历史记录（本地存储，最多 50 条）

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.0 | Web 框架 |
| Apache POI | 5.2.5 | Excel 文件解析 (.xlsx/.xls) |
| OpenCSV | 5.9 | CSV 文件解析 |
| Tess4J | 5.9.0 | OCR 图片文字识别 |
| Jackson | - | JSON 序列化 |
| Lombok | - | 代码简化 |
| Maven | - | 项目管理与构建 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4.0 | 前端框架 (Options API) |
| Vite | 5.0.10 | 构建工具 |
| Vue Router | 4.2.5 | 路由管理 |
| Axios | 1.6.2 | HTTP 请求 |
| 纯 CSS | - | UI 样式（自定义设计系统） |

### 架构图

```
┌─────────────────────────────────────────────┐
│                   前端 (Vue 3)                │
│  ┌──────────┐ ┌──────────┐ ┌─────────────┐  │
│  │ FileUpload│ │ResultTable│ │ AISettings  │  │
│  └──────────┘ └──────────┘ └─────────────┘  │
│         localhost:3000 (Vite Dev Server)     │
└─────────────────────┬───────────────────────┘
                      │ HTTP (Axios)
                      │ Vite Proxy → localhost:8088
┌─────────────────────┴───────────────────────┐
│               后端 (Spring Boot 3)            │
│  ┌──────────────────────────────────────┐   │
│  │         UploadController             │   │
│  │  /api/upload/* 文件上传 & OCR 识别    │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │     ReconciliationController         │   │
│  │  /api/reconciliation/* 规则对账       │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │         AIController                 │   │
│  │  /api/ai/* AI 对账 & 模型对接         │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │        DataStoreService              │   │
│  │  ConcurrentHashMap 内存存储           │   │
│  └──────────────────────────────────────┘   │
│              localhost:8088                   │
└─────────────────────────────────────────────┘
```

---

## 项目截图

| 首页 | AI 设置 |
|:---:|:---:|
| ![首页](https://gitee.com/naitang_room/images/raw/master/%E5%AF%B9%E8%B4%A6%E7%B3%BB%E7%BB%9F%E9%A6%96%E9%A1%B5.png) | ![AI设置](https://gitee.com/naitang_room/images/raw/master/%E5%AF%B9%E8%B4%A6%E7%B3%BB%E7%BB%9Fai%E8%AE%BE%E7%BD%AE.png) |

| 历史记录 | 历史记录详情 |
|:---:|:---:|
| ![历史记录](https://gitee.com/naitang_room/images/raw/master/%E5%AF%B9%E8%B4%A6%E7%B3%BB%E7%BB%9F%E5%8E%86%E5%8F%B2%E8%AE%B0%E5%BD%95.png) | ![历史详情](https://gitee.com/naitang_room/images/raw/master/%E5%AF%B9%E8%B4%A6%E7%B3%BB%E7%BB%9F%E5%8E%86%E5%8F%B2%E8%AE%B0%E5%BD%95%E8%AF%A6%E6%83%85.png) |

---

## 快速开始

### 环境要求

- **JDK 17+** — [下载地址](https://www.oracle.com/java/technologies/downloads/#java17)
- **Maven 3.8+** — [下载地址](https://maven.apache.org/download.cgi)
- **Node.js 18+** — [下载地址](https://nodejs.org/)
- **Tesseract OCR** *(可选，仅 OCR 功能需要)* — [下载地址](https://github.com/UB-Mannheim/tesseract/wiki)

### 1. 克隆项目

```bash
git clone https://github.com/xiaoxulaila/SmartRecon-.git
cd 对账项目
```

### 2. 启动后端

```bash
cd backend

# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

后端启动后运行在 `http://localhost:8088`

### 3. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端启动后运行在 `http://localhost:3000`

浏览器打开 `http://localhost:3000` 即可使用。

### 4. (可选) 安装 Tesseract OCR

如果需要使用图片 OCR 识别功能：

1. 下载安装 [Tesseract OCR](https://github.com/UB-Mannheim/tesseract/wiki)（Windows 推荐用这个版本）
2. 安装时勾选 **中文简体 (chi_sim)** 语言包
3. 确保 `tesseract` 命令可在命令行中执行：
   ```bash
   tesseract --version
   ```
4. 如未自动加入 PATH，在 `backend/src/main/resources/application.yml` 中配置：
   ```yaml
   ocr:
     tesseract-cmd: "C:/Program Files/Tesseract-OCR/tesseract.exe"
   ```

---

## 详细配置

### 后端配置 (`application.yml`)

```yaml
server:
  port: 8088                         # 后端服务端口

spring:
  servlet.multipart:
    max-file-size: 50MB              # 上传文件大小限制
    max-request-size: 50MB
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

ocr:                                 # OCR 配置（可选）
  enabled: true
  tesseract-cmd: tesseract           # Tesseract 命令行路径
  tessdata-path: C:/Program Files/Tesseract-OCR/tessdata
  language: chi_sim+eng              # 识别语言
  preprocess: true                   # 开启图像预处理
  scale: 2                           # 图片放大倍数

upload:
  path: ./uploads                    # 上传文件存储路径

reconciliation:
  amount-tolerance: 0.01             # 金额容差（元）
  date-tolerance-days: 3             # 日期容差（天）
```

### AI 配置

在系统界面的 **AI 设置** 面板中配置，支持以下模型的 OpenAI 兼容 API：

| 模型 | 预设 Base URL | 推荐模型名 |
|------|-------------|-----------|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| Kimi (Moonshot) | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |
| GLM (智谱) | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` |
| MiniMax | `https://api.minimax.chat/v1` | `abab6.5s-chat` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |

配置会自动保存在浏览器 localStorage 中。

---

## API 接口文档

### 文件上传

#### 上传文件
```http
POST /api/upload/file
Content-Type: multipart/form-data

file: 对账文件 (Excel/CSV/图片)
```

**响应示例：**
```json
{
  "dataId": "abc123",
  "fileName": "1月流水.xlsx",
  "recordCount": 150,
  "headers": ["姓名", "金额", "日期", "备注"],
  "success": true
}
```

#### 获取数据源列表
```http
GET /api/upload/sources
```

#### 获取指定数据源
```http
GET /api/upload/data/{dataId}
```

#### 删除数据源
```http
DELETE /api/upload/data/{dataId}
```

### 规则对账

```http
POST /api/reconciliation/run
Content-Type: application/json

{
  "dataSourceAId": "abc123",
  "dataSourceBId": "def456",
  "matchColumns": [
    {
      "sourceColumn": "姓名",
      "targetColumn": "客户名",
      "matchType": "EXACT"
    },
    {
      "sourceColumn": "金额",
      "targetColumn": "订单金额",
      "matchType": "NUMERIC_TOLERANCE",
      "tolerance": 0.01
    }
  ],
  "matchMode": "ALL"
}
```

**响应示例：**
```json
{
  "matchedCount": 142,
  "matchRate": 94.6,
  "onlyInSource": [...],
  "onlyInTarget": [...],
  "matchedPairs": [
    {
      "source": { "rawData": { "姓名": "张三", "金额": 100.50 } },
      "target": { "rawData": { "客户名": "张三", "订单金额": 100.50 } },
      "hasDifference": false
    }
  ]
}
```

### AI 对账

```http
POST /api/ai/reconcile
Content-Type: application/json

{
  "dataSourceAId": "abc123",
  "dataSourceBId": "def456",
  "model": "gpt-4o",
  "apiKey": "sk-xxx",
  "baseUrl": "https://api.openai.com/v1",
  "customPrompt": "按姓名和金额匹配，金额容差0.01元"
}
```

#### 测试 AI 连接

```http
POST /api/ai/test
Content-Type: application/json

{
  "model": "gpt-4o",
  "apiKey": "sk-xxx",
  "baseUrl": "https://api.openai.com/v1"
}
```

---

## 项目结构

```
对账项目/
├── README.md                         # 项目说明文档
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Maven 依赖配置
│   └── src/main/
│       ├── java/com/reconciliation/
│       │   ├── ReconciliationApplication.java   # 启动入口
│       │   ├── config/
│       │   │   ├── WebConfig.java              # CORS + RestTemplate 配置
│       │   │   └── GlobalExceptionHandler.java  # 全局异常处理
│       │   ├── controller/
│       │   │   ├── UploadController.java         # 文件上传接口
│       │   │   ├── ReconciliationController.java # 规则对账接口
│       │   │   └── AIController.java             # AI 对账接口
│       │   ├── dto/
│       │   │   ├── ReconRequest.java             # 对账请求 DTO
│       │   │   └── AIReconRequest.java           # AI 对账请求 DTO
│       │   ├── model/
│       │   │   ├── ReconRecord.java              # 对账记录模型
│       │   │   ├── ReconResult.java              # 对账结果模型
│       │   │   └── UploadResponse.java           # 上传响应模型
│       │   └── service/
│       │       ├── ReconciliationService.java    # 对账核心引擎
│       │       ├── ExcelService.java             # Excel/CSV 解析
│       │       ├── OcrService.java               # OCR 图片识别
│       │       ├── AIService.java                # AI 大模型对接
│       │       └── DataStoreService.java         # 内存数据存储
│       └── resources/
│           └── application.yml                   # 应用配置
│
└── frontend/                         # Vue 3 前端
    ├── package.json                  # 前端依赖配置
    ├── vite.config.js               # Vite 配置（含 API 代理）
    ├── index.html                   # HTML 入口
    └── src/
        ├── main.js                  # Vue 入口
        ├── App.vue                  # 根组件（侧边栏布局）
        ├── router/
        │   └── index.js             # 路由配置
        ├── api/
        │   └── index.js             # Axios API 封装
        ├── views/
        │   ├── HomeView.vue         # 首页（对账主流程）
        │   └── RecentReconciliations.vue  # 历史记录页
        ├── components/
        │   ├── FileUpload.vue       # 文件上传组件
        │   ├── ResultTable.vue      # 对账结果表格
        │   └── AISettings.vue       # AI 设置弹窗
        ├── utils/
        │   └── history.js           # 本地历史记录工具
        └── styles/
            └── main.css             # 全局样式
```

---

## 对账匹配逻辑

### 规则对账的匹配流程

```
数据源A (150条)              数据源B (148条)
    │                            │
    ├─── 解析 matchColumns ──────┤
    │    列映射: 姓名→客户名      │
    │    匹配类型: EXACT         │
    │    列映射: 金额→订单金额    │
    │    匹配类型: NUMERIC_TOLERANCE │
    │                            │
    ├─── 双重循环比对 ───────────┤
    │    for (A记录 in 数据源A)   │
    │      for (B记录 in 数据源B) │
    │        检查所有列是否匹配    │
    │                            │
    ▼                            ▼
 匹配成功(142对)             仅A(8条)  仅B(6条)
```

### AI 对账的匹配流程

```
数据源A ──→ ┐
            ├──→ 拼接为 Prompt ──→ AI 大模型 ──→ JSON 结果 ──→ 解析展示
数据源B ──→ ┘
```

---

## AI 模型支持

本系统通过 **OpenAI 兼容 API** 协议对接大模型，理论上支持所有兼容该协议的服务：

| 服务商 | 需要 API Key | 对账场景推荐模型 |
|--------|:----------:|--------------|
| [OpenAI](https://platform.openai.com/) | ✅ | `gpt-4o`、`gpt-4o-mini` |
| [DeepSeek](https://platform.deepseek.com/) | ✅ | `deepseek-chat` |
| [Moonshot (Kimi)](https://platform.moonshot.cn/) | ✅ | `moonshot-v1-8k` |
| [智谱 AI (GLM)](https://open.bigmodel.cn/) | ✅ | `glm-4-flash` |
| [MiniMax](https://platform.minimax.chat/) | ✅ | `abab6.5s-chat` |
| [阿里云通义千问](https://dashscope.aliyun.com/) | ✅ | `qwen-plus` |
| [Ollama (本地部署)](https://ollama.com/) | ❌ 免费 | `qwen2.5`、`llama3` |

> 💡 **免费方案**：使用 Ollama 在本地部署开源模型，完全免费且数据不出电脑。

---

## 常见问题

<details>
<summary><b>Q: OCR 识别报错 "tesseract is not recognized"？</b></summary>

A: 需要安装 Tesseract OCR 并配置环境变量。详见 [安装 Tesseract OCR](#4-可选-安装-tesseract-ocr)。
</details>

<details>
<summary><b>Q: AI 对账显示"连接失败"？</b></summary>

A: 请检查：
1. API Key 是否填写正确
2. Base URL 是否与模型服务商匹配
3. 账户是否有余额
4. 网络是否能访问对应服务（部分服务可能需要代理）
</details>

<details>
<summary><b>Q: 上传的 Excel 文件提示解析失败？</b></summary>

A: 确保文件是 `.xlsx`、`.xls` 或 `.csv` 格式。如果文件有密码保护或使用了特殊的 Excel 特性（如合并单元格），可能会解析失败。
</details>

<details>
<summary><b>Q: 对账结果能保存吗？重启后还在吗？</b></summary>

A: 当前版本的对账历史保存在浏览器的 `localStorage` 中（最多 50 条），清除浏览器数据或换浏览器会丢失。后续版本计划加入数据库持久化。
</details>

<details>
<summary><b>Q: 如何部署到服务器？</b></summary>

A:
```bash
# 后端打包
cd backend
mvn clean package -DskipTests
java -jar target/reconciliation-0.0.1-SNAPSHOT.jar

# 前端打包
cd frontend
npm run build
# 将 dist/ 目录部署到 Nginx 或其他静态服务器
```
</details>

---

## 后续计划

- [ ] 数据库持久化（MySQL/PostgreSQL）
- [ ] 用户登录与多用户支持
- [ ] 对账结果差异详情钻取
- [ ] 批量对账（一次上传多组文件）
- [ ] 对账报告自动生成（PDF/Word）
- [ ] Docker 一键部署
- [ ] 微信小程序版本
- [ ] 更多 AI 模型适配（Claude、Gemini）
- [ ] 对账模板保存与复用

---

## 联系作者

### 📱 社交媒体

| 平台 | 链接 |
|:---:|------|
| 📺 **Bilibili** | [点击观看视频](https://www.bilibili.com/video/BV19bNd6bEqh/) |
| 🎵 **抖音** | [点击观看实战演示](https://v.douyin.com/Zzln1BSknsc/) |
| 🎬 **微信视频号** | [点击观看操作](https://weixin.qq.com/sph/ADBSdw8z25) |
| 💬 **微信公众号** | ![公众号](https://gitee.com/naitang_room/images/raw/master/%E5%85%AC%E4%BC%97%E5%8F%B7%E4%BA%8C%E7%BB%B4%E7%A0%81_258.jpg) |

### 🐛 问题反馈

- **GitHub Issues**: [提交 Bug 或功能建议](https://github.com/xiaoxulaila/SmartRecon-/issues)
- **Pull Request**: 欢迎贡献代码！

---

## 许可证

本项目基于 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) 开源，允许自由使用、修改、分享，但**禁止商业用途**。商用需联系作者获取单独授权。

---

## ⭐ Star History

如果这个项目对你有帮助，请给一个 Star ⭐ 支持一下！

[![Star History Chart](https://api.star-history.com/svg?repos=xiaoxulaila/SmartRecon-&type=Date)](https://star-history.com/#xiaoxulaila/SmartRecon-&Date)

---

<p align="center">
  <b>Made with ❤️ by xiaoxulaila</b>
</p>