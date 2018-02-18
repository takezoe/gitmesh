<template>
  <div class="hello">
    <h1>gitmesh cluster status</h1>

    <table class="table table-border table-hover">
      <tr>
        <th>URL</th>
        <th>Repositories</th>
        <th>Disk usage</th>
      </tr>
      <tr v-for="node in nodes" :key="node.url">
        <td>{{node.url}}</td>
        <td>
          <div v-for="repo in node.repos" :key="repo">{{repo}}</div>
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
  name: 'HelloWorld',
  data () {
    return {
      nodes: []
    }
  },
  created: function () {
    fetchNodesInfo(this)
  }
}

function fetchNodesInfo (app) {
  axios('http://localhost:8081/api/nodes').then(function (response) {
    Vue.set(app, 'nodes', response.data)
    app.$emit('GET_AJAX_COMPLETE')
  })
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
/*
h1, h2 {
  font-weight: normal;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
*/
</style>
