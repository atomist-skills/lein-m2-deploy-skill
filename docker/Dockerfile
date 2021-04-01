FROM gcr.io/atomist-container-skills/clojure-base:0.0.1@sha256:cd6e9de3de5b121dc7aef0cb3f6a1b58423049ce6db08fd7161cac7b39722f55

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
