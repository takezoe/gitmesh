<template>
  <div id="app">
    <nav class="navbar navbar-expand-lg navbar-light bg-light">
      <a class="navbar-brand" href="#"><img src="./assets/git.png" width="32" height="32"/></a>
      <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="navbarSupportedContent">
        <ul class="navbar-nav mr-auto">
          <li class="nav-item" :class="{active: $route.path == '/repos'}">
            <router-link to="repos" class="nav-link">Repositories</router-link>
          </li>
          <li class="nav-item" :class="{active: $route.path == '/nodes'}">
            <router-link to="nodes" class="nav-link">Nodes</router-link>
          </li>
        </ul>
        <form class="form-inline">
          <input id="controllerUrl" class="form-control mr-sm-2" type="text" placeholder="Controller URL" size="40" v-model="editingUrl" :disabled="!editing" autocomplete="off">
          <button class="btn btn-outline-success" type="submit" v-on:click="edit" v-if="!editing">Edit</button>
          <button class="btn btn-outline-success" type="submit" v-on:click="connect" v-if="editing">Connect</button>
        </form>
      </div>
    </nav>
    <div class="container">
      <router-view :controllerUrl="controllerUrl"/>
    </div>
  </div>
</template>

<script>
import Vue from 'vue'

export default {
  name: 'App',
  data () {
    let controllerUrl = localStorage.getItem('gitmesh.controllerUrl')
    if (controllerUrl === '' || controllerUrl === null) {
      controllerUrl = 'http://localhost:8081'
    }
    return {
      controllerUrl: controllerUrl,
      editingUrl: controllerUrl,
      editing: false
    }
  },
  methods: {
    edit: function () {
      Vue.set(this, 'editing', true)
      // document.getElementById('controllerUrl').focus()
    },
    connect: function () {
      Vue.set(this, 'controllerUrl', this.$data.editingUrl)
      Vue.set(this, 'editing', false)
      localStorage.setItem('gitmesh.controllerUrl', this.$data.editingUrl)
    }
  }
}
</script>
