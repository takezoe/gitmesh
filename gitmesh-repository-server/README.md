gitmesh-repository-server
========

## Git repository

Git repositories are available at `/git/REPOSITORY_NAME.git`

## API endpoints

### Status

- Endpoint: `GET /`
- Request: None
- Response:
  ```javascript
  {
    "endpoint": "http://localhost:8081",
    "diskUsage": 0.5,
    "repos": [
      "repo1",
      "repo2"
    ]
  }
  ```

### List repositories

- Endpoint: `GET /api/repos`
- Request: None
- Response:
  ```javascript
  [
    {
      "name": "repo1",
      "empty": false
    },
    {
      "name": "repo2",
      "empty": true
    }
  ]
  ```

### Show repository status

- Endpoint: `GET /api/repos/REPOSITORY_NAME`
- Request: None
- Response:
  ```javascript
  {
    "name": "repo1",
    "empty": false
  }
  ```

### Create a repository

- Endpoint: `POST /api/repos/REPOSITORY_NAME`
- Request: None
- Response: None

### Delete a repository

- Endpoint: `DELETE /api/repos/REPOSITORY_NAME`
- Request: None
- Response: None

### Clone an another repository

- Endpoint: `PUT /api/repos/REPOSITORY_NAME`
- Request:
  ```javascript
  {
    "endpoint": "http://localhost:8081"
  }
  ```
- Response: None
