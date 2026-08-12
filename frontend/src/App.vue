<template>
  <div class="app-shell">
    <header class="topbar">
      <div>
        <div class="eyebrow">SPRING RULE STARTER · v0.1</div>
        <h1>Rule Console</h1>
      </div>
      <div class="topbar-actions">
        <span class="health" :class="healthOk ? 'ok' : 'down'">
          <span class="dot"></span>{{ healthOk ? `Backend · ${loadedCount} loaded` : 'Backend unavailable' }}
        </span>
        <button class="ghost" @click="reloadAll">Reload all</button>
      </div>
    </header>

    <div v-if="notice" class="notice" :class="noticeType">{{ notice }}</div>

    <main class="workspace">
      <aside class="sidebar panel">
        <div class="panel-heading">
          <div>
            <div class="label">Rules</div>
            <strong>{{ rules.length }}</strong>
          </div>
          <button class="icon-button" title="Refresh" @click="fetchRules">↻</button>
        </div>

        <input v-model="query" class="input search" placeholder="Search rules" />

        <div class="rule-list">
          <button
            v-for="rule in filteredRules"
            :key="rule.id"
            class="rule-row"
            :class="{ active: selected && selected.name === rule.name }"
            @click="selectRule(rule)"
          >
            <div class="rule-row-main">
              <strong>{{ rule.name }}</strong>
              <span class="badge" :class="statusClass(rule.status)">{{ rule.status }}</span>
            </div>
            <div class="rule-row-meta">
              <span>v{{ rule.version || 1 }}</span>
              <span :class="buildClass(rule.lastBuildStatus)">{{ rule.lastBuildStatus || 'NOT_BUILT' }}</span>
            </div>
          </button>
          <div v-if="!filteredRules.length" class="empty compact">No matching rules.</div>
        </div>

        <div class="upload-card">
          <div class="label">Create from DRL</div>
          <input v-model.trim="uploadName" class="input" placeholder="discount-rule" />
          <input class="file-input" type="file" accept=".drl,text/plain" @change="onFile" />
          <div class="hint">Name: letters, numbers, <code>._-</code>, max 120 chars.</div>
          <button class="primary full" :disabled="busy || !uploadName || !file" @click="upload">
            Upload & compile
          </button>
        </div>
      </aside>

      <section class="main-column">
        <template v-if="selected">
          <section class="panel editor-panel">
            <div class="editor-header">
              <div>
                <div class="label">Editing rule</div>
                <div class="title-line">
                  <h2>{{ selected.name }}</h2>
                  <span class="badge" :class="statusClass(selected.status)">{{ selected.status }}</span>
                  <span class="version">v{{ selected.version || 1 }}</span>
                </div>
                <div class="build-line">
                  Last build:
                  <strong :class="buildClass(selected.lastBuildStatus)">{{ selected.lastBuildStatus || 'NOT_BUILT' }}</strong>
                  <span v-if="selected.lastBuildAt">· {{ formatDate(selected.lastBuildAt) }}</span>
                </div>
              </div>
              <div class="toolbar">
                <button class="ghost" :disabled="busy" @click="validateEditor">Validate</button>
                <button class="ghost" :disabled="busy" @click="refreshRule">Rebuild</button>
                <button class="ghost" :disabled="busy" @click="toggleStatus">
                  {{ selected.status === 'ENABLED' ? 'Disable' : 'Enable' }}
                </button>
                <button class="danger-ghost" :disabled="busy" @click="deleteRule">Delete</button>
                <button class="primary" :disabled="busy || !editorContent.trim()" @click="saveEdits">Save new version</button>
              </div>
            </div>

            <textarea
              v-model="editorContent"
              class="code-editor"
              spellcheck="false"
              aria-label="DRL editor"
            ></textarea>

            <div v-if="selected.lastBuildMessage" class="build-message">
              {{ selected.lastBuildMessage }}
            </div>
          </section>

          <div class="lower-grid">
            <section class="panel execution-panel">
              <div class="section-title">
                <div>
                  <div class="label">Test execution</div>
                  <h3>Run a fact</h3>
                </div>
                <select v-model="execMode" class="select">
                  <option value="order">Order demo</option>
                  <option value="map">Generic Map</option>
                </select>
              </div>
              <p class="hint execution-hint">
                <template v-if="execMode === 'order'">Legacy compatibility mode: JSON must contain <code>amount</code>.</template>
                <template v-else>Map mode inserts the JSON object directly as a <code>java.util.Map</code> Drools fact.</template>
              </p>
              <textarea v-model="execJson" class="json-editor" spellcheck="false"></textarea>
              <button class="primary" :disabled="busy || selected.status !== 'ENABLED'" @click="doExec">Run rule</button>
              <pre class="result-view">{{ execResult || 'Execution result will appear here.' }}</pre>
            </section>

            <section class="panel history-panel">
              <div class="section-title">
                <div>
                  <div class="label">Audit trail</div>
                  <h3>Build history</h3>
                </div>
                <button class="ghost small" @click="loadHistory">Refresh</button>
              </div>

              <div class="history-list">
                <div v-for="item in history" :key="item.id" class="history-row">
                  <div>
                    <div class="history-top">
                      <strong>v{{ item.version }}</strong>
                      <span :class="buildClass(item.status)">{{ item.status }}</span>
                    </div>
                    <div class="history-meta">{{ item.builtBy || 'system' }} · {{ formatDate(item.builtAt) }}</div>
                    <div v-if="item.message" class="history-message">{{ item.message }}</div>
                  </div>
                  <button
                    class="ghost small"
                    :disabled="busy || item.status !== 'SUCCESS' || item.version === selected.version"
                    @click="rollback(item.version)"
                  >
                    Rollback
                  </button>
                </div>
                <div v-if="!history.length" class="empty compact">No build history yet.</div>
              </div>
            </section>
          </div>
        </template>

        <section v-else class="panel empty-state">
          <div class="empty-icon">⌁</div>
          <h2>Select a rule</h2>
          <p>Choose a rule from the left, or upload a DRL file to create the first rule.</p>
        </section>
      </section>
    </main>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  data() {
    return {
      rules: [],
      query: '',
      uploadName: '',
      file: null,
      selected: null,
      editorContent: '',
      history: [],
      execMode: 'order',
      execJson: JSON.stringify({ amount: 120 }, null, 2),
      execResult: '',
      notice: '',
      noticeType: 'info',
      busy: false,
      healthOk: false,
      loadedCount: 0,
      noticeTimer: null
    }
  },
  computed: {
    filteredRules() {
      const q = this.query.trim().toLowerCase()
      if (!q) return this.rules
      return this.rules.filter(rule => rule.name.toLowerCase().includes(q))
    }
  },
  methods: {
    unwrap(response) {
      const payload = response && response.data
      if (!payload || payload.code !== 200) {
        throw new Error((payload && payload.message) || 'Request failed')
      }
      return payload.data
    },
    showNotice(message, type = 'info') {
      this.notice = message
      this.noticeType = type
      if (this.noticeTimer) clearTimeout(this.noticeTimer)
      this.noticeTimer = setTimeout(() => { this.notice = '' }, 4500)
    },
    async withBusy(action) {
      if (this.busy) return
      this.busy = true
      try {
        await action()
      } catch (error) {
        const message = error.response?.data?.message || error.message || 'Request failed'
        this.showNotice(message, 'error')
      } finally {
        this.busy = false
      }
    },
    async fetchHealth() {
      try {
        const data = this.unwrap(await axios.get('/api/rules/health'))
        this.healthOk = data.status === 'UP'
        this.loadedCount = data.loadedRules || 0
      } catch (_) {
        this.healthOk = false
      }
    },
    async fetchRules() {
      try {
        const selectedName = this.selected?.name
        this.rules = this.unwrap(await axios.get('/api/rules/list')) || []
        if (selectedName) {
          const stillExists = this.rules.find(rule => rule.name === selectedName)
          if (stillExists) await this.loadMeta(selectedName)
          else this.selected = null
        }
      } catch (error) {
        this.showNotice(error.message, 'error')
      }
      await this.fetchHealth()
    },
    async selectRule(rule) {
      await this.loadMeta(rule.name)
      await this.loadHistory()
      this.execResult = ''
    },
    async loadMeta(name) {
      const meta = this.unwrap(await axios.get('/api/rules/meta/' + encodeURIComponent(name)))
      this.selected = meta
      this.editorContent = meta.content || ''
    },
    async loadHistory() {
      if (!this.selected) return
      try {
        const page = this.unwrap(await axios.get('/api/rules/history/' + encodeURIComponent(this.selected.name), {
          params: { page: 0, size: 50 }
        }))
        this.history = page?.items || []
      } catch (error) {
        this.showNotice(error.message, 'error')
      }
    },
    onFile(event) {
      this.file = event.target.files?.[0] || null
    },
    async upload() {
      await this.withBusy(async () => {
        const form = new FormData()
        form.append('name', this.uploadName)
        form.append('type', 'DROOLS')
        form.append('file', this.file)
        this.unwrap(await axios.post('/api/rules/upload', form))
        const createdName = this.uploadName
        this.uploadName = ''
        this.file = null
        this.showNotice('Rule compiled and created.', 'success')
        await this.fetchRules()
        await this.loadMeta(createdName)
        await this.loadHistory()
      })
    },
    async validateEditor() {
      if (!this.selected) return
      await this.withBusy(async () => {
        this.unwrap(await axios.post('/api/rules/validate', this.editorContent, {
          params: { name: this.selected.name },
          headers: { 'Content-Type': 'text/plain' }
        }))
        this.showNotice('Validation passed. Active rule was not changed.', 'success')
      })
    },
    async saveEdits() {
      if (!this.selected) return
      await this.withBusy(async () => {
        this.unwrap(await axios.put('/api/rules/' + encodeURIComponent(this.selected.name), this.editorContent, {
          headers: { 'Content-Type': 'text/plain' }
        }))
        this.showNotice('New rule version saved and activated.', 'success')
        await this.fetchRules()
        await this.loadHistory()
      })
    },
    async toggleStatus() {
      if (!this.selected) return
      const next = this.selected.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      await this.withBusy(async () => {
        this.unwrap(await axios.patch('/api/rules/' + encodeURIComponent(this.selected.name) + '/status', null, {
          params: { status: next }
        }))
        this.showNotice(`Rule ${next.toLowerCase()}.`, 'success')
        await this.fetchRules()
        await this.loadHistory()
      })
    },
    async deleteRule() {
      if (!this.selected || !window.confirm(`Delete rule "${this.selected.name}" and its history?`)) return
      const name = this.selected.name
      await this.withBusy(async () => {
        this.unwrap(await axios.delete('/api/rules/' + encodeURIComponent(name)))
        this.selected = null
        this.history = []
        this.showNotice('Rule deleted.', 'success')
        await this.fetchRules()
      })
    },
    async refreshRule() {
      if (!this.selected) return
      await this.withBusy(async () => {
        this.unwrap(await axios.post('/api/rules/refresh/' + encodeURIComponent(this.selected.name)))
        this.showNotice('Rule rebuilt from the database.', 'success')
        await this.fetchRules()
      })
    },
    async reloadAll() {
      await this.withBusy(async () => {
        this.unwrap(await axios.post('/api/rules/reload-all'))
        this.showNotice('All enabled rules reloaded.', 'success')
        await this.fetchRules()
      })
    },
    async doExec() {
      if (!this.selected) return
      await this.withBusy(async () => {
        let body
        try {
          body = JSON.parse(this.execJson)
        } catch (_) {
          throw new Error('Execution input must be valid JSON.')
        }
        const endpoint = this.execMode === 'map' ? 'exec-map' : 'exec'
        const data = this.unwrap(await axios.post(`/api/rules/${endpoint}/${encodeURIComponent(this.selected.name)}`, body))
        this.execResult = JSON.stringify(data, null, 2)
      })
    },
    async rollback(version) {
      if (!this.selected || !window.confirm(`Create a new version from successful snapshot v${version}?`)) return
      await this.withBusy(async () => {
        this.unwrap(await axios.post(`/api/rules/rollback/${encodeURIComponent(this.selected.name)}/${version}`))
        this.showNotice(`Rolled back from v${version} as a new active version.`, 'success')
        await this.fetchRules()
        await this.loadHistory()
      })
    },
    statusClass(status) {
      return status === 'ENABLED' ? 'enabled' : 'disabled'
    },
    buildClass(status) {
      return status === 'SUCCESS' ? 'build-success' : status === 'FAILURE' ? 'build-failure' : 'build-muted'
    },
    formatDate(value) {
      if (!value) return '—'
      const date = new Date(value)
      return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
    }
  },
  mounted() {
    this.fetchRules()
  },
  beforeUnmount() {
    if (this.noticeTimer) clearTimeout(this.noticeTimer)
  }
}
</script>

