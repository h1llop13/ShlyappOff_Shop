package com.shlyapoff.shop.config;

import com.shlyapoff.shop.model.User;
import com.shlyapoff.shop.repository.BrandRepository;
import com.shlyapoff.shop.repository.CategoryRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private ProductRepository productRepository;

    @Test
    void replacesExistingAdminPasswordFromEnvironment() throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User existing = new User();
        existing.setUsername("admin");
        existing.setPassword(encoder.encode("old-password"));
        existing.setRole("ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        DataInitializer initializer = initializer(encoder, "admin");
        initializer.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(encoder.matches("admin", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void leavesExistingPasswordUntouchedWhenEnvironmentPasswordIsBlank() throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User existing = new User();
        existing.setUsername("admin");
        existing.setPassword(encoder.encode("existing-password"));
        existing.setRole("ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        initializer(encoder, "").run();

        verify(userRepository, never()).save(any());
    }

    private DataInitializer initializer(BCryptPasswordEncoder encoder, String password) {
        DataInitializer initializer = new DataInitializer(userRepository, categoryRepository, brandRepository,
                productRepository, encoder);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
        return initializer;
    }

    @Test
    void doesNotPromoteExistingUserWhenPasswordIsNotExplicitlyConfigured() {
        User user = new User();
        user.setUsername("admin");
        user.setRole("ROLE_USER");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        initializer(new BCryptPasswordEncoder(), "").run();
        assertThat(user.getRole()).isEqualTo("ROLE_USER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void existingBrandsAreNotRecreatedWhenCategoriesAreEmpty() {
        when(brandRepository.count()).thenReturn(2L);
        initializer(new BCryptPasswordEncoder(), "admin").run();
        verify(brandRepository, never()).save(any());
    }
}
