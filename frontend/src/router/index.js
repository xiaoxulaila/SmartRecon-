import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import RecentReconciliations from '../views/RecentReconciliations.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: HomeView,
    meta: { title: '首页 - 智能对账系统' }
  },
  {
    path: '/recent',
    name: 'Recent',
    component: RecentReconciliations,
    meta: { title: '最近对账 - 智能对账系统' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title || '智能对账系统'
})

export default router
