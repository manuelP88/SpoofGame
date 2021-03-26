import Player.PEvent
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object DrawCoinsHandler {

  sealed trait DCEvent
  final case class StartDrawCoins(replyTo : ActorRef[PEvent]) extends DCEvent
  final case class DrawCoinsOk() extends DCEvent

  def apply(players : List[ActorRef[PEvent]]) : Behavior[DCEvent] = {
    idle(players, players.length)
  }

  def idle(players : List[ActorRef[PEvent]], totResp : Int) : Behavior[DCEvent] = {
    Behaviors.receive((context, message) => {
      message match {
        case StartDrawCoins(replyTo) =>
          players.foreach(_ ! Player.DrawCoinsReq(context.self))
          waitAllDraws(players, replyTo, totResp, totResp)
      }
    })
  }

  def waitAllDraws(players : List[ActorRef[PEvent]], replyTo : ActorRef[PEvent], totResp : Int, missing : Int) : Behavior[DCEvent] = {
    Behaviors.receiveMessage(message => {
      message match {
        case DrawCoinsOk() if missing > 1 =>
          waitAllDraws(players, replyTo, totResp, missing - 1)
        case DrawCoinsOk() if missing == 1 =>
          println("[DrawCoinsHandler] Ok! All players have drawn the coins!")
          replyTo ! Player.DrawCoinsEnd()
          idle(players, totResp-1)
      }
    })
  }
}
