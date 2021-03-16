import GuessHandler.GEvent
import Player.PEvent
import Game.GameEvent
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

final case class PRef(parent : ActorRef[GameEvent],
                      next : ActorRef[PEvent],
                      coinsh : ActorRef[DrawCoinsHandler.DCEvent],
                      table : ActorRef[GEvent])

final case class PConf(id: Int, init : Boolean, nump : Int, refs : PRef)


object Game {

  sealed trait GameEvent
  final case class Start(numPlayers : Int) extends GameEvent
  final case class Create(totPlayers : Int) extends GameEvent
  final case class End(looserId : Int) extends GameEvent

  def apply(): Behavior[GameEvent] = waitToStart()

  def waitToStart() : Behavior[GameEvent] = {
    Behaviors.receive((context, message) =>
      message match {
        case Start(numPlayers) if numPlayers <= 1 =>
          Behaviors.same
        case Start(numPlayers) if numPlayers > 1 =>
          context.self ! Create(numPlayers)
          creation(List.empty)
      })
  }

  def creation(players : List[ActorRef[PEvent]]) : Behavior[GameEvent] = {
    Behaviors.receive((context, message) =>
      message match {
        case Create(totPlayers) if players.length < totPlayers =>
          context.self ! Create(totPlayers)

          val id = players.length + 1
          val isInit = players.length == 0 //convention: the initializator is the first created actor
          val conf = PConf(id, isInit, totPlayers, null)
          val newPlayer = context.spawn(Player(conf), s"$id")

          creation(players.appended(newPlayer) )

        case Create(totPlayers) if players.length == totPlayers =>
          //Create CoinsReqHandler actor
          val coinsh = context.spawn(DrawCoinsHandler(players), "coinsh")
          //Create GuessTable actor
          val table = context.spawn(GuessHandler(players), "guessh")

          //Connect Actors in a ring topology
          for (i <- 0 to (players.length-1)) {
            val next = players((i+1) % players.length)
            players(i) ! Player.Connect( PRef(context.self, next, coinsh, table) )
          }

          players(0) ! Player.StartRound()
          waitEnd()
      })
  }

  def waitEnd() : Behavior[GameEvent] = {
    Behaviors.receiveMessage(message =>
      message match {
        case End(looserId) =>
          println(s"$looserId, you have to pay!")
          println("END")
          Behaviors.stopped
      })
  }
}

object SpoofGame extends App {
  val system: ActorSystem[GameEvent] =
    ActorSystem(Game(), "spoofGame")
  system ! Game.Start(5)
}