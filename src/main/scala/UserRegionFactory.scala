import EmailRegionFactory.EmailRegion
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings

object UserRegionFactory {
  type UserRegion = ActorRef
  def apply(system: ActorSystem, emailRegion: EmailRegion): UserRegion =
    ClusterSharding(system).start(
      typeName = "user",
      entityProps = Props(classOf[UserActor], emailRegion),
      settings = ClusterShardingSettings(system),
      extractEntityId = UserActor.idExtractor,
      extractShardId = UserActor.shardResolver
    )
}
