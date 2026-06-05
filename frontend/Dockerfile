# 前端构建镜像：Vite 产物由 Nginx 提供静态资源和 API 反向代理。

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY index.html tsconfig.json tsconfig.node.json vite.config.ts ./
COPY src ./src
RUN npm run build

FROM nginx:1.27-alpine

LABEL org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.ref.name="agent-frontend"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
