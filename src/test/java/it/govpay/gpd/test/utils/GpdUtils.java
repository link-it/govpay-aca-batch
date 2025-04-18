package it.govpay.gpd.test.utils;

import java.util.UUID;

import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import it.govpay.gpd.client.beans.ProblemJson;

public class GpdUtils {
	
	public static MultiValueMap<String, String> getHeadersCreatedOk(String xRequestId) {
		MultiValueMap<String, String> headers = new org.springframework.util.LinkedMultiValueMap<>();
		headers.add("Content-Length", "100");
		headers.add("Content-Type", "application/json");
		if(xRequestId == null)
			xRequestId = UUID.randomUUID().toString();
		headers.add("X-Request-Id", xRequestId);
		return headers;
	}
	
	public static MultiValueMap<String, String> getHeadersProblem(String xRequestId) {
		MultiValueMap<String, String> headers = new org.springframework.util.LinkedMultiValueMap<>();
		headers.add("Content-Length", "100");
		headers.add("Content-Type", "application/problem+json");
		if(xRequestId == null)
			xRequestId = UUID.randomUUID().toString();
		headers.add("X-Request-Id", xRequestId);
		return headers;
	}
	
	public static ResponseEntity<ProblemJson> creaResponseUpdateKo(InvocationOnMock invocation, HttpStatus httpStatus) {
		String xRequestId = invocation.getArgument(3);
		return creaResponseKo(xRequestId, httpStatus);
	}
	
	public static ResponseEntity<ProblemJson> creaResponseKo(InvocationOnMock invocation, HttpStatus httpStatus) {
		String xRequestId = invocation.getArgument(2);
		return creaResponseKo(xRequestId, httpStatus);
	}
	
	public static ResponseEntity<ProblemJson> creaResponseKo(String xRequestId, HttpStatus httpStatus) {
		ProblemJson response = null;
		
		switch (httpStatus) {
		case BAD_REQUEST:  
			response = createProblem400();
			break;
		case UNAUTHORIZED:  
			response = createProblem401();
			break;
		case FORBIDDEN:  
			response = createProblem403();
			break;
		case NOT_FOUND:  
			response = createProblem404();
			break;
		case CONFLICT:  
			response = createProblem409();
			break;
		case TOO_MANY_REQUESTS:  
			response = createProblem429();
			break;
		case INTERNAL_SERVER_ERROR:  
			response = createProblem500();
			break;
		case SERVICE_UNAVAILABLE:  
			response = createProblem503();
			break;
		default:
			throw new IllegalArgumentException("Unexpected value: " + httpStatus);
		}
		
		return new ResponseEntity<>(response, getHeadersProblem(xRequestId), httpStatus);
	}
	
	private static ProblemJson createProblemFromOpenAPI(String title, String detail, int status) {
		ProblemJson problem = new ProblemJson();
	    problem.setStatus(status);
	    problem.setTitle(title);
	    problem.setDetail(detail);
	    return problem;
	}

	public static ProblemJson createProblem400() {
	    return createProblemFromOpenAPI("Bad Request", "Missing required field.", 400);
	}

	public static ProblemJson createProblem401() {
	    return createProblemFromOpenAPI("Unauthorized", "Invalid Credentials", 401);
	}

	public static ProblemJson createProblem403() {
	    return createProblemFromOpenAPI("Forbidden", "User not authorized for the operation", 403);
	}

	public static ProblemJson createProblem404() {
	    return createProblemFromOpenAPI("Not Found", "Resource not found", 404);
	}

	public static ProblemJson createProblem409() {
	    return createProblemFromOpenAPI("Conflict", "Resource with the same id already present", 409);
	}

	public static ProblemJson createProblem429() {
	    return createProblemFromOpenAPI("Too Many Requests", "User has sent too many requests in a given amount of time", 429);
	}

	public static ProblemJson createProblem503() {
	    return createProblemFromOpenAPI("Service Unavailable", "Request Can't be fulfilled at the moment", 503);
	}
	
	public static ProblemJson createProblem500() {
		return createProblemFromOpenAPI("Internal Server Error", "Internal Server Error", 500);
	}
}
