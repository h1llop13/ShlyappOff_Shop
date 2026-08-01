package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.model.VariantType;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import com.shlyapoff.shop.service.BrandService;
import com.shlyapoff.shop.service.CategoryService;
import com.shlyapoff.shop.service.ProductService;
import com.shlyapoff.shop.service.ProductVariantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private ProductService productService;
    private ProductVariantService productVariantService;
    private ProductVariantRepository productVariantRepository;
    private CategoryService categoryService;
    private BrandService brandService;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        productVariantService = mock(ProductVariantService.class);
        productVariantRepository = mock(ProductVariantRepository.class);
        categoryService = mock(CategoryService.class);
        brandService = mock(BrandService.class);
        when(categoryService.findAll()).thenReturn(List.of());
        when(brandService.findAll()).thenReturn(List.of());
        controller = new AdminController(productService, categoryService, brandService,
                productVariantService, productVariantRepository);
    }

    @Test
    void editFormLoadsProductWithCategoryAndBrand() {
        Product product = productWithVariants();
        when(productService.findByIdWithVariants(7L)).thenReturn(Optional.of(product));

        String view = controller.editProductForm(7L, new ExtendedModelMap());

        assertThat(view).isEqualTo("admin/product-form");
        verify(productService).findByIdWithVariants(7L);
    }

    @Test
    void catalogShowsCategoriesInsteadOfAMixedProductList() {
        Category category = categoryWithId(4L);
        when(categoryService.findAll()).thenReturn(List.of(category));
        when(productService.activeProductCountsByCategory()).thenReturn(Map.of(4L, 3L));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.adminPage(model);

        assertThat(view).isEqualTo("admin/catalog");
        assertThat(model.get("categories")).isEqualTo(List.of(category));
        assertThat(model.get("productCounts")).isEqualTo(Map.of(4L, 3L));
    }

    @Test
    void categoryProductsPageLoadsOnlyTheSelectedCategory() {
        Category category = categoryWithId(4L);
        Product product = productWithVariants();
        when(categoryService.findById(4L)).thenReturn(Optional.of(category));
        when(productService.findAdminProductsByCategory(4L, 0)).thenReturn(new PageImpl<>(List.of(product)));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.categoryProductsPage(4L, 0, model);

        assertThat(view).isEqualTo("admin/products");
        assertThat(model.get("category")).isEqualTo(category);
        assertThat(model.get("products")).isEqualTo(List.of(product));
        verify(productService).findAdminProductsByCategory(4L, 0);
    }

    @Test
    void variantsPageLoadsProductWithCategory() {
        Product product = productWithVariants();
        when(productService.findByIdWithVariants(7L)).thenReturn(Optional.of(product));
        when(productVariantService.findByProductId(7L)).thenReturn(List.of());

        String view = controller.manageVariants(7L, new ExtendedModelMap());

        assertThat(view).isEqualTo("admin/variants");
        verify(productService).findByIdWithVariants(7L);
    }

    @Test
    void variantStockUsesProductFetchForRedirect() {
        Product product = productWithVariants();
        ProductVariant variant = new ProductVariant();
        variant.setId(3L);
        variant.setProduct(product);
        when(productVariantRepository.findByIdWithProduct(3L)).thenReturn(Optional.of(variant));

        String view = controller.updateVariantStock(3L, 5, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/admin/product/7/variants");
        verify(productVariantService).updateStockQuantity(3L, 5);
    }

    @Test
    void deleteProductDeactivatesInsteadOfRemovingOrderHistory() {
        Product product = productWithVariants();
        product.setCategory(categoryWithId(4L));
        when(productService.findById(7L)).thenReturn(Optional.of(product));
        when(productService.deleteById(7L)).thenReturn(true);

        String view = controller.deleteProduct(7L, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/admin/category/4/products");
        verify(productService).deleteById(7L);
    }

    private Product productWithVariants() {
        Category category = new Category();
        category.setVariantType(VariantType.FLAVOR);
        Product product = new Product();
        product.setId(7L);
        product.setCategory(category);
        return product;
    }

    private Category categoryWithId(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setVariantType(VariantType.FLAVOR);
        return category;
    }
}
