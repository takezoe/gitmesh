distributed-git-server
========

An experimental project to make a distributed git server cluster

The main goal of this project is to find a reasonable way to add scalability and redundancy to git repositories. Basic idea is locating git repositories on multiple nodes, and proxy requests from git clients to appropriate nodes. This approach is similar to [GitHub's DGit](https://githubengineering.com/introducing-dgit/).

The distributed gitserver cluster consists of following two kinds of servers:

- [Controller server](https://github.com/takezoe/distributed-git-server/tree/master/dgit-controller-server)

  This is a front server of the cluster. It manages repository servers and proxy requests from git clients to appropriate repository servers. We can make redundant it by setup multiple instances with a load balancer. 

- [Repository server](https://github.com/takezoe/distributed-git-server/tree/master/dgit-repository-server)

  This is a storage server of the cluster. Git repositories are located on this kind of servers actually. We can add any number of repository server instances to the cluster.

![Architecture](architecture.png)

This project is still under development phase, but if you are interested, please try it. Any feedback is welcome!
