package com.ewallet.wallet.service;

import com.ewallet.common.exception.WalletNotFoundException;
import com.ewallet.entity.Wallet;
import com.ewallet.wallet.dto.WalletResponse;
import com.ewallet.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

	private final WalletRepository walletRepository;

	public WalletService(WalletRepository walletRepository) {
		this.walletRepository = walletRepository;
	}

	@Transactional(readOnly = true)
	public WalletResponse getByUserId(Long userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new WalletNotFoundException(userId));
		return new WalletResponse(wallet.getId(), wallet.getBalance(), wallet.isActive(), wallet.getUpdatedAt());
	}
}
