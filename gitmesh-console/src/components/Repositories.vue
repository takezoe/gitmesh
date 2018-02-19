<template>
  <div>
    <div class="mt-2 mb-2">
      <form class="form-inline">
        <input type="text" class="form-control" v-model="name" placeholder="Repository name..." size="50%" autocomplete="off" :disabled="creating"/>
        <button class="btn btn-success ml-1" v-on:click="create" :disabled="creating">Create</button>
      </form>
    </div>
    <table class="table table-bordered table-hover">
      <tr>
        <th>Name</th>
        <th>URL</th>
        <th>Nodes</th>
      </tr>
      <tr v-for="repo in repos" :key="repo.name">
        <td>{{repo.name}}</td>
        <td>http://localhost:8081/git/{{repo.name}}.git</td>
        <td>
          <div v-for="node in repo.nodes" :key="node">
            {{node}}
            <span class="badge badge-primary" v-if="node == repo.primaryNode">Primary</span>
          </div>
        </td>
      </tr>
    </table>
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
    fetchReposInfo(this)
  },
  methods: {
    refresh: function () {
      fetchReposInfo(this)
    },
    create: function () {
      // TODO validation
      createRepository(this)
    }
  }
}

function fetchReposInfo (app) {
  axios('http://localhost:8081/api/repos').then(function (response) {
    Vue.set(app, 'repos', response.data)
    // app.$emit('GET_AJAX_COMPLETE')
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
    fetchReposInfo(app)
  })
}
</script>
