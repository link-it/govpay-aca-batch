package it.govpay.gpd.test.utils;

import java.time.OffsetDateTime;

import org.mockito.invocation.InvocationOnMock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import it.govpay.gpd.client.beans.PaymentPositionModel;
import it.govpay.gpd.client.beans.PaymentPositionModel.StatusEnum;

public class PaymentPositionModelUtils {
	
	private PaymentPositionModelUtils() {}
	
	public static ResponseEntity<PaymentPositionModel> creaResponseCreatePaymentPositionModelOk(InvocationOnMock invocation) {
		PaymentPositionModel paymentPositionModel = invocation.getArgument(1);
		String xRequestId = invocation.getArgument(2);
		Boolean toPublish = invocation.getArgument(3);
		StatusEnum status = PaymentPositionModelUtils.getStatus(toPublish);
		
		PaymentPositionModel response = PaymentPositionModelUtils.createPaymentPositionModelResponse(paymentPositionModel, null, status);
		
		ResponseEntity<PaymentPositionModel> mockResponseEntity = new ResponseEntity<>(response, GpdUtils.getHeadersCreatedOk(xRequestId), HttpStatus.CREATED);
		return mockResponseEntity;
	}
	
	public static StatusEnum getStatus(Boolean toPublish) {
		if(toPublish != null && toPublish.booleanValue())
			return StatusEnum.VALID;
		
		return StatusEnum.DRAFT;
	}
	
	public static PaymentPositionModel createPaymentPositionModelResponse(PaymentPositionModel request, OffsetDateTime paymentDate, StatusEnum status) {
		PaymentPositionModel response = new PaymentPositionModel(paymentDate, status);

		response.setAca(request.isAca()); 
		response.setCity(request.getCity());
		response.setCivicNumber(request.getCivicNumber());
		response.setCompanyName(request.getCompanyName());
		response.setCountry(request.getCountry());
		response.setEmail(request.getEmail());
		response.setFiscalCode(request.getFiscalCode());
		response.setFullName(request.getFullName());
		response.setIupd(request.getIupd());
		response.setOfficeName(request.getOfficeName());
		response.setPaymentOption(request.getPaymentOption());
		response.setPayStandIn(request.isPayStandIn());
		response.setPhone(request.getPhone());
		response.setPostalCode(request.getPostalCode());
		response.setProvince(request.getProvince());
		response.setRegion(request.getRegion());
		response.setStreetName(request.getStreetName());
		response.setSwitchToExpired(request.isSwitchToExpired());
		response.setType(request.getType());
		response.setValidityDate(request.getValidityDate());
		
		return response;
	}
}
