<template>
  <div class="app-layout">
    <!-- 移动端遮罩 -->
    <div class="sidebar-overlay" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- 移动端菜单按钮 -->
    <button class="menu-toggle" @click="sidebarOpen = !sidebarOpen">☰</button>

    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-brand">
        <div class="sidebar-brand-icon">💰</div>
        <span class="sidebar-brand-text">对账系统</span>
      </div>
      <nav class="sidebar-nav">
        <router-link
          to="/"
          class="sidebar-nav-item"
          exact-active-class="active"
          @click="sidebarOpen = false"
        >
          <span class="sidebar-nav-icon">🏠</span>
          首页
        </router-link>
        <router-link
          to="/recent"
          class="sidebar-nav-item"
          active-class="active"
          @click="sidebarOpen = false"
        >
          <span class="sidebar-nav-icon">📋</span>
          最近对账
        </router-link>
      </nav>
      <div class="sidebar-footer">
        ©  智能对账系统
      </div>
    </aside>

    <!-- 主内容区域 -->
    <main class="main-content">
      <!-- 顶部栏 -->
      <div class="top-bar">
        <div class="top-bar-left">
          <span class="top-bar-title">💰 智能对账系统</span>
        </div>
        <div class="top-bar-right">
          <AISettings />
        </div>
      </div>
      <router-view />
    </main>
  </div>
</template>

<script>
import AISettings from './components/AISettings.vue'

export default {
  name: 'App',
  components: { AISettings },
  data() {
    return { sidebarOpen: false }
  }
}
</script>
