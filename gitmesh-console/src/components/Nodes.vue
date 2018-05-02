<template>
  <div>
    <table class="table table-bordered mt-2">
      <tr>
        <th>URL</th>
        <th>Repositories</th>
        <th>Disk usage</th>
      </tr>
      <tr v-for="node in nodes" :key="node.url">
        <td>{{node.url}}</td>
        <td>
          <span class="badge badge-primary">Total</span> {{node.repos.length}}&nbsp;&nbsp;
          <span class="badge badge-success">Ready</span>  {{node.repos.filter(node => node.status == 'READY').length}}&nbsp;&nbsp;
          <span class="badge badge-secondary">Preparing</span> {{node.repos.filter(node => node.status == 'PREPARING').length}}&nbsp;&nbsp;
        </td>
        <td>{{node.diskUsage}}</td>
      </tr>
    </table>
  </div>
</template>

<script>
import Vue from 'vue'
import axios from 'axios'

export default {
  name: 'Nodes',
  data () {
    return {
      nodes: []
    }
  },
  props: ['controllerUrl'],
  watch: {
    controllerUrl: function () {
      fetchNodesInfo(this)
    }
  },
  created: function () {
    fetchNodesInfo(this)
  },
  methods: {
    refresh: function () {
      fetchNodesInfo(this)
    }
  }
}

function fetchNodesInfo (app) {
  axios(app.controllerUrl + '/api/nodes')
    .then(function (response) {
      Vue.set(app, 'nodes', response.data)
    }).catch(function (error) {
      Vue.set(app, 'nodes', [])
      alert(error)
    })
}
</script>
