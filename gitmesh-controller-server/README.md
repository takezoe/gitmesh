gitmesh-controller-server
========

## Git repository

Git repositories are available at `/git/REPOSITORY_NAME.git`

## API endpoints

### List repositories

- Endpoint: `GET /api/repos`
- Request: None
- Response:
  ```javascript
  [
    {
      "name": "repo1",
      "primaryNode": "http://localhost:8081".
      "nodes": [
        "http://localhost:8081",
        "http://localhost:8082"
      ]
    },
    {
      "name": "repo2",
      "primaryNode": "http://localhost:8081".
      "nodes": [
        "http://localhost:8081",
        "http://localhost:8082"
      ]
    }    
  ]
  ```

### Create a repository

- Endpoint: `POST /api/repos/REPOSITORY_NAME`
- Request: None
- Response: None

### Delete a repository

- Endpoint: `DELETE /api/repos/REPOSITORY_NAME`
- Request: None
- Response: None

### List nodes

- Endpoint: `GET /api/nodes`
- Request: None
- Response:
  ```javascript
  [
    {
      "node": "http://localhost:8081",
      "diskUsage": 0.5,
      "repos": [
        "repo1",
        "repo2"
      ]
    },
    {
      "node": "http://localhost:8082",
      "diskUsage": 0.5,
      "repos": [
        "repo1",
        "repo2"
      ]
    }
  ]
  ```

### Join node

- Endpoint: `GET /api/nodes/join`
- Request:
  ```javascript
  {
    "node": "http://localhost:8081",
    "diskUsage": 0.5,
    "repos": [
      "repo1",
      "repo2"
    ]
  }
  ```
- Response: None
