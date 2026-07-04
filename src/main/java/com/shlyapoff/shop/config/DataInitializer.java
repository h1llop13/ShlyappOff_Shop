package com.shlyapoff.shop.config;

import com.shlyapoff.shop.model.Brand;
import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.User;
import com.shlyapoff.shop.repository.BrandRepository;
import com.shlyapoff.shop.repository.CategoryRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername("admin");
            //шифр пароля
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ROLE_ADMIN");
            userRepository.save(admin);
            System.out.println("Админ создан: логин 'admin', пароль 'admin123'");
        }

        if (categoryRepository.count() == 0) {
            Category cat1 = new Category();
            cat1.setName("Одноразки");
            categoryRepository.save(cat1);

            Category cat2 = new Category();
            cat2.setName("Жидкости");
            categoryRepository.save(cat2);

            Brand brand1 = new Brand();
            brand1.setName("HQD");
            brandRepository.save(brand1);

            Brand brand2 = new Brand();
            brand2.setName("ELFBAR");
            brandRepository.save(brand2);

            System.out.println("Тестовые категории и бренды созданы");
        }
    }
}
