Docker support for gitmesh
========

Build docker images:

```
$ ./build-docker.sh
```

This builds following docker images:

- gitmesh-console
- gitmesh-controller-server
- gitmesh-repository-server

Run the minumal cluster (console x 1, controller x 1, mysql x 1, repository x 2) using docker-compose:

```
$ ./run-docker-compose.sh
```

Then gitmesh console is available at `http://localhost:8080`, endpoints are available at `http://localhost:8081`.
