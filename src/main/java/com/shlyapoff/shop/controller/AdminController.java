package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Brand;
import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.service.BrandService;
import com.shlyapoff.shop.service.CategoryService;
import com.shlyapoff.shop.service.ProductService;
import com.shlyapoff.shop.service.ProductVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ProductVariantService productVariantService;
    private final ProductVariantRepository productVariantRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    // Список товаров в админке
    @GetMapping
    public String adminPage(Model model) {
        model.addAttribute("products", productService.findAllActive());
        return "admin/products";
    }

    // Форма добавления товара
    @GetMapping("/product/create")
    public String createProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("brands", brandService.findAll());
        return "admin/product-form";
    }

    // Обработка формы добавления товара
    @PostMapping("/product/create")
    public String createProduct(
            @ModelAttribute("product") Product product,
            @RequestParam("category_id") Long categoryId,
            @RequestParam("brand_id") Long brandId,
            @RequestParam("imageFile") MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {

        // Устанавливаем категорию и бренд
        Optional<Category> category = categoryService.findById(categoryId);
        Optional<Brand> brand = brandService.findById(brandId);

        category.ifPresent(product::setCategory);
        brand.ifPresent(product::setBrand);

        // Обрабатываем загрузку картинки
        if (!imageFile.isEmpty()) {
            String imageUrl = saveImage(imageFile);
            product.setImageUrl(imageUrl);
        }

        productService.save(product);
        redirectAttributes.addFlashAttribute("successMessage", "Товар успешно добавлен!");
        return "redirect:/admin";
    }

    // Форма редактирования товара
    @GetMapping("/product/edit/{id}")
    public String editProductForm(@PathVariable Long id, Model model) {
        Optional<Product> product = productService.findById(id);
        if (product.isEmpty()) {
            return "redirect:/admin";
        }
        model.addAttribute("product", product.get());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("brands", brandService.findAll());
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
            RedirectAttributes redirectAttributes) {

        // Находим существующий товар
        Optional<Product> existingProduct = productService.findById(id);
        if (existingProduct.isEmpty()) {
            return "redirect:/admin";
        }

        Product productToUpdate = existingProduct.get();

        // Обновляем поля
        productToUpdate.setName(product.getName());
        productToUpdate.setDescription(product.getDescription());
        productToUpdate.setPrice(product.getPrice());
        productToUpdate.setNicotineStrength(product.getNicotineStrength());
        productToUpdate.setVolume(product.getVolume());
        productToUpdate.setActive(product.getActive());

        // Обновляем категорию и бренд
        Optional<Category> category = categoryService.findById(categoryId);
        Optional<Brand> brand = brandService.findById(brandId);

        category.ifPresent(productToUpdate::setCategory);
        brand.ifPresent(productToUpdate::setBrand);

        // Если загружена новая картинка, заменяем старую
        if (!imageFile.isEmpty()) {
            String imageUrl = saveImage(imageFile);
            productToUpdate.setImageUrl(imageUrl);
        }

        productService.save(productToUpdate);
        redirectAttributes.addFlashAttribute("successMessage", "Товар успешно обновлен!");
        return "redirect:/admin";
    }

    // Удаление товара
    @PostMapping("/product/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Товар успешно удален!");
        return "redirect:/admin";
    }

    // Метод для сохранения картинки
    private String saveImage(MultipartFile imageFile) {

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

            if (!extension.matches("(?i)\\.(jpg|jpeg|png|webp|gif)")) {
                throw new IllegalArgumentException("Недопустимый формат файла. Используйте JPG, PNG или WEBP.");
            }

            String newFilename = UUID.randomUUID().toString() + extension;

            // Сохраняем файл
            Path filePath = uploadPath.resolve(newFilename);
            Files.copy(imageFile.getInputStream(), filePath);

            // Возвращаем URL для доступа к файлу
            return "/images/" + newFilename;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
        return "admin/category-form";
    }

    @PostMapping("/category/edit/{id}")
    public String editCategory(@PathVariable Long id, @ModelAttribute("category") Category category, RedirectAttributes redirectAttributes) {
        Optional<Category> existingCategory = categoryService.findById(id);
        if (existingCategory.isEmpty()) return "redirect:/admin/categories";

        Category categoryToUpdate = existingCategory.get();
        categoryToUpdate.setName(category.getName());
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
        Optional<Product> product = productService.findById(id);
        if (product.isEmpty()) {
            return "redirect:/admin";
        }

        model.addAttribute("product", product.get());
        model.addAttribute("variants", productVariantService.findByProductId(id));
        return "admin/variants";
    }

    @PostMapping("/product/{id}/variant/add")
    public String addVariant(@PathVariable Long id,
                             @RequestParam String flavorName,
                             @RequestParam(defaultValue = "true") Boolean inStock,
                             RedirectAttributes redirectAttributes) {
        productVariantService.save(id, flavorName, inStock);
        redirectAttributes.addFlashAttribute("successMessage", "Вкус добавлен!");
        return "redirect:/admin/product/" + id + "/variants";
    }

    @PostMapping("/product/variant/{variantId}/toggle")
    public String toggleVariantStock(@PathVariable Long variantId, RedirectAttributes redirectAttributes) {
        Optional<ProductVariant> variantOpt = productVariantRepository.findById(variantId);
        if (variantOpt.isPresent()) {
            ProductVariant variant = variantOpt.get();
            productVariantService.updateStock(variantId, !variant.getInStock());
            redirectAttributes.addFlashAttribute("successMessage", "Наличие обновлено!");
            return "redirect:/admin/product/" + variant.getProduct().getId() + "/variants";
        }
        return "redirect:/admin";
    }

    @PostMapping("/product/variant/{variantId}/delete")
    public String deleteVariant(@PathVariable Long variantId, RedirectAttributes redirectAttributes) {
        Optional<ProductVariant> variantOpt = productVariantRepository.findById(variantId);
        if (variantOpt.isPresent()) {
            Long productId = variantOpt.get().getProduct().getId();
            productVariantService.deleteById(variantId);
            redirectAttributes.addFlashAttribute("successMessage", "Вкус удален!");
            return "redirect:/admin/product/" + productId + "/variants";
        }
        return "redirect:/admin";
    }
}