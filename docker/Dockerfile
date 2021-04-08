FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:e826f3d213fa3e07c521985cfe5d8a0f36a32db120ddb5ff593685eb2817e401

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
