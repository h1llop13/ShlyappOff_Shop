package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Favorite;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.FavoriteRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Long> findProductIds(Customer customer) {
        return favoriteRepository.findAllByCustomerId(customer.getId()).stream()
                .map(favorite -> favorite.getProduct().getId()).toList();
    }

    @Transactional
    public boolean toggle(Customer customer, Long productId) {
        var existing = favoriteRepository.findByCustomerIdAndProductId(customer.getId(), productId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
        Favorite favorite = new Favorite();
        favorite.setCustomer(customer);
        favorite.setProduct(product);
        favoriteRepository.save(favorite);
        return true;
    }
}
