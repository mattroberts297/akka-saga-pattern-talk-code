import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64

object Secure {
  def salt(): String = {
    val bytes = new Array[Byte](256)
    new SecureRandom().nextBytes(bytes)
    Base64.getUrlEncoder.encodeToString(bytes)
  }

  def hash(salt: String, password: String): String = {
    // TODO: Hash the password properly
    Base64.getUrlEncoder.encodeToString(
      MessageDigest.getInstance("SHA-256").digest(s"$salt$password".getBytes)
    )
  }
}
