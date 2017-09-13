import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat

object UserFormats extends DefaultJsonProtocol {
  import UserRequests._
  implicit val registrationRequest: RootJsonFormat[RegistrationRequest] =
    jsonFormat2(RegistrationRequest)
  implicit val changeEmailRequest: RootJsonFormat[ChangeEmailRequest] =
    jsonFormat2(ChangeEmailRequest)
}
