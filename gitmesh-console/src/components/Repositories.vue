<template>
  <div>
    <div class="text-right mt-2 mb-2">
      <button class="btn btn-success">Create</button>
      <button class="btn btn-success" v-on:click="refresh">Refresh</button>
    </div>
  </div>
</template>

<script>
import Vue from 'vue'
import axios from 'axios'

export default {
  name: 'Repositories',
  data () {
    return {
      nodes: []
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
  axios('http://localhost:8081/api/nodes').then(function (response) {
    Vue.set(app, 'nodes', response.data)
    app.$emit('GET_AJAX_COMPLETE')
  })
}
</script>
