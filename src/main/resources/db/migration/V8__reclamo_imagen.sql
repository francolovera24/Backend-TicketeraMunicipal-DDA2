-- Foto opcional del problema. El binario va aparte para no cargarlo en cada listado.

ALTER TABLE reclamo ADD COLUMN tiene_imagen boolean NOT NULL DEFAULT false;

CREATE TABLE reclamo_imagen (
    reclamo_id uuid PRIMARY KEY REFERENCES reclamo (id),
    media_type varchar(40) NOT NULL,
    contenido   bytea      NOT NULL
);
