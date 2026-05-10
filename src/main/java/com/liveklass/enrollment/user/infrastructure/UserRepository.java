package com.liveklass.enrollment.user.infrastructure;

import com.liveklass.enrollment.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
