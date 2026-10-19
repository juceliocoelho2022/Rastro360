CREATE TABLE product (
    id UUID PRIMARY KEY,
    sku VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    unit VARCHAR(20) NOT NULL
);

CREATE TABLE location (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('PRODUCAO', 'ESTOQUE', 'EXPEDICAO'))
);

CREATE TABLE shipment (
    id UUID PRIMARY KEY,
    reference VARCHAR(32) NOT NULL UNIQUE,
    truck_plate VARCHAR(20) NOT NULL,
    driver_name VARCHAR(150) NOT NULL,
    customer_name VARCHAR(180) NOT NULL,
    customer_address VARCHAR(350) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PREPARACAO', 'EM_TRANSITO', 'NO_CLIENTE', 'ENTREGUE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    departed_at TIMESTAMP WITH TIME ZONE,
    arrived_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    received_by VARCHAR(150)
);

CREATE TABLE tracking_unit (
    id UUID PRIMARY KEY,
    tracking_code VARCHAR(42) NOT NULL UNIQUE,
    product_id UUID NOT NULL REFERENCES product(id),
    lot VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PRODUZIDO', 'ARMAZENADO', 'CARREGADO', 'EM_TRANSITO', 'NO_CLIENTE', 'ENTREGUE')),
    location_id UUID REFERENCES location(id),
    shipment_id UUID REFERENCES shipment(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_unit_storage_or_shipment CHECK (
        (location_id IS NOT NULL AND shipment_id IS NULL)
        OR (location_id IS NULL AND shipment_id IS NOT NULL)
    )
);
CREATE INDEX idx_tracking_unit_shipment ON tracking_unit(shipment_id);
CREATE INDEX idx_tracking_unit_product ON tracking_unit(product_id);

CREATE TABLE tracking_event (
    id UUID PRIMARY KEY,
    unit_id UUID NOT NULL REFERENCES tracking_unit(id),
    type VARCHAR(30) NOT NULL,
    from_location_id UUID REFERENCES location(id),
    to_location_id UUID REFERENCES location(id),
    shipment_id UUID REFERENCES shipment(id),
    actor VARCHAR(120) NOT NULL,
    details VARCHAR(400),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_tracking_event_unit_time ON tracking_event(unit_id, occurred_at);

INSERT INTO location (id, code, name, kind) VALUES
 ('10000000-0000-0000-0000-000000000001', 'PROD-01', 'Linha de produção 01', 'PRODUCAO'),
 ('10000000-0000-0000-0000-000000000002', 'EST-01', 'Estoque principal', 'ESTOQUE'),
 ('10000000-0000-0000-0000-000000000003', 'EXP-01', 'Doca de expedição', 'EXPEDICAO');
