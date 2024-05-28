package it.govpay.aca.test.utils;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.util.MultiValueMap;

import it.govpay.aca.client.beans.ProblemJson;

public class AcaUtils {
	
	public static MultiValueMap<String, String> getHeadersCreatedOk() {
		MultiValueMap<String, String> headers = new org.springframework.util.LinkedMultiValueMap<>();
		headers.add("Content-Length", "0");
		return headers;
	}
	
	public static MultiValueMap<String, String> getHeadersProblem() {
		MultiValueMap<String, String> headers = new org.springframework.util.LinkedMultiValueMap<>();
		headers.add("Content-Length", "100");
		headers.add("Content-Type", "application/problem+json");
		return headers;
	}
	
	
	private static ProblemJson createProblemFromOpenAPI(String title, String detail, int status, String type) throws URISyntaxException {
		ProblemJson problem = new ProblemJson();
	    problem.setStatus(status);
	    problem.setTitle(title);
	    problem.setDetail(detail);
	    problem.setType(new URI(type));
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
