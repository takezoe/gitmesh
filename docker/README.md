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

Then gitmesh is endpoints are available at `http://localhost:8081`.
