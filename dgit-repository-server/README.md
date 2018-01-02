dgit-repository-server
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
    "repo1",
    "repo2",
    ...
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

### Clone an another repository

- Endpoint: `PUT /api/repos/REPOSITORY_NAME`
- Request:
  ```javascript
  {
    "source": "http://host:port/git/repository.git"
  }
  ```
- Response: None
