FROM gcr.io/atomist-container-skills/clojure-base:openjdk11@sha256:bc3d3822b9e05b62d260bb0b1daf6a950b04fb9f14e89e63ee73df3e5ead2c03

WORKDIR /usr/src/app

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]
