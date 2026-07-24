package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductField;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.service.BrandService;
import com.shlyapoff.shop.service.CartService;
import com.shlyapoff.shop.service.CategoryService;
import com.shlyapoff.shop.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final CartService cartService;

    @GetMapping("/")
    public String homePage(Model model) {
        List<Product> products = productService.findAllActive();

        model.addAttribute("products", products);

        return "index";
    }

    @GetMapping("/catalog")
    public String catalogPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "0") int page, // Номер страницы (начинается с 0)
            Model model) {

        // Если строка поиска пустая, превращаем её в null, чтобы сработала логика в @Query
        if (search != null && search.trim().isEmpty()) {
            search = null;
        }

        // Запрашиваем страницу товаров (по 12 штук на страницу)
        Page<Product> productPage = productService.findWithFilters(search, categoryId, brandId, page, 12);

        // Кладем в модель сам список товаров для текущей страницы
        model.addAttribute("products", productPage.getContent());
        // Кладем информацию о пагинации
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());

        // Сохраняем параметры фильтрации, чтобы они не пропали при переходе по страницам
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brandId", brandId);

        // Списки для выпадающих меню фильтров
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("brands", brandService.findAll());

        return "catalog";
    }

    @GetMapping("/product/{id}")
    public String productPage(@PathVariable Long id, Model model) {
        // Используем новый метод, который сразу загружает варианты
        Optional<Product> product = productService.findByIdWithVariants(id);

        if (product.isEmpty()) {
            return "redirect:/";
        }

        Product prod = product.get();
        model.addAttribute("product", prod);
        model.addAttribute("productFields", ProductField.forCategory(prod.getCategory()));

        // Варианты уже загружены через JOIN FETCH, но можно явно передать
        model.addAttribute("variants", prod.getVariants());

        return "product";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Long productId, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        cartService.addToCart(sessionId, productId, 1);
        return "redirect:/catalog";
    }

    @GetMapping("/cart")
    public String cartPage(Model model, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        Optional<Cart> cartOpt = cartService.getCartBySessionId(sessionId);

        if (cartOpt.isPresent()) {
            Cart cart = cartOpt.get();
            model.addAttribute("cart", cart);

            double total = cart.getItems().stream()
                    .mapToDouble(item -> item.getProduct().getPrice().doubleValue() * item.getQuantity())
                    .sum();
            model.addAttribute("total", total);
        } else {
            model.addAttribute("cart", null);
        }

        return "cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        cartService.removeFromCart(sessionId, productId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateQuantity(@RequestParam Long productId, @RequestParam int quantity, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        cartService.updateQuantity(sessionId, productId, quantity);
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clearCart(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        cartService.clearCart(sessionId);
        return "redirect:/cart";
    }
}
