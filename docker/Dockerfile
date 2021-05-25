FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:6f73844f3aaa8abe1e27069058eb152c092fe59c94890027fbe5c8f469753daf

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
