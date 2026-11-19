package com.shlyapoff.shop.config;

import com.shlyapoff.shop.model.Brand;
import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.VariantType;
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
        initializeAdmin();

        if (categoryRepository.count() == 0) {
            Category cat1 = new Category();
            cat1.setName("Одноразки");
            cat1.setVariantType(VariantType.FLAVOR);
            categoryRepository.save(cat1);

            Category cat2 = new Category();
            cat2.setName("Жидкости");
            cat2.setVariantType(VariantType.FLAVOR);
            categoryRepository.save(cat2);

        }
        if (brandRepository.count() == 0) {
            Brand brand1 = new Brand();
            brand1.setName("HQD");
            brandRepository.save(brand1);

            Brand brand2 = new Brand();
            brand2.setName("ELFBAR");
            brandRepository.save(brand2);

            System.out.println("Начальные бренды созданы");
        }
    }

    private void initializeAdmin() {
        boolean configuredPassword = adminPassword != null && !adminPassword.isBlank();
        var existingAdmin = userRepository.findByUsername(adminUsername);

        if (existingAdmin.isPresent()) {
            User admin = existingAdmin.get();
            boolean changed = false;
            if (configuredPassword && !"ROLE_ADMIN".equals(admin.getRole())) {
                admin.setRole("ROLE_ADMIN");
                changed = true;
            }
            // An explicitly configured password is the source of truth on every start.
            // It makes a recovery from a lost local password possible without deleting data.
            if (configuredPassword && !passwordEncoder.matches(adminPassword, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(adminPassword));
                changed = true;
            }
            if (changed) {
                userRepository.save(admin);
                System.out.println("Учётная запись администратора '" + adminUsername + "' обновлена из переменных окружения.");
            }
            return;
        }

        if (userRepository.count() != 0 && !configuredPassword) {
            return;
        }

        String rawPassword = configuredPassword ? adminPassword : generateRandomPassword();
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRole("ROLE_ADMIN");
        userRepository.save(admin);

        System.out.println("=====================================================");
        System.out.println("Создан администратор: логин '" + adminUsername + "'");
        if (configuredPassword) {
            System.out.println("Пароль взят из переменной окружения ADMIN_PASSWORD.");
        } else {
            System.out.println("Сгенерированный пароль (сохраните и смените после входа): " + rawPassword);
        }
        System.out.println("=====================================================");
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
