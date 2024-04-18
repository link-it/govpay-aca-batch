package it.govpay.aca.test.utils;

import java.net.URI;
import java.net.URISyntaxException;

import it.govpay.gde.client.beans.Problem;

public class GdeProblemUtils {
	
	private static Problem createProblemFromOpenAPI(String title, String detail, int status, String type) throws URISyntaxException {
	    Problem problem = new Problem();
	    problem.setStatus(status);
	    problem.setTitle(title);
	    problem.setDetail(detail);
	    problem.setType(new URI(type));
	    return problem;
	}

	public static Problem createProblem400() throws URISyntaxException {
	    return createProblemFromOpenAPI("Bad Request", "Missing required field.", 400, "https://www.rfc-editor.org/rfc/rfc9110.html#name-400-bad-request");
	}

	public static Problem createProblem401() throws URISyntaxException {
	    return createProblemFromOpenAPI("Unauthorized", "Invalid Credentials", 401, "https://www.rfc-editor.org/rfc/rfc9110.html#name-401-unauthorized");
	}

	public static Problem createProblem403() throws URISyntaxException {
	    return createProblemFromOpenAPI("Forbidden", "User not authorized for the operation", 403, "https://www.rfc-editor.org/rfc/rfc9110.html#name-403-forbidden");
	}

	public static Problem createProblem404() throws URISyntaxException {
	    return createProblemFromOpenAPI("Not Found", "Resource not found", 404, "https://www.rfc-editor.org/rfc/rfc9110.html#name-404-not-found");
	}

	public static Problem createProblem409() throws URISyntaxException {
	    return createProblemFromOpenAPI("Conflict", "Resource with the same id already present", 409, "https://www.rfc-editor.org/rfc/rfc9110.html#name-409-conflict");
	}

	public static Problem createProblem429() throws URISyntaxException {
	    return createProblemFromOpenAPI("Too Many Requests", "User has sent too many requests in a given amount of time", 429, "https://www.rfc-editor.org/rfc/rfc6585#section-4");
	}

	public static Problem createProblem503() throws URISyntaxException {
	    return createProblemFromOpenAPI("Service Unavailable", "Request Can't be fulfilled at the moment", 503, "https://www.rfc-editor.org/rfc/rfc9110.html#name-503-service-unavailable");
	}
}
