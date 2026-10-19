# E-Wallet

Simulasi dompet digital (e-wallet) berbasis REST API dengan Spring Boot. Proyek portofolio yang berfokus pada konsistensi data keuangan: transfer atomik, pencatatan transaksi gaya double-entry, dan penanganan akses bersamaan (concurrency).

> **Status:** backend selesai dan teruji. Frontend web menyusul (folder `frontend/`). Daftar fitur ada di bagian [Rencana Fitur](#rencana-fitur).

## Teknologi

- Java 21
- Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security, Actuator)
- PostgreSQL 16
- Flyway (migrasi database)
- Maven (Maven Wrapper)
- Docker Compose (database untuk development)

## Rencana Fitur

- [x] Registrasi (`POST /api/auth/register`, password di-hash dengan BCrypt, wallet dibuat otomatis)
- [x] Login (`POST /api/auth/login`, token JWT HS256) dan profil (`GET /api/users/me`)
- [x] Cek saldo (`GET /api/wallets/me`)
- [x] Top up (`POST /api/wallets/top-up`, wallet dikunci selama proses supaya aman dari top up bersamaan)
- [x] Transfer antar pengguna (`POST /api/transfers`, atomik, dua wallet dikunci urut berdasarkan id supaya tidak deadlock)
- [x] Riwayat transaksi dengan paginasi (`GET /api/transactions?page=0&size=20`, terbaru lebih dulu)
- [x] Idempotency key untuk mencegah transfer ganda (header `Idempotency-Key` di `POST /api/transfers`, request yang diulang mengembalikan hasil asli)
- [x] Dokumentasi API (Swagger UI di `/swagger-ui.html`, spesifikasi OpenAPI di `/v3/api-docs`)
- [x] Test konkurensi dan integration test (top up paralel, transfer silang tanpa deadlock, idempotency paralel)
- [x] CORS (dari `CORS_ALLOWED_ORIGINS`) dan error handling seragam (format ProblemDetail, error 500 tidak membocorkan detail)
- [x] Refresh token di PostgreSQL (`POST /api/auth/refresh` dengan rotasi, `POST /api/auth/logout` yang mencabut sesi; token yang dipakai ulang mencabut seluruh rantainya)
- [ ] Deploy beserta frontend

## Desain Database

Tiga tabel utama (ditambah `idempotency_records` untuk idempotency key dan `refresh_tokens` untuk sesi login):

| Tabel | Fungsi |
|---|---|
| `users` | Data autentikasi dan profil dasar |
| `wallets` | Satu dompet per pengguna: saldo, status aktif, versi (optimistic locking) |
| `transactions` | Log keuangan. Satu transfer menghasilkan dua baris (`DEBIT` untuk pengirim, `CREDIT` untuk penerima) dengan `reference_number` yang sama |

Beberapa keputusan desain:

- Uang disimpan sebagai `NUMERIC(19,2)` dan dihitung dengan `BigDecimal`, bukan `double`.
- Database ikut menjaga integritas lewat constraint: `balance >= 0`, `amount > 0`, `UNIQUE(reference_number, wallet_id)`.
- `transactions.balance_after` menyimpan saldo setelah mutasi untuk keperluan audit.
- Skema dikelola Flyway (`src/main/resources/db/migration`); Hibernate hanya memvalidasi (`ddl-auto=validate`).

## Struktur Proyek

Monorepo:

```
e-wallet/
├── backend/                Spring Boot API (Maven, Dockerfile, .env)
├── frontend/               Aplikasi web (menyusul)
├── docker-compose.yml      PostgreSQL untuk development
└── docker-compose.prod.yml Aplikasi + PostgreSQL untuk server
```

Isi `backend/`:

```
src/main/java/com/ewallet/
├── EwalletApplication.java
├── entity/          Entity JPA: User, Wallet, Transaction, enum
├── auth/            controller, service, dto, repository (register, login, JWT, refresh token)
├── common/          config (security) dan exception (error handler)
├── user/            controller, service, dto, repository
├── wallet/          controller, service, dto, repository (cek saldo)
├── transaction/     controller, dto, repository, service (riwayat, nomor referensi)
└── transfer/        controller, service, dto, repository (top up, transfer, idempotency)
src/main/resources/
├── application.properties
└── db/migration/    skrip Flyway
```

Pengelompokan per fitur; controller, service, dan dto ditambahkan seiring fitur dibuat.

## Menjalankan Secara Lokal

Prasyarat: JDK 21 dan Docker (atau PostgreSQL yang sudah terpasang).

