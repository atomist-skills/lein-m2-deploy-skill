FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:550818f291a60abecbbced89dfec6466511314aafbcea67ed0c7663ed0717c7c

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
