FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:8fab20562af88acab4f183b9713093fca85011b2e62677ce8e5ef6390da50ee7

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
