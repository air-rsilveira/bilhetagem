CREATE TABLE IF NOT EXISTS cobranca (
    id BIGSERIAL PRIMARY KEY,
    id_usuario VARCHAR(255) NOT NULL,
    nome_solicitante VARCHAR(255) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    metodo VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    valor_solicitacao NUMERIC(19,2),
    valor_pago NUMERIC(19,2),
    txid VARCHAR(255),
    copia_e_cola TEXT,
    transaction_id VARCHAR(255),
    acs_url VARCHAR(500),
    three_ds_payload TEXT,
    data_criacao TIMESTAMP NOT NULL,
    data_expiracao TIMESTAMP,
    data_finalizada TIMESTAMP,
    id_cobranca_origem BIGINT REFERENCES cobranca(id)
);

CREATE INDEX IF NOT EXISTS idx_cobranca_txid ON cobranca(txid);
CREATE INDEX IF NOT EXISTS idx_cobranca_transaction_id ON cobranca(transaction_id);
CREATE INDEX IF NOT EXISTS idx_cobranca_id_usuario ON cobranca(id_usuario);
CREATE INDEX IF NOT EXISTS idx_cobranca_id_origem ON cobranca(id_cobranca_origem);
