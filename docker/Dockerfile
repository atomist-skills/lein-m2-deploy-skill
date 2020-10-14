FROM gcr.io/atomist-container-skills/clojure-base:0.0.1

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
