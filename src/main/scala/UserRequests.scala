object UserRequests {
  final case class RegistrationRequest(email: String, password: String)
  final case class ChangeEmailRequest(oldEmail: String, newEmail: String)
}
