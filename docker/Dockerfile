FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:d353203b9309d0ce9a3b1f16c67ff663f3aee93a252a7b27924c764062448ff0

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
