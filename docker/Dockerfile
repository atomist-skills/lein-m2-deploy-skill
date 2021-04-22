FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:d2f307cd28edcf3d90d1c9a0b6c3b2685a7f0a48cb5fbab05e50f878cf1bd61b

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
