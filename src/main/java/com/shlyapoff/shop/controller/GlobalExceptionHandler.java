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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public Object handleConcurrentUpdate(Exception exception, HttpServletRequest request) {
        return response(exception, request, HttpStatus.CONFLICT,
                "Данные изменились во время запроса. Обновите страницу и повторите действие.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataConflict(Exception exception, HttpServletRequest request) {
        return response(exception, request, HttpStatus.CONFLICT,
                "Не удалось сохранить изменение: запись уже существует или используется в других данных.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public Object handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return response(exception, request, HttpStatus.BAD_REQUEST, "Проверьте формат и обязательные поля запроса.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(Exception exception, HttpServletRequest request) {
        return response(exception, request, HttpStatus.NOT_FOUND, "Страница не найдена.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleLargeUpload(Exception exception, HttpServletRequest request) {
        return response(exception, request, HttpStatus.PAYLOAD_TOO_LARGE, "Изображение слишком большое. Максимум — 5 МБ.");
    }

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
        if (request.getRequestURI().startsWith("/api/") || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
            problem.setTitle(status.getReasonPhrase());
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
