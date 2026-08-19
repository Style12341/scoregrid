package com.scoregrid.auth;

import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase;
import com.scoregrid.auth.auth.domain.port.in.AuthenticateUserUseCase.LoginCommand;
import com.scoregrid.auth.auth.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "scoregrid.admin.password=correct-horse-battery")
class AuthServiceApplicationTests {

	@Autowired
	private UserRepositoryPort users;

	@Autowired
	private AuthenticateUserUseCase authenticateUser;

	@Test
	void contextLoads() {
	}

	@Test
	void createsAnAdminAccountOnStartup() {
		User admin = users.findByUsernameOrEmail("admin").orElseThrow();

		assertThat(admin.roles()).containsExactlyInAnyOrder(Role.PLAYER, Role.ADMIN);
		assertThat(authenticateUser.authenticate(new LoginCommand("admin", "correct-horse-battery"))
				.user().id()).isEqualTo(admin.id());
	}

}
