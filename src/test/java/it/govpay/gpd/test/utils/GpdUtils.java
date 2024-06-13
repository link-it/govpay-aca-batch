package it.govpay.gpd.test.utils;

import java.net.URISyntaxException;
import java.util.UUID;

import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.ProblemJson;
import it.govpay.gpd.client.beans.PaymentPositionModel.StatusEnum;

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
	
	public static ResponseEntity<ProblemJson> creaResponseKo(InvocationOnMock invocation, HttpStatus httpStatus) throws URISyntaxException {
		String xRequestId = invocation.getArgument(2);
		
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
		
		ResponseEntity<ProblemJson> mockResponseEntity = new ResponseEntity<>(response, getHeadersProblem(xRequestId), httpStatus);
		return mockResponseEntity;
	}
	
	private static ProblemJson createProblemFromOpenAPI(String title, String detail, int status, String type) throws URISyntaxException {
		ProblemJson problem = new ProblemJson();
	    problem.setStatus(status);
	    problem.setTitle(title);
	    problem.setDetail(detail);
//	    problem.setType(new URI(type));
	    return problem;
	}

	public static ProblemJson createProblem400() throws URISyntaxException {
	    return createProblemFromOpenAPI("Bad Request", "Missing required field.", 400, "https://www.rfc-editor.org/rfc/rfc9110.html#name-400-bad-request");
	}

	public static ProblemJson createProblem401() throws URISyntaxException {
	    return createProblemFromOpenAPI("Unauthorized", "Invalid Credentials", 401, "https://www.rfc-editor.org/rfc/rfc9110.html#name-401-unauthorized");
	}

	public static ProblemJson createProblem403() throws URISyntaxException {
	    return createProblemFromOpenAPI("Forbidden", "User not authorized for the operation", 403, "https://www.rfc-editor.org/rfc/rfc9110.html#name-403-forbidden");
	}

	public static ProblemJson createProblem404() throws URISyntaxException {
	    return createProblemFromOpenAPI("Not Found", "Resource not found", 404, "https://www.rfc-editor.org/rfc/rfc9110.html#name-404-not-found");
	}

	public static ProblemJson createProblem409() throws URISyntaxException {
	    return createProblemFromOpenAPI("Conflict", "Resource with the same id already present", 409, "https://www.rfc-editor.org/rfc/rfc9110.html#name-409-conflict");
	}

	public static ProblemJson createProblem429() throws URISyntaxException {
	    return createProblemFromOpenAPI("Too Many Requests", "User has sent too many requests in a given amount of time", 429, "https://www.rfc-editor.org/rfc/rfc6585#section-4");
	}

	public static ProblemJson createProblem503() throws URISyntaxException {
	    return createProblemFromOpenAPI("Service Unavailable", "Request Can't be fulfilled at the moment", 503, "https://www.rfc-editor.org/rfc/rfc9110.html#name-503-service-unavailable");
	}
	
	public static ProblemJson createProblem500() throws URISyntaxException {
		return createProblemFromOpenAPI("Internal Server Error", "Internal Server Error", 500, "https://www.rfc-editor.org/rfc/rfc9110.html#name-500-internal-server-error");
	}
}
