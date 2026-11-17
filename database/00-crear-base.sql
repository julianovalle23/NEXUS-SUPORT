-- ===========================================================
-- NEXUS Support - Creación del usuario y la base de datos
-- Ejecutar UNA vez con un superusuario de PostgreSQL, pasando la clave del usuario nexus:
--   psql -U postgres -v clave=LA_CLAVE_DEL_ENV -f database/00-crear-base.sql
-- Luego cargar database/init/01..04 con el usuario nexus (ver README).
-- ===========================================================

CREATE ROLE nexus LOGIN PASSWORD :'clave';
CREATE DATABASE nexus_support OWNER nexus ENCODING 'UTF8' TEMPLATE template0;
