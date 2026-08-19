import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { initFavicon } from './utils/favicon'
import './styles/main.css'

initFavicon() // 网页图标按系统颜色切换，挂载前先设置一次

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
