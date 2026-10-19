# E-Wallet

Simulasi dompet digital (e-wallet) berbasis REST API dengan Spring Boot. Proyek portofolio yang berfokus pada konsistensi data keuangan: transfer atomik, pencatatan transaksi gaya double-entry, dan penanganan akses bersamaan (concurrency).

> **Status:** dalam pengembangan. Saat ini baru fondasi (skema database dan entity). Fitur lain ada di bagian [Rencana Fitur](#rencana-fitur).

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
- [ ] Refresh token di PostgreSQL (rotasi dan logout yang mencabut sesi)
- [ ] Deploy beserta frontend

## Desain Database

Tiga tabel utama (ditambah `idempotency_records` untuk idempotency key):

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

```
src/main/java/com/ewallet/
├── EwalletApplication.java
├── entity/          Entity JPA: User, Wallet, Transaction, enum
├── auth/            controller, service, dto (register, login, JWT)
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

Prasyarat: JDK 21 dan Docker.

1. Salin konfigurasi environment:
   ```bash
   cp .env.example .env
   ```
   Di PowerShell: `Copy-Item .env.example .env`. Sesuaikan nilainya jika perlu.
2. Jalankan database:
   ```bash
   docker compose up -d
   ```
3. Jalankan aplikasi:
   ```bash
   ./mvnw spring-boot:run
   ```
   Di Windows: `mvnw.cmd spring-boot:run`.

Saat start, Flyway otomatis membuat tabel (lihat log: `Successfully applied 1 migration`). Endpoint register, login, dan `/actuator/health` terbuka; endpoint lain wajib memakai header `Authorization: Bearer <token>`.

> Jika memakai Docker Compose, nilai `DB_USERNAME` dan `DB_PASSWORD` di `.env` harus sama dengan yang dipakai `docker-compose.yml` (bawaan: `ewallet`/`ewallet`).

## Menjalankan Test

Test memakai database terpisah bernama `ewallet_test` (supaya data development aman). Buat sekali:

```bash
psql -U <user> -c "CREATE DATABASE ewallet_test;"
```

Lalu jalankan:

```bash
./mvnw test
```

User dan password database diambil dari `.env`; URL test bisa diubah lewat `TEST_DB_URL`.

## Variabel Environment

| Variabel | Fungsi | Contoh |
|---|---|---|
| `DB_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/ewallet` |
| `DB_USERNAME` | User database | `ewallet` |
| `DB_PASSWORD` | Password database | |
| `SERVER_PORT` | Port aplikasi | `8080` |
| `JWT_SECRET` | Kunci penandatangan token, wajib diisi, minimal 32 karakter | string acak panjang |
| `JWT_EXPIRATION_MINUTES` | Masa berlaku token dalam menit | `60` |
| `CORS_ALLOWED_ORIGINS` | Alamat frontend yang diizinkan, dipisah koma | `http://localhost:5173` |

File `.env` tidak boleh di-commit. Untuk deployment, set variabel yang sama langsung di environment platform hosting.
