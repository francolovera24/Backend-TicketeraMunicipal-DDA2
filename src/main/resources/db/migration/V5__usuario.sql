-- Cuentas para autenticacion (JWT). Separadas de ciudadano a proposito.

CREATE TABLE usuario (
    id            uuid PRIMARY KEY,
    email         varchar(150) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    rol           varchar(20)  NOT NULL,
    fecha_alta    timestamp(6) with time zone NOT NULL
);
