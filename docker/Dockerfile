FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:168ce85a3573a310cee64d4b19f4bf78fe841c80cf0e44bfb62632ac97b9175a

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
