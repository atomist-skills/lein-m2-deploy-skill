FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:316c1611f5182161dd4e6545fd56eb59c1bd75ffee2d72c8bfe13daaed68c4e1

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
