FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:16573fd54bd6cd7d6a5776e95fb81f35e39e1d51cdd35198d592a91bc6425701

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
