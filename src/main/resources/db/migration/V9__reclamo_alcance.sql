-- Los historicos conservan null: su alcance no fue informado.
-- Permite incorporar bases locales que ya contengan el atributo.
ALTER TABLE reclamo ADD COLUMN IF NOT EXISTS alcance varchar(10);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'reclamo'::regclass AND conname = 'ck_reclamo_alcance'
    ) THEN
        ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_alcance
            CHECK (alcance IN ('PERSONAL', 'COLECTIVO'));
    END IF;
END $$;