1. Salin konfigurasi environment:
   ```bash
   cd backend
   cp .env.example .env
   ```
   Di PowerShell: `Copy-Item .env.example .env`. Sesuaikan nilainya jika perlu.
2. Jalankan database (dari root repo):
   ```bash
   docker compose up -d
   ```
3. Jalankan aplikasi (dari folder `backend/`):
   ```bash
   ./mvnw spring-boot:run
   ```
   Di Windows: `mvnw.cmd spring-boot:run`.

Saat start, Flyway otomatis membuat tabel (lihat log: `Successfully applied N migrations`). Dokumentasi API ada di `http://localhost:8080/swagger-ui.html`. Endpoint register, login, dan `/actuator/health` terbuka; endpoint lain wajib memakai header `Authorization: Bearer <token>`.

> Jika memakai Docker Compose, nilai `DB_USERNAME` dan `DB_PASSWORD` di `.env` harus sama dengan yang dipakai `docker-compose.yml` (bawaan: `ewallet`/`ewallet`).

## Menjalankan Test

Test memakai database terpisah bernama `ewallet_test` (supaya data development aman). Buat sekali:

```bash
psql -U <user> -c "CREATE DATABASE ewallet_test;"
```

Lalu jalankan dari folder `backend/`:

```bash
./mvnw test
```

User dan password database diambil dari `.env`; URL test bisa diubah lewat `TEST_DB_URL`.

## Deploy

Proyek menyertakan `Dockerfile` (multi-stage: build dengan JDK, jalan dengan JRE sebagai user non-root). Platform hosting yang mendukung Docker bisa membangunnya langsung dari repository.

Uji secara lokal:

```bash
docker build -t ewallet ./backend
docker run --env-file backend/.env -p 127.0.0.1:8080:8080 ewallet
```

`127.0.0.1:` di depan port membuat container hanya bisa diakses dari komputer itu sendiri, tidak dari jaringan luar.

### Deploy di VPS

`docker-compose.prod.yml` menjalankan aplikasi dan PostgreSQL bersama. Database tidak membuka port sama sekali (hanya dapat dijangkau aplikasi lewat jaringan internal Docker), dan aplikasi hanya terbuka di `127.0.0.1:8080`. Akses dari internet lewat reverse proxy (Nginx atau Caddy) dengan HTTPS.

1. Salin proyek ke server, buat `backend/.env` dari `backend/.env.example`. Isi `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, dan `CORS_ALLOWED_ORIGINS` dengan nilai produksi. `DB_URL` diatur otomatis oleh compose.
2. Jalankan:
   ```bash
   docker compose --env-file backend/.env -f docker-compose.prod.yml up -d --build
   ```
3. Arahkan reverse proxy ke `http://127.0.0.1:8080`.

Catatan: `DB_USERNAME` dan `DB_PASSWORD` dipakai PostgreSQL saat volume pertama kali dibuat. Mengubahnya setelah itu tidak mengubah user di database yang sudah ada.

Catatan:

- Dari dalam container, `localhost` bukan komputer host. Untuk menguji dengan PostgreSQL di host, isi `DB_URL` dengan `host.docker.internal` sebagai pengganti `localhost`.
- Port dibaca dari `PORT` (diisi otomatis oleh banyak platform), lalu `SERVER_PORT`, lalu 8080.
- Untuk database terkelola (bukan compose di atas), biasanya membutuhkan SSL: tambahkan `?sslmode=require` di akhir `DB_URL`.
- Set `JWT_SECRET` dan `CORS_ALLOWED_ORIGINS` (domain frontend) di environment platform. Jangan memasukkan `.env` ke image; `.dockerignore` sudah mengecualikannya.
- Health check: `GET /actuator/health`.

## Variabel Environment

| Variabel | Fungsi | Contoh |
|---|---|---|
| `DB_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/ewallet` |
| `DB_USERNAME` | User database | `ewallet` |
| `DB_PASSWORD` | Password database | |
| `SERVER_PORT` | Port aplikasi (`PORT` dari platform diutamakan) | `8080` |
| `JWT_SECRET` | Kunci penandatangan token, wajib diisi, minimal 32 karakter | string acak panjang |
| `JWT_EXPIRATION_MINUTES` | Masa berlaku access token dalam menit | `60` |
| `JWT_REFRESH_EXPIRATION_DAYS` | Masa berlaku refresh token dalam hari | `7` |
| `CORS_ALLOWED_ORIGINS` | Alamat frontend yang diizinkan, dipisah koma | `http://localhost:5173` |

File `.env` tidak boleh di-commit. Untuk deployment, set variabel yang sama langsung di environment platform hosting.
