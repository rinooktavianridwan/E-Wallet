package com.ewallet.wallet.repository;

import com.ewallet.entity.Wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

	Optional<Wallet> findByUserId(Long userId);

	/**
	 * Locks the given wallets (SELECT ... FOR UPDATE), ordered by id.
	 * Always locking in ascending id order prevents deadlocks between concurrent transfers.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Wallet w where w.id in :ids order by w.id asc")
	List<Wallet> lockAllByIdOrdered(@Param("ids") List<Long> ids);
}
