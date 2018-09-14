FROM node:8.12.0-alpine

MAINTAINER Naoki Takezoe <takezoe [at] gmail.com>

RUN npm install -g http-server

WORKDIR /app

COPY package*.json ./

RUN npm install

COPY . .

RUN npm run build

EXPOSE 8080

CMD [ "http-server", "dist" ]
