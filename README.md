# gestion-ganadera

Sistema de gestión ganadera: establecimientos, lotes, rodeos, animales, eventos (nacimientos, servicios, tactos, tratamientos, pesajes, movimientos, ventas), stock y referencias de mercado.

> Nombre del producto e identidad visual: pendiente de definir (issue #1).

## Estructura

```
/backend   API REST — Java 21, Spring Boot 3, PostgreSQL, Flyway
/web       Panel web — React, TypeScript, Vite, Tailwind
/mobile    App móvil offline (milestone M5)
/docs      Decisiones técnicas, diagramas y documentación
```

## Requisitos

- Java 21 y Maven 3.9+
- Node.js 20+ y npm
- Docker y Docker Compose

## Levantar el proyecto en local

```bash
# 1. Variables de entorno
cp .env.example .env

# 2. Base de datos
docker compose up -d

# 3. Backend (http://localhost:8080)
cd backend
mvn spring-boot:run

# 4. Web (http://localhost:5173), en otra terminal
cd web
cp .env.example .env
npm install
npm run dev
```

Comprobar que el backend responde:

```bash
curl http://localhost:8080/api/health
```

### pgAdmin (opcional)

```bash
docker compose --profile tools up -d
```

Abrir http://localhost:5050 con las credenciales de `.env`. Para conectar al servidor usá host `postgres`, puerto `5432`.

## Cómo trabajamos

Leé [CONTRIBUTING.md](CONTRIBUTING.md) antes de abrir tu primer PR.
