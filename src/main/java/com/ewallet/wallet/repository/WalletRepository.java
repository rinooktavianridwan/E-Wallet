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

	@Query("select w.id from Wallet w where w.user.id = :userId")
	Optional<Long> findIdByUserId(@Param("userId") Long userId);

	// SELECT ... FOR UPDATE, harus dipanggil di dalam transaksi
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Wallet> findWithLockByUserId(Long userId);

	// Kunci selalu urut berdasarkan id supaya dua transfer berlawanan arah tidak saling menunggu (deadlock)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Wallet w where w.id in :ids order by w.id asc")
	List<Wallet> lockAllByIdOrdered(@Param("ids") List<Long> ids);
}
