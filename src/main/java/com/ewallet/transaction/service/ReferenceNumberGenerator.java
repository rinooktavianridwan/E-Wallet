package com.ewallet.transaction.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class ReferenceNumberGenerator {

	// contoh: TRX-20261018-3F9A1C07BE
	public String generate() {
		String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
		String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
		return "TRX-" + date + "-" + random;
	}
}
