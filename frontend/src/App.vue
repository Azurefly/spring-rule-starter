
<template>
  <div style="padding:20px;font-family: Arial">
    <h2>Rule Manager (Demo)</h2>
    <div style="display:flex;gap:20px">
      <div style="width:320px;">
        <h3>Rules</h3>
        <button @click="fetchRules">Refresh</button>
        <ul>
          <li v-for="r in rules" :key="r.id" style="margin-bottom:8px">
            <strong>{{ r.name }}</strong> ({{ r.type }}) <br/>
            <button @click="edit(r)">Edit</button>
            <button @click="exec(r)">Execute</button>
          </li>
        </ul>
        <hr/>
        <h4>Upload New Rule</h4>
        <input v-model="uploadName" placeholder="name" /><br/><br/>
        <input type="file" @change="onFile" /><br/><br/>
        <select v-model="uploadType"><option>DROOLS</option><option>CUSTOM</option></select><br/><br/>
        <button @click="upload">Upload</button>
      </div>

      <div style="flex:1;">
        <h3>Editor / Execution</h3>
        <div v-if="selected">
          <h4>Editing: {{ selected.name }} <small v-if="selected">(v{{ selected.version }})</small></h4>\n          <div v-if="selected" style="margin-bottom:8px">Build: <strong>{{ selected.lastBuildStatus || 'N/A' }}</strong> <span v-if="selected.lastBuildAt">@ {{ selected.lastBuildAt }}</span></div>
          <textarea v-model="editorContent" style="width:100%;height:250px"></textarea><br/>
          <button @click="saveEdits">Save</button>
          <button @click="refreshRule">Refresh (rebuild)</button>
          <hr/>
          <h4>Execute</h4>
          <textarea v-model="execJson" style="width:100%;height:120px">{ "amount": 120 }</textarea><br/>
          <button @click="doExec">Run</button>
          <pre>{{ execResult }}</pre>
        </div>
        <div v-else>
          <em>Select a rule from the left or upload a new one.</em>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios'
export default {
  data(){ return {
    rules: [], uploadName:'', uploadType:'DROOLS', file:null, selected:null, editorContent:'', execJson:'', execResult:''
  }},
  methods: {
    async fetchRules(){
      const r = await axios.get('/api/rules/list');
      if (r.data && r.data.data) this.rules = r.data.data;
    },
    onFile(e){ this.file = e.target.files[0]; },
    async upload(){
      if(!this.uploadName || !this.file) { alert('need name and file'); return; }
      const fd = new FormData();
      fd.append('name', this.uploadName);
      fd.append('type', this.uploadType);
      fd.append('file', this.file);
      await axios.post('/api/rules/upload', fd);
      this.fetchRules();
    },
    edit(r){
      this.selected = r;
      axios.get('/api/rules/meta/' + r.name).then(res => {
        if(res.data && res.data.data) {
          this.editorContent = res.data.data.content || '';
          this.selected = res.data.data;
        }
      }).catch(()=>{
        axios.get('/api/rules/rule-content/' + r.name).then(res => {
          if(res.data && res.data.data) this.editorContent = res.data.data;
          else this.editorContent = r.content || '';
        }).catch(()=>{ this.editorContent = r.content || ''; });
      });
    },
    async saveEdits(){
      if(!this.selected) return alert('no rule selected');
      try {
        await axios.put('/api/rules/' + this.selected.name, this.editorContent);
        alert('Saved and rebuild requested.');
        this.fetchRules();
      } catch(e) { alert('save failed: ' + e.message); }
    },
    async exec(r){
      this.selected = r;
      this.execJson = JSON.stringify({amount:120}, null, 2);
    },
    async doExec(){
      try {
        const body = JSON.parse(this.execJson);
        const r = await axios.post('/api/rules/exec/' + this.selected.name, body);
        this.execResult = JSON.stringify(r.data, null, 2);
      } catch(e){
        this.execResult = e.message;
      }
    },
    async refreshRule(){
      await axios.post('/api/rules/refresh/' + this.selected.name);
      alert('Refresh requested (server will try to rebuild).');
    }
  },
  mounted(){ this.fetchRules(); }
}
</script>
