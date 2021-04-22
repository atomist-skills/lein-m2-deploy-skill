FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:6f3f8645daa804a664d332c57c4e22427d8070c9bb56027a32bcf0d59976db00

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
