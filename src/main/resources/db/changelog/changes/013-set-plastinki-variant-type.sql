UPDATE categories
SET variant_type = 'FLAVOR'
WHERE LOWER(TRIM(name)) = 'пластинки';