<style>
:root {
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  color: #172033;
  background: #f4f6f9;
  font-synthesis: none;
}
* { box-sizing: border-box; }
body { margin: 0; background: #f4f6f9; }
button, input, textarea, select { font: inherit; }
button { cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .55; }
code { font-family: "SFMono-Regular", Consolas, monospace; }

.app-shell { min-height: 100vh; }
.topbar {
  height: 78px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #111827;
  color: #fff;
  border-bottom: 1px solid #293244;
}
.topbar h1 { margin: 3px 0 0; font-size: 22px; letter-spacing: -.02em; }
.eyebrow, .label { font-size: 11px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; color: #7f8aa3; }
.topbar .eyebrow { color: #9ca8bd; }
.topbar-actions { display: flex; align-items: center; gap: 12px; }
.health { display: inline-flex; align-items: center; gap: 7px; font-size: 13px; color: #c9d2e3; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #ef4444; }
.health.ok .dot { background: #34d399; }

.notice {
  position: fixed;
  top: 90px;
  right: 24px;
  z-index: 20;
  max-width: 520px;
  padding: 12px 16px;
  border-radius: 10px;
  box-shadow: 0 12px 30px rgba(17, 24, 39, .16);
  background: #fff;
  border: 1px solid #dce2ea;
  font-size: 14px;
}
.notice.success { border-color: #a7e2c8; color: #12623d; background: #f2fcf7; }
.notice.error { border-color: #f0b8b8; color: #9f2424; background: #fff5f5; }

.workspace { display: grid; grid-template-columns: 330px minmax(0, 1fr); gap: 18px; padding: 18px; min-height: calc(100vh - 78px); }
.panel { background: #fff; border: 1px solid #dfe4eb; border-radius: 12px; box-shadow: 0 1px 2px rgba(17, 24, 39, .035); }
.sidebar { padding: 16px; display: flex; flex-direction: column; min-height: 0; }
.panel-heading, .section-title, .editor-header, .title-line, .rule-row-main, .rule-row-meta, .history-top {
  display: flex;
  align-items: center;
}
.panel-heading, .section-title, .editor-header { justify-content: space-between; gap: 14px; }
.panel-heading strong { display: block; margin-top: 3px; font-size: 20px; }

.input, .select {
  width: 100%;
  border: 1px solid #d6dce5;
  border-radius: 8px;
  padding: 9px 10px;
  background: #fff;
  color: #182033;
  outline: none;
}
.input:focus, .select:focus, textarea:focus { border-color: #6f84ff; box-shadow: 0 0 0 3px rgba(90, 111, 238, .10); }
.search { margin: 14px 0 10px; }
.rule-list { overflow: auto; min-height: 130px; max-height: calc(100vh - 390px); }
.rule-row {
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  padding: 10px;
  margin-bottom: 5px;
  color: inherit;
}
.rule-row:hover { background: #f7f8fb; }
.rule-row.active { background: #f1f3ff; border-color: #cfd5ff; }
.rule-row-main { justify-content: space-between; gap: 10px; }
.rule-row-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rule-row-meta { justify-content: space-between; margin-top: 7px; color: #7b8498; font-size: 12px; }

.badge { display: inline-flex; padding: 3px 7px; border-radius: 99px; font-size: 10px; font-weight: 800; letter-spacing: .04em; }
.badge.enabled { background: #e8f8ef; color: #147047; }
.badge.disabled { background: #eef0f4; color: #687186; }
.build-success { color: #15804f; }
.build-failure { color: #c53d3d; }
.build-muted { color: #7c8799; }

.upload-card { margin-top: auto; padding-top: 16px; border-top: 1px solid #e5e9ef; display: grid; gap: 9px; }
.file-input { width: 100%; font-size: 12px; color: #5c6679; }
.hint { color: #747f92; font-size: 12px; line-height: 1.5; }

.main-column { min-width: 0; display: flex; flex-direction: column; gap: 18px; }
.editor-panel { min-height: 485px; overflow: hidden; }
.editor-header { padding: 18px 18px 14px; border-bottom: 1px solid #e5e9ef; }
.title-line { gap: 9px; margin-top: 4px; }
.title-line h2 { margin: 0; font-size: 21px; }
.version { color: #758096; font-size: 12px; }
.build-line { margin-top: 7px; color: #7a8496; font-size: 12px; }
.toolbar { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.code-editor, .json-editor {
  width: 100%;
  resize: vertical;
  border: 0;
  outline: none;
  padding: 16px 18px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  line-height: 1.65;
  color: #d9e1ef;
  background: #111827;
}
.code-editor { min-height: 360px; }
.json-editor { min-height: 150px; border: 1px solid #dce2ea; border-radius: 9px; color: #d9e1ef; margin: 8px 0 10px; }
.build-message { border-top: 1px solid #e5e9ef; padding: 10px 18px; color: #657084; background: #fafbfc; font-size: 12px; white-space: pre-wrap; }

.lower-grid { display: grid; grid-template-columns: minmax(0, .9fr) minmax(360px, 1.1fr); gap: 18px; }
.execution-panel, .history-panel { padding: 16px; min-width: 0; }
.section-title h3 { margin: 3px 0 0; font-size: 17px; }
.section-title .select { width: 150px; padding: 7px 9px; }
.execution-hint { min-height: 38px; margin: 10px 0 0; }
.result-view { margin: 12px 0 0; padding: 12px; min-height: 100px; max-height: 240px; overflow: auto; background: #f7f8fa; border: 1px solid #e3e7ed; border-radius: 9px; font-size: 12px; white-space: pre-wrap; }

.history-list { margin-top: 10px; max-height: 390px; overflow: auto; }
.history-row { display: grid; grid-template-columns: 1fr auto; gap: 12px; padding: 11px 0; border-bottom: 1px solid #edf0f4; }
.history-top { gap: 9px; font-size: 13px; }
.history-meta { margin-top: 4px; color: #8a94a6; font-size: 11px; }
.history-message { margin-top: 5px; color: #667186; font-size: 12px; white-space: pre-wrap; word-break: break-word; }

button.primary, button.ghost, button.danger-ghost, button.icon-button {
  border-radius: 8px;
  padding: 8px 11px;
  border: 1px solid transparent;
  transition: background .15s ease, border-color .15s ease;
}
button.primary { background: #5367e8; color: #fff; border-color: #5367e8; }
button.primary:hover:not(:disabled) { background: #4356d1; }
button.ghost { background: #fff; color: #344058; border-color: #d5dce6; }
.topbar button.ghost { background: #1d2637; border-color: #39445a; color: #e7ebf2; }
button.ghost:hover:not(:disabled) { background: #f6f7f9; }
.topbar button.ghost:hover:not(:disabled) { background: #263147; }
button.danger-ghost { background: #fff; color: #b33535; border-color: #e7c9c9; }
button.danger-ghost:hover:not(:disabled) { background: #fff4f4; }
button.icon-button { width: 34px; height: 34px; padding: 0; background: #f8f9fb; border-color: #dde3ea; color: #596579; font-size: 17px; }
button.small { padding: 5px 8px; font-size: 12px; }
button.full { width: 100%; }

.empty { color: #8993a4; font-size: 13px; }
.empty.compact { padding: 18px 8px; text-align: center; }
.empty-state { flex: 1; display: flex; min-height: 500px; align-items: center; justify-content: center; flex-direction: column; text-align: center; color: #69758a; }
.empty-state h2 { margin: 8px 0 4px; color: #263247; }
.empty-state p { margin: 0; max-width: 460px; }
.empty-icon { font-size: 42px; color: #96a1b5; }

@media (max-width: 1100px) {
  .workspace { grid-template-columns: 280px minmax(0, 1fr); }
  .lower-grid { grid-template-columns: 1fr; }
  .editor-header { align-items: flex-start; flex-direction: column; }
  .toolbar { justify-content: flex-start; }
}
@media (max-width: 760px) {
  .topbar { height: auto; padding: 16px; align-items: flex-start; gap: 12px; flex-direction: column; }
  .workspace { grid-template-columns: 1fr; padding: 10px; }
  .rule-list { max-height: 260px; }
  .topbar-actions { width: 100%; justify-content: space-between; }
}
</style>
