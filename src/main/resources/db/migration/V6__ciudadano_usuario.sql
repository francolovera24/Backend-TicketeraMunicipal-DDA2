-- Vinculo opcional entre un ciudadano y la cuenta del vecino que lo dio de alta.
-- Un usuario tiene a lo sumo un ciudadano; los ciudadanos sin cuenta quedan en null.

ALTER TABLE ciudadano ADD COLUMN usuario_id uuid UNIQUE REFERENCES usuario (id);
