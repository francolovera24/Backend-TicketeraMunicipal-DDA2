-- Titulo/asunto corto del reclamo, distinto de la descripcion larga.
-- Los reclamos existentes quedan con titulo vacio; de ahi en mas el alta lo exige.

ALTER TABLE reclamo ADD COLUMN titulo varchar(150) NOT NULL DEFAULT '';
ALTER TABLE reclamo ALTER COLUMN titulo DROP DEFAULT;
