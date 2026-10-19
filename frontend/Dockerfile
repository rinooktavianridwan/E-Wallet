# Tahap 1: build aplikasi statis (dist/)
FROM node:22-alpine AS build
WORKDIR /app

RUN npm install -g pnpm@10

# Salin file dependensi dulu supaya lapisan install bisa di-cache selama dependensi tidak berubah
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# Tahap 2: nginx tanpa hak root yang menyajikan dist/ dan meneruskan /api ke backend
FROM nginxinc/nginx-unprivileged:stable-alpine

# Alamat backend untuk proxy /api. Template di bawah diisi saat container start.
ENV BACKEND_URL=http://app:8080

COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
