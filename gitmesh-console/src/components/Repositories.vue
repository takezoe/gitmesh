<template>
  <div>
    <div class="mt-2">
      <form class="form-inline">
        <input type="text" class="form-control" v-model="name" placeholder="Repository name" size="50%" autocomplete="off" :disabled="connecting"/>
        <button class="btn btn-success ml-1" v-on:click="createRepository" :disabled="connecting">Create</button>
      </form>
    </div>
    <table class="table table-bordered mt-2">
      <tr>
        <th>Name</th>
        <th>URL</th>
        <th>Nodes</th>
        <th width="100"></th>
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
        <td class="text-center">
          <button class="btn btn-danger btn-sm" v-on:click="deleteRepository(repo.name)" :disabled="connecting">Delete</button>
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
      connecting: false
    }
  },
  props: ['controllerUrl'],
  watch: {
    controllerUrl: function () {
      fetchReposInfo(this)
    }
  },
  created: function () {
    fetchReposInfo(this)
  },
  methods: {
    refresh: function () {
      fetchReposInfo(this)
    },
    createRepository: function () {
      // TODO validation
      let app = this
      let name = app.$data.name
      Vue.set(app, 'connecting', true)
      axios({
        method: 'POST',
        url: app.controllerUrl + '/api/repos/' + name
      }).then(function (response) {
        Vue.set(app, 'name', '')
        Vue.set(app, 'connecting', false)
        fetchReposInfo(app)
      }).error(function (error) {
        Vue.set(app, 'name', '')
        Vue.set(app, 'connecting', false)
        alert(error)
      })
    },
    deleteRepository: function (name) {
      if (confirm('Delete repository "' + name + '". Are you sure?')) {
        let app = this
        Vue.set(app, 'connecting', true)
        axios({
          method: 'POST',
          url: app.controllerUrl + '/api/repos/' + name + '/_delete'
        }).then(function (response) {
          // TODO wait???
          Vue.set(app, 'connecting', false)
          fetchReposInfo(app)
        }).catch(function (error) {
          Vue.set(app, 'connecting', false)
          alert(error)
        })
      }
    }
  }
}

function fetchReposInfo (app) {
  axios(app.controllerUrl + '/api/repos')
    .then(function (response) {
      Vue.set(app, 'repos', response.data)
    })
    .catch(function (error) {
      Vue.set(app, 'repos', [])
      alert(error)
    })
}
</script>
