FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:861a3a8bc36a5fb12e09419d8ea8c3ad292d07e2d04d46a0764cd1af24c1b4c9

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
