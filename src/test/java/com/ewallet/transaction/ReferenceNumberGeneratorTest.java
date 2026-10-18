package com.ewallet.transaction;

import com.ewallet.transaction.service.ReferenceNumberGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceNumberGeneratorTest {

	private final ReferenceNumberGenerator generator = new ReferenceNumberGenerator();

	@Test
	void generate_followsExpectedFormat() {
		assertThat(generator.generate()).matches("^TRX-\\d{8}-[A-Z0-9]{10}$");
	}

	@Test
	void generate_producesUniqueValues() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			seen.add(generator.generate());
		}

		assertThat(seen).hasSize(1000);
	}
}
