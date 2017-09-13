import java.util.UUID

import scala.concurrent.duration._

import UserActor.UserInfo
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ReceiveTimeout
import akka.cluster.sharding.ShardRegion
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import akka.persistence.SnapshotMetadata
import akka.persistence.SnapshotOffer

class EmailActor extends PersistentActor with ActorLogging {
  import EmailActor._
  import ShardRegion.Passivate

  // TODO put 30 seconds in configuration.
  context.setReceiveTimeout(30.seconds)

  var maybeState: Option[EmailState] = None

  var snapshotMetadata: Option[SnapshotMetadata] = None

  override def persistenceId: String = s"email-${self.path.name}"

  override def receiveRecover: Receive = LoggingReceive {
    case event: EmailEvent =>
      update(event)
    case offer: SnapshotOffer =>
      restore(offer)
  }

  override def receiveCommand: Receive = LoggingReceive {
    case EmailCommand.CreateEmail(email, deliveryId, client, userInfo) =>
      maybeState match {
        case Some(state) =>
          if (state.userId == userInfo.userId) {
            log.info(s"Duplicate create command for: ${email} -> ${userInfo.userId}")
            sender() ! EmailReply.EmailCreated(deliveryId, client, userInfo)
          } else {
            log.info(s"Bad create command for: ${email} -> ${userInfo.userId}. See ${state.userId}")
            sender() ! EmailReply.EmailAlreadyExists(deliveryId, client, userInfo)
          }
        case None =>
          persist(EmailEvent.EmailCreated(userInfo.userId, email)) { event =>
            log.info(s"Create command for: ${email} -> ${userInfo.userId}")
            update(event)
            sender() ! EmailReply.EmailCreated(deliveryId, client, userInfo)
          }
      }
    case EmailCommand.DeleteEmail(email, deliveryId, client, userInfo) =>
      maybeState match {
        case None =>
          log.info(s"Duplicate delete command for: ${email} -> ${userInfo.userId}")
          sender() ! EmailReply.EmailDeleted(deliveryId, client, userInfo)
        case Some(state) =>
          // TODO Use a better error than EmailAlreadyExists.
          if (state.userId != userInfo.userId) {
            log.info(s"Bad delete command for: ${email} -> ${userInfo.userId}. See ${state.userId}")
            sender() ! EmailReply.EmailAlreadyExists(deliveryId, client, userInfo)
          } else {
            persist(EmailEvent.EmailDeleted(userInfo.userId, email)) { event =>
              log.info(s"Delete command for: ${email} -> ${userInfo.userId}")
              update(event)
              sender() ! EmailReply.EmailDeleted(deliveryId, client, userInfo)
            }
          }
      }
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = Stop)
    case Stop =>
      context.stop(self)
  }

  def update(event: EmailEvent): Unit = {
    log.debug(s"update($event)")
    event match {
      case EmailEvent.EmailCreated(userId, email) =>
        maybeState = Some(EmailState(userId, email))
      case EmailEvent.EmailDeleted(_, _) =>
        maybeState = None
    }
  }

  def restore(offer: SnapshotOffer): Unit = {
    log.debug(s"restore($offer)")
    snapshotMetadata = Some(offer.metadata)
    offer.snapshot match {
      case Some(state: EmailState) => maybeState = Some(state)
    }
  }
}

object EmailActor {
  type Email = String
  type UserId = UUID
  type DeliveryId = Long

  // TODO Investigate use of Persistent FSM in UserActor.
  // OK. So in hindsight, putting everything in messages is pretty ugly.
  // The email actor really should have to know about RegistrationInfo.
  sealed trait EmailReply
  object EmailReply {
    final case class EmailAlreadyExists(deliveryId: DeliveryId, client: ActorRef, userInfo: UserInfo) extends EmailReply
    final case class EmailCreated(deliveryId: DeliveryId, client: ActorRef, userInfo: UserInfo) extends EmailReply
    final case class EmailDeleted(deliveryId: DeliveryId, client: ActorRef, userInfo: UserInfo) extends EmailReply
  }

  sealed trait EmailCommand {
    def email: Email
  }
  object EmailCommand {
    final case class CreateEmail(email: Email, deliveryId: DeliveryId, client: ActorRef, userInfo: UserInfo) extends EmailCommand
    final case class DeleteEmail(email: Email, deliveryId: DeliveryId, client: ActorRef, userInfo: UserInfo) extends EmailCommand
  }

  sealed trait EmailEvent
  object EmailEvent {
    final case class EmailCreated(userId: UserId, email: Email) extends EmailEvent
    final case class EmailDeleted(userId: UserId, email: Email) extends EmailEvent
  }

  case object Stop

  case class EmailState(userId: UserId, email: Email)

  val idExtractor: ShardRegion.ExtractEntityId = {
    case c: EmailCommand => (c.email, c)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    // TODO put modulo 50 in configuration.
    case c: EmailCommand => (math.abs(c.email.hashCode) % 50).toString
  }
}
