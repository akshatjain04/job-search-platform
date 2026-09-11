# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /build/web
COPY web/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build
WORKDIR /build/extension
COPY extension/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY extension/ ./
RUN npm run build && apk add --no-cache zip && cd dist && zip -qr /build/web/dist/myjobai-extension.zip .

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY --from=build /build/web/dist/ /usr/share/nginx/html/
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
COPY infra/nginx/locations.conf /etc/nginx/myjobai-locations.conf
COPY infra/nginx/http.conf /etc/nginx/conf.d/default.conf
USER 101:101
EXPOSE 8080 8443
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
