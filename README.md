distributed-git-server
========

Experimental project to make a distributed Git server

The main goal of this project is to find a reasonable way to add scalability and durability to git repositories. Basic idea is locating git repositories on multiple nodes, and proxy requests from git clients to appropriate nodes. This approach is similar to [GitHub's DGit](https://githubengineering.com/introducing-dgit/).

distributed-git-server cluster consists of following two components:

- Controller server: This is a front server of distributed-git-server cluster. Manage repository servers and receive requests from git clients and proxy these requests to appropriate repository servers. We can make redundant it by setup multiple instances with a load balancer. 
- Repository server: This is a storage server of distributed-git-server cluster. Git repositories are located on this kind of servers actually. We can add any number of repository server instances to distributed-git-server cluster.

distributed-git-server is still under development. I will add more the detailed architecture documentation, and the instruction how to setup.