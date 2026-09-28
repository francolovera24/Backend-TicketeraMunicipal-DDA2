-- Idempotencia de consumidores: un evento se procesa una sola vez por consumidor.
-- La clave incluye el consumidor porque el mismo evento llega a varias colas
-- (por ejemplo reclamo.creado a cuadrillas.eventos y a ia.eventos).

CREATE TABLE evento_procesado (
    event_id     uuid        NOT NULL,
    consumidor   varchar(100) NOT NULL,
    procesado_en timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (event_id, consumidor)
);
