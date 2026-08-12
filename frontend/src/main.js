import { createApp } from 'vue'
import axios from 'axios'
import App from './App.vue'

const apiKeyStorageKey = 'spring-rule-api-key'
const apiKeyHeader = import.meta.env.VITE_RULE_API_KEY_HEADER || 'X-Rule-Api-Key'

axios.interceptors.request.use(config => {
  const apiKey = window.sessionStorage.getItem(apiKeyStorageKey)
  if (apiKey) {
    config.headers = config.headers || {}
    config.headers[apiKeyHeader] = apiKey
  }
  return config
})

axios.interceptors.response.use(
  response => response,
  async error => {
    const config = error.config || {}
    if (error.response?.status === 401 && !config.__ruleApiKeyRetry) {
      const apiKey = window.prompt('Rule API key required')
      if (apiKey) {
        window.sessionStorage.setItem(apiKeyStorageKey, apiKey)
        config.__ruleApiKeyRetry = true
        config.headers = config.headers || {}
        config.headers[apiKeyHeader] = apiKey
        return axios(config)
      }
    }
    return Promise.reject(error)
  }
)

window.ruleAdminAuth = {
  clear() {
    window.sessionStorage.removeItem(apiKeyStorageKey)
  },
  configured() {
    return Boolean(window.sessionStorage.getItem(apiKeyStorageKey))
  }
}

createApp(App).mount('#app')
