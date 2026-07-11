package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.User;
import com.shlyapoff.shop.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для UserService и CustomUserDetailsService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("findByUsername возвращает пользователя, если он существует")
    void findByUsernameReturnsUser() {
        User user = new User();
        user.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByUsername("admin");

        assertThat(result).isPresent().contains(user);
    }

    @Test
    @DisplayName("findByUsername возвращает пустой Optional, если пользователя нет")
    void findByUsernameReturnsEmptyWhenMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save сохраняет пользователя через репозиторий")
    void saveDelegatesToRepository() {
        User user = new User();
        user.setUsername("new_user");
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.save(user);

        assertThat(result.getUsername()).isEqualTo("new_user");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("loadUserByUsername возвращает UserDetails с корректной ролью для Spring Security")
    void loadUserByUsernameReturnsUserDetailsWithRole() {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("encoded-password");
        user.setRole("ROLE_ADMIN");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("admin");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getPassword()).isEqualTo("encoded-password");
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername выбрасывает UsernameNotFoundException для несуществующего пользователя")
    void loadUserByUsernameThrowsWhenUserMissing() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown");
    }
}