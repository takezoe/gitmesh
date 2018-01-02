dgit-repository-server
========

## Git repository

Git repositories are available at `/git/REPOSITORY_NAME.git`

## API endpoints

- List repositories: `GET /api/repos`
- Get a repository information: `GET /api/repos/REPOSITORY_NAME`
- Create a repository: `POST /api/repos/REPOSITORY_NAME`
- Delete a repository: `DELETE /api/repos/REPOSITORY_NAME`
- Clone an another repository