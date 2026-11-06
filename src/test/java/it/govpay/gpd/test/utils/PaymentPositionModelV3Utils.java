package it.govpay.gpd.test.utils;

import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelResponseV3.StatusEnum;
import it.govpay.gpd.client.v3.beans.PaymentPositionModelV3;

/** Fixture per le risposte mock del client GPD v3, stesso stile di {@link PaymentPositionModelUtils}. */
public class PaymentPositionModelV3Utils {

	private PaymentPositionModelV3Utils() {}

	public static ResponseEntity<PaymentPositionModelV3> creaResponseCreateOk(InvocationOnMock invocation) {
		PaymentPositionModelV3 request = invocation.getArgument(1);
		String xRequestId = invocation.getArgument(3);
		return new ResponseEntity<>(request, GpdUtils.getHeadersCreatedOk(xRequestId), HttpStatus.CREATED);
	}

	public static ResponseEntity<PaymentPositionModelV3> creaResponseUpdateOk(InvocationOnMock invocation) {
		PaymentPositionModelV3 request = invocation.getArgument(2);
		String xRequestId = invocation.getArgument(4);
		return new ResponseEntity<>(request, GpdUtils.getHeadersCreatedOk(xRequestId), HttpStatus.OK);
	}

	public static ResponseEntity<PaymentPositionModelResponseV3> creaResponseGetPositionOk(InvocationOnMock invocation, StatusEnum statusEnum) {
		String codDominio = invocation.getArgument(0);
		String iupd = invocation.getArgument(1);
		String xRequestId = invocation.getArgument(2);

		PaymentPositionModelResponseV3 response = new PaymentPositionModelResponseV3();
		response.setOrganizationFiscalCode(codDominio);
		response.setIupd(iupd);
		response.setStatus(statusEnum);

		return new ResponseEntity<>(response, GpdUtils.getHeadersCreatedOk(xRequestId), HttpStatus.OK);
	}

	public static ResponseEntity<PaymentPositionModelV3> creaResponsePublishOk(InvocationOnMock invocation) {
		String iupd = invocation.getArgument(1);
		String xRequestId = invocation.getArgument(2);

		PaymentPositionModelV3 response = new PaymentPositionModelV3();
		response.setIupd(iupd);

		return new ResponseEntity<>(response, GpdUtils.getHeadersCreatedOk(xRequestId), HttpStatus.OK);
	}
}
