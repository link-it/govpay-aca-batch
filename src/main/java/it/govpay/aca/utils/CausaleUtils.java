package it.govpay.aca.utils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import it.govpay.aca.entity.causale.Causale;
import it.govpay.aca.entity.causale.CausaleSemplice;
import it.govpay.aca.entity.causale.CausaleSpezzoni;
import it.govpay.aca.entity.causale.CausaleSpezzoniStrutturati;

public class CausaleUtils {
	
	private CausaleUtils() {}
	
	public static Causale decode(String encodedCausale) throws UnsupportedEncodingException {
		if(encodedCausale == null || encodedCausale.trim().isEmpty())
			return null;

		String[] causaleSplit = encodedCausale.split(" ");
		if(causaleSplit[0].equals("01")) {
			CausaleSemplice causale = new CausaleSemplice();
			if(causaleSplit.length > 1 && causaleSplit[1] != null) {
				causale.setCausale(new String(Base64.getDecoder().decode(causaleSplit[1].getBytes()), StandardCharsets.UTF_8));
				return causale;
			} else {
				return null;
			}
		}

		if(causaleSplit[0].equals("02")) {
			List<String> spezzoni = new ArrayList<>();
			for(int i=1; i<causaleSplit.length; i++) {
				spezzoni.add(new String(Base64.getDecoder().decode(causaleSplit[i].getBytes()), StandardCharsets.UTF_8));
			}
			CausaleSpezzoni causale = new CausaleSpezzoni();
			causale.setSpezzoni(spezzoni);
			return causale;
		}

		if(causaleSplit[0].equals("03")) {
			List<String> spezzoni = new ArrayList<>();
			List<BigDecimal> importi = new ArrayList<>();

			for(int i=1; i<causaleSplit.length; i=i+2) {
				spezzoni.add(new String(Base64.getDecoder().decode(causaleSplit[i].getBytes()), StandardCharsets.UTF_8));
				importi.add(BigDecimal.valueOf(Double.parseDouble(new String(Base64.getDecoder().decode(causaleSplit[i+1].getBytes()), StandardCharsets.UTF_8))));
			}
			CausaleSpezzoniStrutturati causale = new CausaleSpezzoniStrutturati();
			causale.setSpezzoni(spezzoni);
			causale.setImporti(importi);
			return causale;
		}
		throw new UnsupportedEncodingException();
	}

}
