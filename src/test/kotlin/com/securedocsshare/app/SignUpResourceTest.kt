import com.securedocsshare.app.api.model.SignUpCompletionRequest
import com.securedocsshare.app.api.model.SignUpInitiateRequest
import com.securedocsshare.app.api.model.SignUpRegenerationRequest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class SignUpResourceTest {

    @BeforeEach
    fun setup() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 8080
    }

    @Test
    fun `test sign-up initiation with valid email`() {
        val request = SignUpInitiateRequest(email = "valid@example.com")
        val response: Response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/auth/sign-up/initiation")

        assertEquals(200, response.statusCode)
        assertEquals("Confirmation link sent", response.jsonPath().getString("message"))
    }

    @Test
    fun `test sign-up initiation with invalid email`() {
        val request = SignUpInitiateRequest(email = "invalid-email")
        val response: Response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/auth/sign-up/initiation")

        assertEquals(400, response.statusCode)
        assertEquals("Email is invalid", response.jsonPath().getString("errorMessage"))
    }

    @Test
    fun `test sign-up initiation with existing email`() {
        val request = SignUpInitiateRequest(email = "existing@example.com")
        val response: Response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/auth/sign-up/initiation")

        assertEquals(400, response.statusCode)
        assertEquals("An account with this email already exists. Please sign in.", response.jsonPath().getString("errorMessage"))
    }

    @Test
    fun `test OTP regeneration`() {
        val request = SignUpRegenerationRequest(email = "valid@example.com")
        val response: Response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/auth/sign-up/otp-regeneration")

        assertEquals(200, response.statusCode)
        assertEquals("Your OTP has been regenerated", response.jsonPath().getString("message"))
    }

    @Test
    fun `test sign-up completion with valid data`() {
        val request = SignUpCompletionRequest(
            email = "valid@example.com",
            otp = "123456",
            password = "StrongPassword1!",
            confirmationPassword = "StrongPassword1!"
        )
        val response: Response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/auth/sign-up/completion")

        assertEquals(200, response.statusCode)
        assertEquals("Sign up successful!", response.jsonPath().getString("message"))
    }

    @Test
    fun `test sign-up completion with invalid OTP`() {
        val request = SignUpCompletionRequest(
            email = "valid@example.com",
            otp = "wrong-otp",
            password = "StrongPassword1!",
            confirmationPassword = "StrongPassword1!"
        )
        val response: Response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/auth/sign-up/completion")

        assertEquals(400, response.statusCode)
        assertEquals("Invalid otp", response.jsonPath().getString("errorMessage"))
    }
}