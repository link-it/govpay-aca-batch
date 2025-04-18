package it.govpay.gpd.utils;

public class IuvUtils {
	
	private IuvUtils() {}
	
	public static boolean checkISO11640(String iuv){
		return iuv.startsWith("RF");
	}

}
