package com.shlyapoff.shop.config;

import com.shlyapoff.shop.model.Brand;
import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.User;
import com.shlyapoff.shop.repository.BrandRepository;
import com.shlyapoff.shop.repository.CategoryRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    // Логин/пароль первого админа можно задать через переменные окружения.
    // Если ADMIN_PASSWORD не задан — сгенерируется случайный пароль
    // и один раз будет выведен в лог при первом запуске.
    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            String rawPassword = (adminPassword == null || adminPassword.isBlank())
                    ? generateRandomPassword()
                    : adminPassword;

            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(rawPassword));
            admin.setRole("ROLE_ADMIN");
            userRepository.save(admin);

            System.out.println("=====================================================");
            System.out.println("Создан администратор: логин '" + adminUsername + "'");
            if (adminPassword == null || adminPassword.isBlank()) {
                System.out.println("Сгенерированный пароль (сохраните и смените после входа): " + rawPassword);
            } else {
                System.out.println("Пароль взят из переменной окружения ADMIN_PASSWORD.");
            }
            System.out.println("=====================================================");
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

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}