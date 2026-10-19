package com.ewallet.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

// Extend ResponseEntityExceptionHandler supaya error bawaan Spring MVC (JSON rusak, method salah, path tidak ada)
// ikut menjadi ProblemDetail, bukan jatuh ke handler 500 di bawah.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
		return problem(HttpStatus.CONFLICT, "Email already registered", ex);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", ex);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "User not found", ex);
	}

	@ExceptionHandler(WalletNotFoundException.class)
	public ProblemDetail handleWalletNotFound(WalletNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Wallet not found", ex);
	}

	@ExceptionHandler(WalletInactiveException.class)
	public ProblemDetail handleWalletInactive(WalletInactiveException ex) {
		return problem(HttpStatus.FORBIDDEN, "Wallet inactive", ex);
	}

	@ExceptionHandler(RecipientNotFoundException.class)
	public ProblemDetail handleRecipientNotFound(RecipientNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Recipient not found", ex);
	}

	@ExceptionHandler(SelfTransferException.class)
	public ProblemDetail handleSelfTransfer(SelfTransferException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid transfer", ex);
	}

	@ExceptionHandler(InsufficientBalanceException.class)
	public ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance", ex);
	}

	@ExceptionHandler(InvalidIdempotencyKeyException.class)
	public ProblemDetail handleInvalidIdempotencyKey(InvalidIdempotencyKeyException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid idempotency key", ex);
	}

	@ExceptionHandler(IdempotencyKeyReuseException.class)
	public ProblemDetail handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused", ex);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
																  HttpHeaders headers,
																  HttpStatusCode status,
																  WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setTitle("Invalid request");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	// Jaring pengaman: detail error tidak dikirim ke klien, hanya dicatat di log
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");
		problem.setTitle("Internal server error");
		return problem;
	}

	private static ProblemDetail problem(HttpStatus status, String title, Exception ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setTitle(title);
		return problem;
	}
}
