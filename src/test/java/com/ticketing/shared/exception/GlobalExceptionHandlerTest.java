package com.ticketing.shared.exception;

import com.ticketing.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void handleInvalidCredentials_shouldReturnUnauthorizedResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/auth/login");
        InvalidCredentialsException exception = new InvalidCredentialsException("Invalid credentials");

        // Act
        var response = globalExceptionHandler.handleInvalidCredentials(exception, request);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid credentials", response.getBody().getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().getStatus());
        assertEquals("/auth/login", response.getBody().getPath());
    }

    @Test
    void handleNotFound_shouldReturnNotFoundResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/events/1");
        ResourceNotFoundException exception = new ResourceNotFoundException("Event not found");

        // Act
        var response = globalExceptionHandler.handleNotFound(exception, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Event not found", response.getBody().getMessage());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals("/events/1", response.getBody().getPath());
    }

    @Test
    void handleBadRequest_shouldReturnBadRequestResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/bookings");
        BadRequestException exception = new BadRequestException("Invalid request");

        // Act
        var response = globalExceptionHandler.handleBadRequest(exception, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request", response.getBody().getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("/bookings", response.getBody().getPath());
    }

    @Test
    void handleBadCredentials_shouldReturnUnauthorizedResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/auth/login");
        BadCredentialsException exception = new BadCredentialsException("bad credentials");

        // Act
        var response = globalExceptionHandler.handleBadCredentials(exception, request);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid email or password", response.getBody().getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getBody().getStatus());
        assertEquals("/auth/login", response.getBody().getPath());
    }

    @Test
    void handleValidationException_shouldReturnFirstFieldErrorMessage() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/auth/register");
        MethodArgumentNotValidException exception = buildMethodArgumentNotValidException("email", "Email is required");

        // Act
        var response = globalExceptionHandler.handleValidationException(exception, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Email is required", response.getBody().getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("/auth/register", response.getBody().getPath());
    }

    @Test
    void handleAccessDenied_shouldReturnForbiddenResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/events");
        AccessDeniedException exception = new AccessDeniedException("denied");

        // Act
        var response = globalExceptionHandler.handleAccessDenied(exception, request);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().getMessage());
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().getStatus());
        assertEquals("/events", response.getBody().getPath());
    }

    @Test
    void handleSeatAlreadyExists_shouldReturnConflictResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/events/1/seats");
        SeatAlreadyExistsException exception = new SeatAlreadyExistsException("Seat already exists for this event");

        // Act
        var response = globalExceptionHandler.handleSeatAlreadyExists(exception, request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Seat already exists for this event", response.getBody().getMessage());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals("/events/1/seats", response.getBody().getPath());
    }

    @Test
    void handleSeatAlreadyBooked_shouldReturnConflictResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/bookings");
        SeatAlreadyBookedException exception = new SeatAlreadyBookedException();

        // Act
        var response = globalExceptionHandler.handleSeatAlreadyBooked(exception, request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Seat is already booked", response.getBody().getMessage());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals("/bookings", response.getBody().getPath());
    }

    @Test
    void handlePaymentFailed_shouldReturnBadRequestResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/bookings");
        PaymentFailedException exception = new PaymentFailedException();

        // Act
        var response = globalExceptionHandler.handlePaymentFailed(exception, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Payment failed", response.getBody().getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("/bookings", response.getBody().getPath());
    }

    @Test
    void handleSeatNotHeldByUser_shouldReturnForbiddenResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/bookings");
        SeatNotHeldByUserException exception = new SeatNotHeldByUserException("Seat is held by another user");

        // Act
        var response = globalExceptionHandler.handleSeatNotHeldByUserException(exception, request);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Seat is held by another user", response.getBody().getMessage());
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getBody().getStatus());
        assertEquals("/bookings", response.getBody().getPath());
    }

    @Test
    void handlePaymentServiceUnavailable_shouldReturnServiceUnavailableResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/bookings");
        PaymentServiceUnavailableException exception = new PaymentServiceUnavailableException("Payment service is temporarily unavailable.");

        // Act
        var response = globalExceptionHandler.handlePaymentServiceUnavailable(exception, request);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Payment service is temporarily unavailable.", response.getBody().getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getBody().getStatus());
        assertEquals("/bookings", response.getBody().getPath());
    }

    @Test
    void handleGeneric_shouldReturnInternalServerErrorResponse() {
        // Arrange
        when(request.getRequestURI()).thenReturn("/unexpected");
        Exception exception = new Exception("boom");

        // Act
        var response = globalExceptionHandler.handleGeneric(exception, request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Something went wrong", response.getBody().getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().getStatus());
        assertEquals("/unexpected", response.getBody().getPath());
    }

    private MethodArgumentNotValidException buildMethodArgumentNotValidException(String fieldName, String message)
            throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", LoginRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new LoginRequest(), "loginRequest");
        bindingResult.addError(new FieldError("loginRequest", fieldName, message));
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void sampleMethod(LoginRequest request) {
        // used only for MethodParameter construction in tests
    }
}
