package com.innovax.ganadera.common.error;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.innovax.ganadera.cobro.MercadoPagoException;
import com.innovax.ganadera.cobro.PagoRequeridoException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
            .toList();
        ApiError body = new ApiError(Instant.now(), 400, "Bad Request",
            "Hay datos inválidos", req.getRequestURI(), fields, null);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ResponseEntity<ApiError> handleDatosInvalidos(DatosInvalidosException ex, HttpServletRequest req) {
        ApiError body = new ApiError(Instant.now(), 400, "Bad Request",
            ex.getMessage(), req.getRequestURI(), ex.getFieldErrors(), null);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "La solicitud tiene un formato inválido", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Falta el parámetro " + ex.getParameterName(), req);
    }

    /** Subida de archivos (#43): sin la parte "archivo" o con un multipart mal armado. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<ApiError> handleMultipart(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "No llegó el archivo a adjuntar", req);
    }

    /** Lo corta Spring antes de llegar al controller, con el tope de app.adjuntos.tamanio-maximo. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleArchivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo es demasiado grande", req);
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest req) {
        String message = ex instanceof NotFoundException ? ex.getMessage() : "Recurso no encontrado";
        return build(HttpStatus.NOT_FOUND, message, req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "No tenés permisos para esta acción", req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(PagoRequeridoException.class)
    ResponseEntity<ApiError> handlePagoRequerido(PagoRequeridoException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
            status.getReasonPhrase(), ex.getMessage(), req.getRequestURI(), List.of(), PagoRequeridoException.CODIGO));
    }

    @ExceptionHandler(MercadoPagoException.class)
    ResponseEntity<ApiError> handleMercadoPago(MercadoPagoException ex,
                                               HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY,
            "No pudimos comunicarnos con Mercado Pago. Probá de nuevo en unos minutos.", req);
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    ResponseEntity<ApiError> handleDemasiadosIntentos(DemasiadosIntentosException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
            status.getReasonPhrase(), ex.getMessage(), req.getRequestURI(), List.of(), null));
    }

    @ExceptionHandler(CuentaSuspendidaException.class)
    ResponseEntity<ApiError> handleSuspendida(CuentaSuspendidaException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
            status.getReasonPhrase(), ex.getMessage(), req.getRequestURI(), List.of(), CuentaSuspendidaException.CODIGO));
    }

    /** Antes que BusinessException: lleva un código para que la web ofrezca cambiar de plan (#35). */
    @ExceptionHandler(LimitePlanException.class)
    ResponseEntity<ApiError> handleLimitePlan(LimitePlanException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
            status.getReasonPhrase(), ex.getMessage(), req.getRequestURI(), List.of(), LimitePlanException.CODIGO));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
            .body(ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }
}
