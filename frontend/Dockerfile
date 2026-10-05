# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
FROM node:24.19.0-alpine AS build
WORKDIR /work
COPY package*.json ./
RUN --mount=type=cache,id=zhua-consigndesk-npm,target=/root/.npm,sharing=locked npm ci --no-audit --no-fund --fetch-timeout=30000 --fetch-retries=2 --fetch-retry-mintimeout=1000 --fetch-retry-maxtimeout=5000
COPY . .
RUN npm run format:check && npm run lint && npm test && npm run build
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /work/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
CMD ["nginx","-g","daemon off;"]
