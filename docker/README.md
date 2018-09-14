gitmesh docker support
========

Build docker images:

```
$ ./build-docker.sh
```

Run the minumal cluster (controller x 1, mysql x 1, repository x 2) using docker-compose:

```
$ ./run-docker-compose.sh
```

Then gitmesh console is available at `http://localhost:8080`, endpoints are available at `http://localhost:8081`.
