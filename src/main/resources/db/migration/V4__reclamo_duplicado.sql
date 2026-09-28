-- Deteccion de duplicados: un reclamo DUPLICADO apunta al que ya reportaba el problema.

ALTER TABLE reclamo ADD COLUMN reclamo_original_id uuid REFERENCES reclamo (id);

CREATE INDEX ix_reclamo_original ON reclamo (reclamo_original_id);
CREATE INDEX ix_reclamo_tipo_fecha ON reclamo (tipo, fecha_creacion);
