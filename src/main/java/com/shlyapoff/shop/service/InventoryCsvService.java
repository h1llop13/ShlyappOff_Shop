package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.*;
import com.shlyapoff.shop.repository.BrandRepository;
import com.shlyapoff.shop.repository.CategoryRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryCsvService {
    private static final List<String> HEADER = List.of(
            "product_id", "name", "description", "price", "stock_quantity", "low_stock_threshold",
            "category_id", "brand_id", "publication_status", "publish_at",
            "variant_id", "variant_value", "variant_stock_quantity");

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFF").append(String.join(",", HEADER)).append("\r\n");
        for (Product product : productRepository.findAllForInventory()) {
            if (product.getVariants() == null || product.getVariants().isEmpty()) {
                appendProductRow(csv, product, null);
            } else {
                for (ProductVariant variant : product.getVariants()) appendProductRow(csv, product, variant);
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file, String actor) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите CSV-файл");
        String text = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<List<String>> rows = parse(text);
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV-файл пуст");
        Map<String, Integer> columns = headerMap(rows.getFirst());
        for (String required : HEADER) {
            if (!columns.containsKey(required)) throw new IllegalArgumentException("В CSV нет столбца: " + required);
        }

        int created = 0;
        int updated = 0;
        int variants = 0;
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().allMatch(String::isBlank)) continue;
            try {
                Long productId = optionalLong(value(row, columns, "product_id"));
                Product product = productId == null ? new Product() : productRepository.findByIdForUpdate(productId)
                        .orElseThrow(() -> new IllegalArgumentException("товар " + productId + " не найден"));
                boolean isNew = product.getId() == null;
                int before = number(product.getStockQuantity(), 0);
                product.setName(required(value(row, columns, "name"), "name"));
                product.setDescription(blankToNull(value(row, columns, "description")));
                BigDecimal price = new BigDecimal(required(value(row, columns, "price"), "price"));
                if (price.signum() < 0) throw new IllegalArgumentException("price не может быть отрицательной");
                product.setPrice(price);
                product.setStockQuantity(nonNegativeInt(value(row, columns, "stock_quantity"), "stock_quantity"));
                product.setLowStockThreshold(nonNegativeInt(value(row, columns, "low_stock_threshold"), "low_stock_threshold"));
                product.setCategory(categoryRepository.findById(requiredLong(value(row, columns, "category_id"), "category_id"))
                        .orElseThrow(() -> new IllegalArgumentException("категория не найдена")));
                product.setBrand(brandRepository.findById(requiredLong(value(row, columns, "brand_id"), "brand_id"))
                        .orElseThrow(() -> new IllegalArgumentException("бренд не найден")));
                product.setPublicationStatus(PublicationStatus.valueOf(required(value(row, columns, "publication_status"), "publication_status").toUpperCase(Locale.ROOT)));
                product.setPublishAt(parseDate(value(row, columns, "publish_at")));
                productService.applyPublicationState(product);
                productRepository.save(product);
                inventoryService.record(product, null, isNew ? InventoryMovementType.INITIAL_STOCK : InventoryMovementType.CSV_IMPORT,
                        isNew ? 0 : before, product.getStockQuantity(), "Импорт CSV, строка " + (rowIndex + 1), actor, "CSV", null);
                if (isNew) created++; else updated++;

                Long variantId = optionalLong(value(row, columns, "variant_id"));
                String variantValue = blankToNull(value(row, columns, "variant_value"));
                if (variantId != null || variantValue != null) {
                    ProductVariant variant = variantId == null ? new ProductVariant() : variantRepository.findByIdForUpdate(variantId)
                            .orElseThrow(() -> new IllegalArgumentException("вариант " + variantId + " не найден"));
                    int variantBefore = number(variant.getStockQuantity(), 0);
                    if (variantId != null && !variant.getProduct().getId().equals(product.getId())) {
                        throw new IllegalArgumentException("вариант принадлежит другому товару");
                    }
                    variant.setProduct(product);
                    variant.setValue(required(variantValue, "variant_value"));
                    int variantAfter = nonNegativeInt(value(row, columns, "variant_stock_quantity"), "variant_stock_quantity");
                    variant.setStockQuantity(variantAfter);
                    variantRepository.save(variant);
                    inventoryService.record(product, variant,
                            variantId == null ? InventoryMovementType.INITIAL_STOCK : InventoryMovementType.CSV_IMPORT,
                            variantId == null ? 0 : variantBefore, variantAfter,
                            "Импорт CSV, строка " + (rowIndex + 1), actor, "CSV", null);
                    variants++;
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Ошибка в строке " + (rowIndex + 1) + ": " + ex.getMessage(), ex);
            }
        }
        return new ImportResult(created, updated, variants);
    }

    private void appendProductRow(StringBuilder csv, Product p, ProductVariant v) {
        List<String> values = List.of(
                string(p.getId()), string(p.getName()), string(p.getDescription()), string(p.getPrice()),
                string(p.getStockQuantity()), string(p.getLowStockThreshold()),
                string(p.getCategory() == null ? null : p.getCategory().getId()),
                string(p.getBrand() == null ? null : p.getBrand().getId()),
                string(p.getPublicationStatus()), string(p.getPublishAt()),
                string(v == null ? null : v.getId()), string(v == null ? null : v.getValue()),
                string(v == null ? null : v.getStockQuantity()));
        csv.append(values.stream().map(this::escape).reduce((a, b) -> a + "," + b).orElse("")).append("\r\n");
    }

    private List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (ch == ',' && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>();
            } else cell.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Незакрытая кавычка в CSV");
        if (!row.isEmpty() || cell.length() > 0) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private Map<String, Integer> headerMap(List<String> header) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < header.size(); i++) result.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        return result;
    }

    private String value(List<String> row, Map<String, Integer> columns, String name) {
        int index = columns.get(name);
        return index < row.size() ? row.get(index).trim() : "";
    }

    private String escape(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
    private String string(Object value) { return value == null ? "" : value.toString(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException("пустое поле " + name); return value.trim(); }
    private Long optionalLong(String value) { return value == null || value.isBlank() ? null : Long.valueOf(value); }
    private Long requiredLong(String value, String name) { return Long.valueOf(required(value, name)); }
    private int nonNegativeInt(String value, String name) { int result = Integer.parseInt(required(value, name)); if (result < 0) throw new IllegalArgumentException(name + " не может быть отрицательным"); return result; }
    private int number(Integer value, int fallback) { return value == null ? fallback : value; }
    private LocalDateTime parseDate(String value) { return value == null || value.isBlank() ? null : LocalDateTime.parse(value); }

    public record ImportResult(int createdProducts, int updatedProducts, int processedVariants) { }
}
