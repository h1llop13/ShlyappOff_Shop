package com.shlyapoff.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderDto {

    @NotBlank(message = "Укажите имя")
    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    private String customerName;

    @Pattern(regexp = "^\\+?[0-9\\s\\-()]{10,20}$", message = "Некорректный номер телефона")
    private String phone;

    @NotBlank(message = "Выберите способ получения")
    private String deliveryType;

    @Size(max = 500, message = "Комментарий слишком длинный")
    private String comment;
}
