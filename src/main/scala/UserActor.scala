import java.util.UUID

import scala.collection.immutable._
import scala.concurrent.duration._

import EmailActor._
import EmailRegionFactory.EmailRegion
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ReceiveTimeout
import akka.cluster.sharding.ShardRegion
import akka.event.LoggingReceive
import akka.persistence.AtLeastOnceDelivery
import akka.persistence.PersistentActor
import akka.persistence.SnapshotMetadata
import akka.persistence.SnapshotOffer

class UserActor(emailRegion: EmailRegion)
  extends PersistentActor
  with AtLeastOnceDelivery
  with ActorLogging {

  import ShardRegion.Passivate
  import UserActor._

  // TODO put 30 seconds in configuration.
  context.setReceiveTimeout(30.seconds)

  var maybeState: Option[UserState] = None

  var snapshotMetadata: Option[SnapshotMetadata] = None

  override def persistenceId: String = s"user-${self.path.name}"

  override def receiveRecover: Receive = LoggingReceive {
    case event: UserEvent =>
      update(event)
    case offer: SnapshotOffer =>
      restore(offer)
  }

  override def receiveCommand: Receive = LoggingReceive {
    case register: UserCommand.Register =>
      val salt = Secure.salt()
      val hash = Secure.hash(salt, register.password)
      persist(UserEvent.RegistrationStarted(UserInfo.RegistrationInfo(register.userId, register.email, salt, hash)))(update)
    case EmailReply.EmailAlreadyExists(deliveryId, client, UserInfo.RegistrationInfo(userId, email, _, _)) =>
      persist(UserEvent.RegistrationFailed(deliveryId)) { event =>
        log.info(s"Registration failed for: ${email} -> ${userId}")
        update(event)
        client ! UserReply.RegistrationFailed
      }
    case EmailReply.EmailCreated(deliveryId, client, info@UserInfo.RegistrationInfo(userId, email, _, _)) =>
      persist(UserEvent.RegistrationSucceeded(deliveryId, info)) { event =>
        log.info(s"Registration finished for: ${email} -> ${userId}")
        update(event)
        client ! UserReply.RegistrationSucceeded
      }
    case UserCommand.ChangeEmail(info@UserInfo.ChangeEmailInfo(userId, currentEmail, newEmail)) =>
      maybeState match {
        case None =>
          sender() ! UserReply.ChangeEmailFailed
        case Some(_) =>
          persist(UserEvent.ChangeEmailStarted(sender(), info))(update)
      }
    case EmailReply.EmailAlreadyExists(deliveryId, client, info@UserInfo.ChangeEmailInfo(userId, currentEmail, newEmail)) =>
      persist(UserEvent.ChangeEmailFailed(client, deliveryId, info)) { event =>
        update(event)
        client ! UserReply.ChangeEmailFailed
      }
    case EmailReply.EmailCreated(deliveryId, client, info@UserInfo.ChangeEmailInfo(userId, currentEmail, newEmail)) =>
      persist(UserEvent.NewEmailCreated(client, deliveryId, info))(e => update(e, Some(client)))
    case EmailReply.EmailDeleted(deliveryId, client, info@UserInfo.ChangeEmailInfo(userId, currentEmail, newEmail)) =>
      persistAll(Seq(UserEvent.OldEmailDeleted(client, deliveryId, info), UserEvent.ChangeEmailSucceeded(client, deliveryId, info))) {
        case event: UserEvent.OldEmailDeleted =>
          update(event)
        case event: UserEvent.ChangeEmailSucceeded =>
          update(event)
          client ! UserReply.ChangeEmailSucceeded
      }
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = Stop)
    case Stop =>
      context.stop(self)
  }

  // Now that I think about it, we should really be persisting the ActorRef for proper recovery.
  // I mean in our particular instance we know the client is unlikely to be there anymore.
  def update(event: UserEvent, client: Option[ActorRef]): Unit = {
    log.debug(s"update($event)")
    event match {
      case UserEvent.RegistrationStarted(info) =>
        deliver(emailRegion.path)(deliveryId => EmailCommand.CreateEmail(info.email, deliveryId, sender(), info))
      case UserEvent.RegistrationFailed(deliveryId) =>
        confirmDelivery(deliveryId)
      case UserEvent.RegistrationSucceeded(deliveryId, UserInfo.RegistrationInfo(userId, email, salt, hash)) =>
        confirmDelivery(deliveryId)
        maybeState = Some(UserState(userId, email, salt, hash))
      case UserEvent.ChangeEmailStarted(client, info) =>
        deliver(emailRegion.path)(deliveryId => EmailCommand.CreateEmail(info.newEmail, deliveryId, client, info))
      case UserEvent.ChangeEmailFailed(client, deliveryId, info) =>
        confirmDelivery(deliveryId)
      case UserEvent.NewEmailCreated(client, deliveryId, info) =>
        confirmDelivery(deliveryId)
        deliver(emailRegion.path)(deliveryId => EmailCommand.DeleteEmail(info.oldEmail, deliveryId, client, info))
      case UserEvent.OldEmailDeleted(client, deliveryId, info) =>
        confirmDelivery(deliveryId)
      case UserEvent.ChangeEmailSucceeded(client, deliveryId, info) =>
        maybeState.map(state => state.copy(email = info.newEmail))
    }
  }

  // During recovery, only messages with an un-confirmed delivery id will be resent.
  def update(event: UserEvent): Unit = update(event, None)

  def restore(offer: SnapshotOffer): Unit = {
    log.debug(s"restore($offer)")
    snapshotMetadata = Some(offer.metadata)
    offer.snapshot match {
      case Some(state: UserState) => maybeState = Some(state)
    }
  }
}

