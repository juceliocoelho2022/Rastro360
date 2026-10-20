CREATE TABLE product_image (
    product_id UUID PRIMARY KEY REFERENCES product(id),
    image_data BYTEA NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
