FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:6ae78a66320dbac0ac149213b0bb681e7ed351431104bd7f8eb8882501ca6e25

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