object UserActor {
  type Email = String
  type UserId = UUID

  sealed trait UserReply
  object UserReply {
    final case object RegistrationFailed extends UserReply
    final case object RegistrationSucceeded extends UserReply
    final case object ChangeEmailFailed extends UserReply
    final case object ChangeEmailSucceeded extends UserReply
  }

  sealed trait UserCommand { def userId: UserId }
  object UserCommand {
    final case class Register(userId: UserId, email: String, password: String) extends UserCommand
    final case class ChangeEmail(info: UserInfo.ChangeEmailInfo) extends UserCommand {
      def userId = info.userId
    }
  }

  sealed trait UserEvent
  object UserEvent {
    final case class RegistrationStarted(registrationInfo: UserInfo.RegistrationInfo) extends UserEvent
    final case class RegistrationFailed(deliveryId: DeliveryId) extends UserEvent
    final case class RegistrationSucceeded(deliveryId: DeliveryId, registrationInfo: UserInfo.RegistrationInfo) extends UserEvent

    // Create new email address then delete old email address (don't stop for pedestrians).
    final case class ChangeEmailStarted(client: ActorRef, changeEmailInfo: UserInfo.ChangeEmailInfo) extends UserEvent
    final case class NewEmailCreated(client: ActorRef, deliveryId: DeliveryId, changeEmailInfo: UserInfo.ChangeEmailInfo) extends UserEvent
    final case class OldEmailDeleted(client: ActorRef, deliveryId: DeliveryId, changeEmailInfo: UserInfo.ChangeEmailInfo) extends UserEvent
    final case class ChangeEmailSucceeded(client: ActorRef, deliveryId: DeliveryId, changeEmailInfo: UserInfo.ChangeEmailInfo) extends UserEvent
    final case class ChangeEmailFailed(client: ActorRef, deliveryId: DeliveryId, changeEmailInfo: UserInfo.ChangeEmailInfo) extends UserEvent
  }

  sealed trait UserInfo { def userId: UserId }
  object UserInfo {
    final case class RegistrationInfo(userId: UserId, email: Email, salt: String, hash: String) extends UserInfo
    final case class ChangeEmailInfo(userId: UserId, oldEmail: Email, newEmail: Email) extends UserInfo
  }

  case object Stop

  case class UserState(id: UserId, email: Email, salt: String, hash: String)

  val idExtractor: ShardRegion.ExtractEntityId = {
    case c: UserCommand => (c.userId.toString, c)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    // TODO put modulo 50 in configuration.
    case c: UserCommand => (math.abs(c.userId.hashCode) % 50).toString
  }
}
