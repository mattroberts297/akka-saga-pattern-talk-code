import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings

object EmailRegionFactory {
  type EmailRegion = ActorRef
  def apply(system: ActorSystem): EmailRegion =
    ClusterSharding(system).start(
      typeName = "email",
      entityProps = Props[EmailActor],
      settings = ClusterShardingSettings(system),
      extractEntityId = EmailActor.idExtractor,
      extractShardId = EmailActor.shardResolver
    )
}
