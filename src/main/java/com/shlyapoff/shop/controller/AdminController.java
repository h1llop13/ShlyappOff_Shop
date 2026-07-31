package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Brand;
import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductField;
import com.shlyapoff.shop.model.VariantType;
import com.shlyapoff.shop.service.BrandService;
import com.shlyapoff.shop.service.CategoryService;
import com.shlyapoff.shop.service.ProductService;
import com.shlyapoff.shop.service.ProductVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_IMAGE_DIMENSION = 1200;
    private static final int THUMBNAIL_MAX_DIMENSION = 480;
    private static final float JPEG_QUALITY = 0.85f;

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ProductVariantService productVariantService;
    private final ProductVariantRepository productVariantRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    // Список товаров в админке
    @GetMapping
    public String adminPage(@RequestParam(defaultValue = "0") int page, Model model) {
        var productPage = productService.findAdminProducts(page);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", productPage.getNumber());
        model.addAttribute("totalPages", productPage.getTotalPages());
        return "admin/products";
    }

    // Форма добавления товара
    @GetMapping("/product/create")
    public String createProductForm(Model model) {
        model.addAttribute("product", new Product());
        addProductFormData(model);
        return "admin/product-form";
    }

    // Обработка формы добавления товара
    @PostMapping("/product/create")
    @Transactional
    public String createProduct(
            @ModelAttribute("product") Product product,
            @RequestParam("category_id") Long categoryId,
            @RequestParam("brand_id") Long brandId,
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam(required = false) List<String> variantValues,
            @RequestParam(required = false) List<Integer> variantStockQuantities,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Устанавливаем категорию и бренд
        Optional<Category> category = categoryService.findById(categoryId);
        Optional<Brand> brand = brandService.findById(brandId);

        category.ifPresent(product::setCategory);
        brand.ifPresent(product::setBrand);

        String validationError = validateProduct(product);
        if (validationError != null) {
            model.addAttribute("errorMessage", validationError);
            addProductFormData(model);
            return "admin/product-form";
        }
        if (getVariantType(product) != VariantType.NONE
                && (variantValues == null || variantValues.stream().allMatch(value -> value == null || value.isBlank()))) {
            model.addAttribute("errorMessage", "Добавьте хотя бы один вариант товара");
            addProductFormData(model);
            return "admin/product-form";
        }

        // Обрабатываем загрузку картинки
        if (!imageFile.isEmpty()) {
            StoredImage image = saveImage(imageFile);
            product.setImageUrl(image.imageUrl());
            product.setImageThumbnailUrl(image.thumbnailUrl());
        }

        Product savedProduct = productService.save(product);
        saveProductVariants(savedProduct.getId(), variantValues, variantStockQuantities);
        redirectAttributes.addFlashAttribute("successMessage", "Товар успешно добавлен!");
        return "redirect:/admin";
    }

    // Форма редактирования товара
    @GetMapping("/product/edit/{id}")
    public String editProductForm(@PathVariable Long id, Model model) {
        Optional<Product> product = productService.findByIdWithVariants(id);
        if (product.isEmpty()) {
            return "redirect:/admin";
        }
        model.addAttribute("product", product.get());
        addProductFormData(model);
        return "admin/product-form";
    }

    // Обработка формы редактирования товара
    @PostMapping("/product/edit/{id}")
    public String editProduct(
            @PathVariable Long id,
            @ModelAttribute("product") Product product,
            @RequestParam("category_id") Long categoryId,
            @RequestParam("brand_id") Long brandId,
            @RequestParam("imageFile") MultipartFile imageFile,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Находим существующий товар
        Optional<Product> existingProduct = productService.findById(id);
        if (existingProduct.isEmpty()) {
            return "redirect:/admin";
        }

        Product productToUpdate = existingProduct.get();

        Optional<Category> category = categoryService.findById(categoryId);
        Optional<Brand> brand = brandService.findById(brandId);
        category.ifPresent(product::setCategory);
        brand.ifPresent(product::setBrand);

        String validationError = validateProduct(product);
        if (validationError != null) {
            model.addAttribute("errorMessage", validationError);
            addProductFormData(model);
            return "admin/product-form";
        }

        // Обновляем поля
        productToUpdate.setName(product.getName());
        productToUpdate.setDescription(product.getDescription());
        productToUpdate.setPrice(product.getPrice());
        productToUpdate.setNicotineStrength(product.getNicotineStrength());
        productToUpdate.setVolume(product.getVolume());
        productToUpdate.setBatteryCapacity(product.getBatteryCapacity());
        productToUpdate.setPuffCount(product.getPuffCount());
        productToUpdate.setCartridgeVolume(product.getCartridgeVolume());
        productToUpdate.setMaxPower(product.getMaxPower());
        productToUpdate.setPackageQuantity(product.getPackageQuantity());
        productToUpdate.setStockQuantity(product.getStockQuantity());
        productToUpdate.setActive(true);

        // Обновляем категорию и бренд
        category.ifPresent(productToUpdate::setCategory);
        brand.ifPresent(productToUpdate::setBrand);

        // Если загружена новая картинка, заменяем старую
        if (!imageFile.isEmpty()) {
            StoredImage image = saveImage(imageFile);
            productToUpdate.setImageUrl(image.imageUrl());
            productToUpdate.setImageThumbnailUrl(image.thumbnailUrl());
        }

        productService.save(productToUpdate);
        redirectAttributes.addFlashAttribute("successMessage", "Товар успешно обновлен!");
        return "redirect:/admin";
    }

    // Удаление товара
    @PostMapping("/product/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (productService.deleteById(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Товар снят с продажи.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Товар не найден.");
        }
        return "redirect:/admin";
    }

    // Метод для сохранения картинки
    private StoredImage saveImage(MultipartFile imageFile) {

        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Ошибка: разрешено загружать только изображения!");
        }

        try {
            // Создаем папку, если её нет
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Генерируем уникальное имя файла
            String originalFilename = imageFile.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            if (!extension.matches("(?i)\\.(jpg|jpeg|png)")) {
                throw new IllegalArgumentException("Недопустимый формат файла. Используйте JPG или PNG.");
            }

            String newFilename = UUID.randomUUID() + ".jpg";

            Path filePath = uploadPath.resolve(newFilename);
            Path thumbnailsPath = uploadPath.resolve("thumbs");
            Files.createDirectories(thumbnailsPath);
            Path thumbnailPath = thumbnailsPath.resolve(newFilename);
            BufferedImage source;
            try (InputStream inputStream = imageFile.getInputStream()) {
                source = ImageIO.read(inputStream);
            }
            if (source == null) {
                throw new IllegalArgumentException("Не удалось прочитать изображение. Загрузите JPG или PNG.");
            }
            writeJpeg(resizeImage(source, MAX_IMAGE_DIMENSION), filePath);
            writeJpeg(resizeImage(source, THUMBNAIL_MAX_DIMENSION), thumbnailPath);

            return new StoredImage("/images/" + newFilename, "/images/thumbs/" + newFilename);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить изображение", e);
        }
    }

    private BufferedImage resizeImage(BufferedImage source, int maximumDimension) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(1.0, (double) maximumDimension / Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private void writeJpeg(BufferedImage image, Path path) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(JPEG_QUALITY);

        try (ImageOutputStream output = ImageIO.createImageOutputStream(Files.newOutputStream(path))) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private record StoredImage(String imageUrl, String thumbnailUrl) {
    }

    // Категориии
    @GetMapping("/categories")
    public String categoriesPage(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "admin/categories";
    }

    @GetMapping("/category/create")
    public String createCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        model.addAttribute("variantTypes", VariantType.values());
        return "admin/category-form";
    }

    @PostMapping("/category/create")
    public String createCategory(@ModelAttribute("category") Category category, RedirectAttributes redirectAttributes) {
        categoryService.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Категория успешно добавлена!");
        return "redirect:/admin/categories";
    }

    @GetMapping("/category/edit/{id}")
    public String editCategoryForm(@PathVariable Long id, Model model) {
        Optional<Category> category = categoryService.findById(id);
        if (category.isEmpty()) return "redirect:/admin/categories";
        model.addAttribute("category", category.get());
        model.addAttribute("variantTypes", VariantType.values());
        return "admin/category-form";
    }

    @PostMapping("/category/edit/{id}")
    public String editCategory(@PathVariable Long id, @ModelAttribute("category") Category category, RedirectAttributes redirectAttributes) {
        Optional<Category> existingCategory = categoryService.findById(id);
        if (existingCategory.isEmpty()) return "redirect:/admin/categories";

        Category categoryToUpdate = existingCategory.get();
        categoryToUpdate.setName(category.getName());
        categoryToUpdate.setVariantType(category.getVariantType());
        categoryService.save(categoryToUpdate);

        redirectAttributes.addFlashAttribute("successMessage", "Категория успешно обновлена!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/category/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Категория успешно удалена!");
        return "redirect:/admin/categories";
    }

    // Бренды
    @GetMapping("/brands")
    public String brandsPage(Model model) {
        model.addAttribute("brands", brandService.findAll());
        return "admin/brands";
    }

    @GetMapping("/brand/create")
    public String createBrandForm(Model model) {
        model.addAttribute("brand", new Brand());
        return "admin/brand-form";
    }

    @PostMapping("/brand/create")
    public String createBrand(@ModelAttribute("brand") Brand brand, RedirectAttributes redirectAttributes) {
        brandService.save(brand);
        redirectAttributes.addFlashAttribute("successMessage", "Бренд успешно добавлен!");
        return "redirect:/admin/brands";
    }

    @GetMapping("/brand/edit/{id}")
    public String editBrandForm(@PathVariable Long id, Model model) {
        Optional<Brand> brand = brandService.findById(id);
        if (brand.isEmpty()) return "redirect:/admin/brands";
        model.addAttribute("brand", brand.get());
        return "admin/brand-form";
    }

    @PostMapping("/brand/edit/{id}")
    public String editBrand(@PathVariable Long id, @ModelAttribute("brand") Brand brand, RedirectAttributes redirectAttributes) {
        Optional<Brand> existingBrand = brandService.findById(id);
        if (existingBrand.isEmpty()) return "redirect:/admin/brands";

        Brand brandToUpdate = existingBrand.get();
        brandToUpdate.setName(brand.getName());
        brandService.save(brandToUpdate);

        redirectAttributes.addFlashAttribute("successMessage", "Бренд успешно обновлен!");
        return "redirect:/admin/brands";
    }

    @PostMapping("/brand/delete/{id}")
    public String deleteBrand(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        brandService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Бренд успешно удален!");
        return "redirect:/admin/brands";
    }

    @GetMapping("/product/{id}/variants")
    public String manageVariants(@PathVariable Long id, Model model) {
        Optional<Product> product = productService.findByIdWithVariants(id);
        if (product.isEmpty()) {
            return "redirect:/admin";
        }
        if (getVariantType(product.get()) == VariantType.NONE) {
            return "redirect:/admin";
        }

        model.addAttribute("product", product.get());
        model.addAttribute("variants", productVariantService.findByProductId(id));
        model.addAttribute("variantType", getVariantType(product.get()));
        return "admin/variants";
    }

    @PostMapping("/product/{id}/variant/add")
    public String addVariant(@PathVariable Long id,
                             @RequestParam String value,
                             @RequestParam(defaultValue = "0") Integer stockQuantity,
                             RedirectAttributes redirectAttributes) {
        Optional<Product> product = productService.findByIdWithVariants(id);
        if (product.isEmpty() || getVariantType(product.get()) == VariantType.NONE) {
            return "redirect:/admin";
        }
        if (stockQuantity == null || stockQuantity < 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Остаток варианта не может быть отрицательным");
            return "redirect:/admin/product/" + id + "/variants";
        }
        productVariantService.save(id, value, stockQuantity);
        redirectAttributes.addFlashAttribute("successMessage", "Вариант добавлен!");
        return "redirect:/admin/product/" + id + "/variants";
    }

    @PostMapping("/product/variant/{variantId}/stock")
    public String updateVariantStock(@PathVariable Long variantId,
                                     @RequestParam Integer stockQuantity,
                                     RedirectAttributes redirectAttributes) {
        Optional<ProductVariant> variantOpt = productVariantRepository.findByIdWithProduct(variantId);
        if (variantOpt.isPresent()) {
            ProductVariant variant = variantOpt.get();
            if (stockQuantity == null || stockQuantity < 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "Остаток варианта не может быть отрицательным");
                return "redirect:/admin/product/" + variant.getProduct().getId() + "/variants";
            }
            productVariantService.updateStockQuantity(variantId, stockQuantity);
            redirectAttributes.addFlashAttribute("successMessage", "Наличие обновлено!");
            return "redirect:/admin/product/" + variant.getProduct().getId() + "/variants";
        }
        return "redirect:/admin";
    }

    @PostMapping("/product/variant/{variantId}/delete")
    public String deleteVariant(@PathVariable Long variantId, RedirectAttributes redirectAttributes) {
        Optional<ProductVariant> variantOpt = productVariantRepository.findByIdWithProduct(variantId);
        if (variantOpt.isPresent()) {
            Long productId = variantOpt.get().getProduct().getId();
            productVariantService.deleteById(variantId);
            redirectAttributes.addFlashAttribute("successMessage", "Вариант удален!");
            return "redirect:/admin/product/" + productId + "/variants";
        }
        return "redirect:/admin";
    }

    private VariantType getVariantType(Product product) {
        if (product.getCategory() == null || product.getCategory().getVariantType() == null) {
            return VariantType.NONE;
        }
        return product.getCategory().getVariantType();
    }

    private void saveProductVariants(Long productId, List<String> variantValues, List<Integer> variantStockQuantities) {
        if (variantValues == null) {
            return;
        }

        for (int index = 0; index < variantValues.size(); index++) {
            String value = variantValues.get(index);
            if (value != null && !value.isBlank()) {
                Integer stockQuantity = variantStockQuantities != null && index < variantStockQuantities.size()
                        ? variantStockQuantities.get(index)
                        : 0;
                productVariantService.save(productId, value.trim(), stockQuantity);
            }
        }
    }

    private void addProductFormData(Model model) {
        List<Category> categories = categoryService.findAll();
        Map<Long, List<ProductField>> fieldsByCategory = categories.stream()
                .collect(Collectors.toMap(Category::getId, ProductField::forCategory));
        model.addAttribute("categories", categories);
        model.addAttribute("brands", brandService.findAll());
        model.addAttribute("fieldsByCategory", fieldsByCategory);
    }

    private String validateProduct(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            return "Укажите название товара";
        }
        if (product.getPrice() == null || product.getPrice().signum() < 0) {
            return "Цена товара не может быть отрицательной";
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            return "Остаток товара не может быть отрицательным";
        }
        if (product.getCategory() == null || product.getBrand() == null) {
            return "Выберите существующие категорию и бренд";
        }
        for (ProductField field : ProductField.forCategory(product.getCategory())) {
            String value = field.getValue(product);
            if (value == null || value.isBlank()) {
                return "Заполните поле: " + field.getDisplayName();
            }
        }
        return null;
    }
}
