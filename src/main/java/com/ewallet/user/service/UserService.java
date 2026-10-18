package com.ewallet.user.service;

import com.ewallet.common.exception.UserNotFoundException;
import com.ewallet.entity.User;
import com.ewallet.user.dto.UserResponse;
import com.ewallet.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public UserResponse getById(Long id) {
		User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
	}
}
