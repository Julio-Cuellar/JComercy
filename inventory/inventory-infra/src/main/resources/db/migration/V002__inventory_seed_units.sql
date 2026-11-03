INSERT INTO inventory.unit_of_measure
    (code, name, symbol, dimension, base_unit_code, conversion_factor, decimals, status)
VALUES
    ('G', 'Gramo', 'g', 'MASS', 'G', 1, 3, 'ACTIVE'),
    ('KG', 'Kilogramo', 'kg', 'MASS', 'G', 1000, 3, 'ACTIVE'),
    ('ML', 'Mililitro', 'ml', 'VOLUME', 'ML', 1, 3, 'ACTIVE'),
    ('L', 'Litro', 'L', 'VOLUME', 'ML', 1000, 3, 'ACTIVE'),
    ('MM', 'Milímetro', 'mm', 'LENGTH', 'MM', 1, 3, 'ACTIVE'),
    ('M', 'Metro', 'm', 'LENGTH', 'MM', 1000, 3, 'ACTIVE'),
    ('M2', 'Metro cuadrado', 'm²', 'AREA', 'M2', 1, 3, 'ACTIVE'),
    ('PZA', 'Pieza', 'pza', 'COUNT', 'PZA', 1, 0, 'ACTIVE');
