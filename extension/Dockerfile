FROM node:22-alpine AS build
WORKDIR /extension
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run verify
FROM scratch AS artifact
COPY --from=build /extension/dist/ /
