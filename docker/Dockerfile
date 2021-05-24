FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:eb899ffde28916d32c5f01a81fdcc1e0beb491b439cc7439939c1a341e4c89e6

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
