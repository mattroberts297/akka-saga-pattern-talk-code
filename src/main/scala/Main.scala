import scala.io.StdIn
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.example.MicroService
import org.slf4j.LoggerFactory

object Main extends App {
  implicit val system = ActorSystem("application")
  implicit val materializer = ActorMaterializer()
  implicit val context = system.dispatcher

  val log = LoggerFactory.getLogger("Main")
  val emailRegion = EmailRegionFactory(system)
  val userRegion = UserRegionFactory(system, emailRegion)
  val userRoutes = UserRoutes(userRegion, emailRegion)

  MicroService.bind(userRoutes, "localhost", 8080).onComplete {
    case Success(binding) =>
      log.info("Started, press any key to stop")
      StdIn.readLine()
      binding.unbind().onComplete {
        case _ => system.terminate().onComplete {
          case _ => log.info("Stopped")
        }
      }
    case Failure(exception) =>
      log.error("Failed to start", exception)
  }
}
