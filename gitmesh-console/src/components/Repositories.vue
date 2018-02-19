<template>
  <div>
    <div class="mt-2 mb-2">
      <form class="form-inline">
        <input type="text" class="form-control" v-model="name" placeholder="Repository name..." size="50%" autocomplete="off" :disabled="creating"/>
        <button class="btn btn-success ml-1" v-on:click="create" :disabled="creating">Create</button>
      </form>
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
      repos: [],
      name: '',
      creating: false
    }
  },
  created: function () {
    fetchNodesInfo(this)
  },
  methods: {
    refresh: function () {
      fetchNodesInfo(this)
    },
    create: function () {
      createRepository(this)
    }
  }
}

function fetchNodesInfo (app) {
  axios('http://localhost:8081/api/nodes').then(function (response) {
    Vue.set(app, 'nodes', response.data)
    app.$emit('GET_AJAX_COMPLETE')
  })
}

function createRepository (app) {
  let name = app.$data.name
  Vue.set(app, 'creating', true)
  axios({
    method: 'POST',
    url: 'http://localhost:8081/api/repos/' + name
  }).then(function (response) {
    Vue.set(app, 'name', '')
    Vue.set(app, 'creating', false)
    console.log(response)
  })
}
</script>
