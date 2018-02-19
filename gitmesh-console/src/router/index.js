import Vue from 'vue'
import Router from 'vue-router'
import Nodes from '@/components/Nodes'
import Repositories from '@/components/Repositories'

Vue.use(Router)

export default new Router({
  routes: [
    {
      path: '/',
      name: 'Index',
      redirect: '/nodes'
    },
    {
      path: '/nodes',
      name: 'Nodes',
      component: Nodes
    },
    {
      path: '/repos',
      name: 'Repositories',
      component: Repositories
    }
  ]
})
