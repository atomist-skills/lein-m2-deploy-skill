FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:c812b156b7f4bd0c37ae419e02f44c40eda402f2b8c71cc7117934e9902836e0

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
