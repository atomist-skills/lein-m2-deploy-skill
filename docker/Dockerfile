FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:7b2a6889735237352b6575b0c8f1d50d43e23207edaa71eaa6381962d7e31d8b

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
