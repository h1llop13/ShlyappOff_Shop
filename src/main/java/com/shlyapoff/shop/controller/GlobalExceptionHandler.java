package com.shlyapoff.shop.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, BindException.class,
            MethodArgumentNotValidException.class})
    public Object handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(exception, request, HttpStatus.BAD_REQUEST,
                exception.getMessage() == null ? "Проверьте введённые данные" : exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request error: {} {}", request.getMethod(), request.getRequestURI(), exception);
        return response(exception, request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Не удалось выполнить операцию. Попробуйте ещё раз позже.");
    }

    private Object response(Exception exception, HttpServletRequest request, HttpStatus status, String message) {
        if (request.getRequestURI().startsWith("/api/")) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
            problem.setTitle(status == HttpStatus.BAD_REQUEST ? "Некорректный запрос" : "Внутренняя ошибка");
            problem.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(status).body(problem);
        }
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("message", message);
        modelAndView.addObject("path", request.getRequestURI());
        return modelAndView;
    }
}
