-- Esquema inicial de la Ticketera Municipal.

CREATE TABLE barrio (
    id                 uuid PRIMARY KEY,
    nombre             varchar(100) NOT NULL,
    nombre_normalizado varchar(100) NOT NULL UNIQUE
);

CREATE TABLE ciudadano (
    id       uuid PRIMARY KEY,
    nombre   varchar(150) NOT NULL,
    contacto varchar(150) NOT NULL UNIQUE
);

CREATE TABLE cuadrilla (
    id           uuid PRIMARY KEY,
    nombre       varchar(100) NOT NULL,
    especialidad varchar(30)  NOT NULL,
    disponible   boolean      NOT NULL DEFAULT true,
    version      bigint       NOT NULL DEFAULT 0
);

CREATE INDEX ix_cuadrilla_especialidad ON cuadrilla (especialidad, disponible);

CREATE TABLE reclamo (
    id                  uuid PRIMARY KEY,
    descripcion         varchar(1000) NOT NULL,
    tipo                varchar(30)   NOT NULL,
    direccion           varchar(255)  NOT NULL,
    lat                 double precision,
    lon                 double precision,
    barrio_id           uuid          NOT NULL REFERENCES barrio (id),
    ciudadano_id        uuid          NOT NULL REFERENCES ciudadano (id),
    cuadrilla_id        uuid          REFERENCES cuadrilla (id),
    estado              varchar(20)   NOT NULL,
    fecha_creacion      timestamp(6) with time zone NOT NULL,
    fecha_actualizacion timestamp(6) with time zone NOT NULL,
    score_criticidad    integer       NOT NULL DEFAULT 0,
    urgente             boolean       NOT NULL DEFAULT false,
    version             bigint        NOT NULL DEFAULT 0
);

CREATE INDEX ix_reclamo_barrio_estado ON reclamo (barrio_id, estado);
CREATE INDEX ix_reclamo_ciudadano ON reclamo (ciudadano_id);
CREATE INDEX ix_reclamo_tipo_estado ON reclamo (tipo, estado);
