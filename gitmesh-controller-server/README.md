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
      "primaryNode": "http://localhost:8082".
      "nodes": [
        {
          "url": "http://localhost:8082",
          "status": "READY"
        },
        {
          "url": "http://localhost:8083",
          "status": "PREPARING"
        }
      ]
    },
    {
      "name": "repo2",
      "primaryNode": "http://localhost:8082".
      "nodes": [
        {
          "url": "http://localhost:8082",
          "status": "READY"
        },
        {
          "url": "http://localhost:8083",
          "status": "READY"
        }
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

### Delete a repository (by POST method)

- Endpoint: `POST /api/repos/REPOSITORY_NAME/_delete`
- Request: None
- Response: None

### List nodes

- Endpoint: `GET /api/nodes`
- Request: None
- Response:
  ```javascript
  [
    {
      "url": "http://localhost:8082",
      "diskUsage": 0.5,
      "repos": [
        {
          "name": "repo1",
          "status": "READY"
        },{
          "name": "repo2",
          "status": "PREPARING"
        }
      ]
    },
    {
      "node": "http://localhost:8083",
      "diskUsage": 0.5,
      "repos": [
        {
          "name": "repo1",
          "status": "READY"
        },{
          "name": "repo2",
          "status": "READY"
        }
      ]
    }
  ]
  ```

### Join node

- Endpoint: `GET /api/nodes/notify`
- Request:
  ```javascript
  {
    "url": "http://localhost:8082",
    "diskUsage": 0.5,
    "repos": [
      {
        "name": "repo1",
        "timestamp": 1525232262745
      },{
        "name: ""repo2",
        "timestamp": 1525232262745
       }
    ]
  }
  ```
- Response: None
