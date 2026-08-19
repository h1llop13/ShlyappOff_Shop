package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.dto.ProductCard;
import com.shlyapoff.shop.model.ProductField;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.model.VariantType;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.service.BrandService;
import com.shlyapoff.shop.service.CartService;
import com.shlyapoff.shop.service.CategoryService;
import com.shlyapoff.shop.service.ProductService;
import com.shlyapoff.shop.service.PricingService;
import com.shlyapoff.shop.service.TelegramCartSessionService;
import com.shlyapoff.shop.service.RecommendationService;
import com.shlyapoff.shop.service.ShoppingEventService;
import com.shlyapoff.shop.service.PromotionService;
import com.shlyapoff.shop.model.AbandonmentReason;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Controller
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final CartService cartService;
    private final TelegramCartSessionService telegramCartSessionService;
    private final PricingService pricingService;
    private final RecommendationService recommendationService;
    private final ShoppingEventService shoppingEventService;
    private final PromotionService promotionService;

    @Autowired
    public HomeController(ProductService productService, CategoryService categoryService, BrandService brandService,
                          CartService cartService, TelegramCartSessionService telegramCartSessionService,
                          PricingService pricingService, RecommendationService recommendationService,
                          ShoppingEventService shoppingEventService, PromotionService promotionService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.cartService = cartService;
        this.telegramCartSessionService = telegramCartSessionService;
        this.pricingService = pricingService;
        this.recommendationService = recommendationService;
        this.shoppingEventService = shoppingEventService;
        this.promotionService = promotionService;
    }

    /** Совместимость с существующими изолированными тестами контроллера. */
    public HomeController(ProductService productService, CategoryService categoryService, BrandService brandService,
                          CartService cartService) {
        this(productService, categoryService, brandService, cartService,
                new TelegramCartSessionService(), new PricingService(BigDecimal.ZERO), null, null, null);
    }

    @GetMapping("/")
    public String homePage(Model model, HttpServletRequest request) {
        List<ProductCard> products = productService.findLatestActive();

        model.addAttribute("products", products);
        if (recommendationService != null) {
            Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
            model.addAttribute("recentlyViewed", recommendationService.recentlyViewed(
                    request.getSession().getId(), telegramUserId, null, 6));
        }
        if (promotionService != null) {
            model.addAttribute("activePromotions", promotionService.findActive());
        }

        return "index";
    }

    @GetMapping("/catalog")
    public String catalogPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean onlyInStock,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page, // Номер страницы (начинается с 0)
            Model model) {

        // Если строка поиска пустая, превращаем её в null, чтобы сработала логика в @Query
        if (search != null && search.trim().isEmpty()) {
            search = null;
        }

        // Запрашиваем страницу товаров (по 12 штук на страницу)
        Page<ProductCard> productPage = productService.findWithFilters(search, categoryId, brandId,
                minPrice, maxPrice, onlyInStock, sort, page, 12);

        // Кладем в модель сам список товаров для текущей страницы
        model.addAttribute("products", productPage.getContent());
        // Кладем информацию о пагинации
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());

        // Сохраняем параметры фильтрации, чтобы они не пропали при переходе по страницам
        model.addAttribute("search", search);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brandId", brandId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("onlyInStock", onlyInStock);
        model.addAttribute("sort", sort);

        // Списки для выпадающих меню фильтров
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("brands", brandService.findAll());

        return "catalog";
    }

    @GetMapping("/product/{id}")
    public String productPage(@PathVariable Long id, Model model, HttpServletRequest request) {
        // Используем новый метод, который сразу загружает варианты
        Optional<Product> product = productService.findByIdWithVariants(id);

        if (product.isEmpty() || !Boolean.TRUE.equals(product.get().getActive())) {
            return "redirect:/";
        }

        Product prod = product.get();
        model.addAttribute("product", prod);
        model.addAttribute("productFields", ProductField.forCategory(prod.getCategory()));

        // Варианты уже загружены через JOIN FETCH, но можно явно передать
        model.addAttribute("variants", prod.getVariants());
        boolean requiresVariant = prod.getCategory() != null
                && prod.getCategory().getVariantType() != null
                && prod.getCategory().getVariantType() != VariantType.NONE;
        model.addAttribute("requiresVariant", requiresVariant);
        model.addAttribute("hasInStockVariants", prod.getVariants().stream()
                .anyMatch(variant -> variant.getStockQuantity() != null && variant.getStockQuantity() > 0));
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        if (shoppingEventService != null) {
            shoppingEventService.recordProductView(request.getSession().getId(), telegramUserId, prod);
        }
        if (recommendationService != null) {
            model.addAttribute("similarProducts", recommendationService.similar(prod, 6));
            model.addAttribute("boughtTogether", recommendationService.boughtTogether(prod.getId(), 6));
            model.addAttribute("recentlyViewed", recommendationService.recentlyViewed(
                    request.getSession().getId(), telegramUserId, prod.getId(), 6));
        }

        return "product";
    }

    @PostMapping(value = "/cart/add", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public ResponseEntity<CartAddResponse> addToCartAsync(@RequestParam Long productId,
                                                           @RequestParam(required = false) Long variantId,
                                                           HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        try {
            if (telegramUserId == null) cartService.addToCart(sessionId, productId, variantId, 1);
            else cartService.addToCart(sessionId, telegramUserId, productId, variantId, 1);
            if (shoppingEventService != null) {
                productService.findById(productId)
                        .ifPresent(product -> shoppingEventService.recordCartAdd(sessionId, telegramUserId, product));
            }

            int itemCount = (telegramUserId == null ? cartService.getCartBySessionId(sessionId) : cartService.getCart(sessionId, telegramUserId))
                    .map(cart -> cart.getItems().stream()
                            .mapToInt(item -> item.getQuantity())
                            .sum())
                    .orElse(0);
            String productName = productService.findById(productId)
                    .map(Product::getName)
                    .orElse("Товар");

            return ResponseEntity.ok(new CartAddResponse(true, productName, itemCount, null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new CartAddResponse(false, null, 0, exception.getMessage()));
        }
    }

    @PostMapping(value = "/cart/add", headers = "!X-Requested-With")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(required = false) Long variantId,
                            HttpServletRequest request,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String sessionId = request.getSession().getId();
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        try {
            if (telegramUserId == null) cartService.addToCart(sessionId, productId, variantId, 1);
            else cartService.addToCart(sessionId, telegramUserId, productId, variantId, 1);
            if (shoppingEventService != null) {
                productService.findById(productId)
                        .ifPresent(product -> shoppingEventService.recordCartAdd(sessionId, telegramUserId, product));
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/product/" + productId;
        }
        return "redirect:/catalog";
    }

    public record CartAddResponse(boolean success, String productName, int itemCount, String message) {
    }

    @GetMapping("/cart")
    public String cartPage(Model model, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        Optional<Cart> cartOpt = telegramUserId == null ? cartService.getCartBySessionId(sessionId) : cartService.getCart(sessionId, telegramUserId);

        if (cartOpt.isPresent()) {
            Cart cart = cartOpt.get();
            model.addAttribute("cart", cart);

            model.addAttribute("total", pricingService.cartSubtotal(cart));
        } else {
            model.addAttribute("cart", null);
        }

        return "cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId,
                                 @RequestParam(required = false) Long variantId,
                                 HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        if (telegramUserId == null) cartService.removeFromCart(sessionId, productId, variantId);
        else cartService.removeFromCart(sessionId, telegramUserId, productId, variantId);
        Optional<Cart> remaining = telegramUserId == null
                ? cartService.getCartBySessionId(sessionId) : cartService.getCart(sessionId, telegramUserId);
        if (shoppingEventService != null && remaining.map(Cart::getItems).map(List::isEmpty).orElse(true)) {
            shoppingEventService.recordAbandoned(sessionId, telegramUserId, AbandonmentReason.REMOVED_LAST_ITEM);
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateQuantity(@RequestParam Long productId,
                                 @RequestParam(required = false) Long variantId,
                                 @RequestParam int quantity,
                                 HttpServletRequest request,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String sessionId = request.getSession().getId();
        try {
            Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
            if (telegramUserId == null) cartService.updateQuantity(sessionId, productId, variantId, quantity);
            else cartService.updateQuantity(sessionId, telegramUserId, productId, variantId, quantity);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clearCart(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        Long telegramUserId = telegramCartSessionService.getTelegramUserId(request.getSession());
        if (telegramUserId == null) cartService.clearCart(sessionId);
        else cartService.clearCart(sessionId, telegramUserId);
        if (shoppingEventService != null) {
            shoppingEventService.recordAbandoned(sessionId, telegramUserId, AbandonmentReason.CART_CLEARED);
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/abandon")
    public String recordAbandonment(@RequestParam AbandonmentReason reason, HttpServletRequest request) {
        if (shoppingEventService != null) {
            shoppingEventService.recordAbandoned(request.getSession().getId(),
                    telegramCartSessionService.getTelegramUserId(request.getSession()), reason);
        }
        return "redirect:/catalog";
    }
}
