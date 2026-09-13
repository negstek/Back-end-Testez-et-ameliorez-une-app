package com.openclassrooms.etudiant.cucumber.unit;

import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.UserRepository;
import com.openclassrooms.etudiant.service.JwtService;
import com.openclassrooms.etudiant.service.UserService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Step definitions for user_registration.feature. Cucumber instantiates this class fresh for every
// scenario (no dependency-injection framework needed for a single glue class), so the mocks and
// UserService below start clean each time — there is no @BeforeEach to wire, the constructor does it.
public class UserRegistrationSteps {

    private final UserRepository userRepository = mock(UserRepository.class);
    // A real BCrypt encoder rather than a mock: the "password is not the plain text" and "wrong
    // password rejected" scenarios only mean something if the password is genuinely hashed/compared.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = mock(JwtService.class);
    private final UserService userService = new UserService(userRepository, passwordEncoder, jwtService);

    // register() mutates the User it receives in place (it sets the encoded password on it) and
    // returns void, so this field — not a return value — is what the "Then" steps below inspect.
    private User registeredUser;
    private Exception thrown;

    @Given("no user is registered with the login {string}")
    public void no_user_is_registered_with_the_login(String login) {
        when(userRepository.findByLogin(login)).thenReturn(Optional.empty());
    }

    @Given("a user is already registered with the login {string}")
    public void a_user_is_already_registered_with_the_login(String login) {
        User existing = new User();
        existing.setLogin(login);
        existing.setPassword("irrelevant-for-this-scenario");
        when(userRepository.findByLogin(login)).thenReturn(Optional.of(existing));
    }

    @Given("a user is registered with the login {string} and password {string}")
    public void a_user_is_registered_with_the_login_and_password(String login, String password) {
        User existing = new User();
        existing.setLogin(login);
        existing.setPassword(passwordEncoder.encode(password));
        when(userRepository.findByLogin(login)).thenReturn(Optional.of(existing));
    }

    @When("I register a new user with login {string} and password {string}")
    public void i_register_a_new_user_with_login_and_password(String login, String password) {
        registeredUser = new User();
        registeredUser.setLogin(login);
        registeredUser.setPassword(password);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        try {
            userService.register(registeredUser);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("I log in with login {string} and password {string}")
    public void i_log_in_with_login_and_password(String login, String password) {
        try {
            userService.login(login, password);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the registration succeeds")
    public void the_registration_succeeds() {
        assertThat(thrown).isNull();
    }

    @Then("the stored password is not the plain text {string}")
    public void the_stored_password_is_not_the_plain_text(String plainText) {
        assertThat(registeredUser.getPassword()).isNotEqualTo(plainText);
    }

    @Then("the registration is rejected")
    public void the_registration_is_rejected() {
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Then("the login is rejected")
    public void the_login_is_rejected() {
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
