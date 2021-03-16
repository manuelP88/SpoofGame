import GuessHandler.GEvent
import Player.PEvent
import Game.GameEvent
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.util.Random

final case class PRef(parent : ActorRef[GameEvent],
                      next : ActorRef[PEvent],
                      coinsh : ActorRef[DrawCoinsHandler.DCEvent],
                      table : ActorRef[GEvent])

final case class PConf(id: Int, init : Boolean, nump : Int, refs : PRef)

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
          replyTo ! Player.DrawCoinsEnd()
          idle(players, totResp-1)
      }
    })
  }
}

object GuessHandler {

  sealed trait GEvent
  final case class SendGuess(id : Int, myguess : Int, replyTo : ActorRef[PEvent]) extends GEvent
  final case class ComputeTheResult(replyTo : ActorRef[PEvent]) extends GEvent
  final case class HereMyCoins(coins : Int) extends GEvent

  private def alreadyChoosen(guesses : Map[Int, Int], guess : Int) : Boolean = guesses.contains(guess)
  private def returnTheWinner(guesses : Map[Int, Int], coinsSum : Int) : Int = {
    val minDiff = guesses.keys.minByOption(x=>(coinsSum-x).abs).get
    guesses.get(minDiff).get
  }

  def apply(players : List[ActorRef[PEvent]]) : Behavior[GEvent] =
    collectAllGuesses(players, Map.empty[Int, Int])

  def collectAllGuesses(players : List[ActorRef[PEvent]], guesses : Map[Int, Int]) : Behavior[GEvent] = {
    Behaviors.receiveMessage(message =>
    message match {
      case SendGuess(_, guess, replyTo) if alreadyChoosen(guesses, guess) =>
        replyTo ! Player.SendGuessResp(true) //Retry
        collectAllGuesses(players, guesses)
      case SendGuess(id, guess, replyTo) if !alreadyChoosen(guesses, guess) =>
        replyTo ! Player.SendGuessResp(false) //Unblock next guess
        collectAllGuesses(players, guesses + (guess -> id))
      case ComputeTheResult(replyTo) =>//caming from initiator
        players.foreach(_ ! Player.GiveMeYourCoins())
        waitToComputeTheWinner(players, guesses, List.empty[Int], replyTo)
    })
  }

  def waitToComputeTheWinner(players : List[ActorRef[PEvent]], guesses : Map[Int, Int], coinsList : List[Int], replyTo : ActorRef[PEvent]) : Behavior[GuessHandler.GEvent] = {
    Behaviors.receive((context, message) =>
    message match {
      case GuessHandler.HereMyCoins(mycoins)  if coinsList.length+1 < guesses.size =>
        waitToComputeTheWinner(players, guesses, coinsList :+ mycoins, replyTo)
      case HereMyCoins(mycoins) if coinsList.length+1 == guesses.size=>
        val allCoins = coinsList :+ mycoins
        val winnerId = returnTheWinner(guesses, allCoins.sum)
        val msg = if (allCoins.length== 2) Player.EndGame(winnerId) else Player.EndRound(winnerId)
        val pl = allCoins.length
        println(s"[RES] #players=$pl winnerID=$winnerId #coins=${allCoins.sum}")
        replyTo ! msg
        collectAllGuesses(players, Map.empty[Int, Int])
    })
  }
}

object Player {

  sealed trait PEvent
  final case class Connect(refs : PRef) extends PEvent
  final case class StartRound() extends PEvent
  final case class DrawCoinsReq(replyTo : ActorRef[DrawCoinsHandler.DCEvent]) extends PEvent
  final case class DrawCoinsEnd() extends PEvent
  final case class PlayYourTurn() extends PEvent
  final case class SendGuessResp(alreadyChoosen : Boolean) extends PEvent
  final case class GiveMeYourCoins() extends PEvent
  final case class EndRound(winnerId : Int) extends PEvent
  final case class EndGame(winnerId : Int) extends PEvent
  final case class RoundResult(winnerId : Int, initAlreadyChosen : Boolean) extends PEvent

  def apply(conf : PConf): Behavior[PEvent] = {
    Behaviors.receiveMessage(message =>
      message match {
        case Connect(refs) =>
          val newConf = PConf(conf.id, conf.init, conf.nump, refs)
          waitDrawingCoins(newConf)
      })
  }

  def waitDrawingCoins(conf : PConf): Behavior[PEvent] = {
    Behaviors.receive((context, message) =>
      message match {
        case StartRound() if conf.init =>
          println(s"[${conf.id}] I'm INIT, get start!")
          conf.refs.coinsh ! DrawCoinsHandler.StartDrawCoins(context.self)
          Behaviors.same
        case DrawCoinsReq(replyTo) =>
          println(s"[${conf.id}] draw coin")
          replyTo ! DrawCoinsHandler.DrawCoinsOk()
          guessing(conf, Random.between(0, 3))
      })
  }

  def guessing(conf : PConf, coins : Int) : Behavior[PEvent] = {
    Behaviors.receive((context, message) =>
      message match {
        case DrawCoinsEnd() if conf.init =>
          val guess = Random.between(0, 3*conf.nump)
          println(s"[${conf.id}] I'm INIT, stop draw but my guess is $guess!")
          conf.refs.table ! GuessHandler.SendGuess(conf.id, guess, context.self)
          guessing(conf, coins)
        case PlayYourTurn() =>
          val guess = Random.between(0, 3*conf.nump)
          println(s"[${conf.id}] I guess $guess!")
          conf.refs.table ! GuessHandler.SendGuess(conf.id, guess, context.self)
          guessing(conf, coins)
        case SendGuessResp(alreadyChoosen) if alreadyChoosen =>
          val guess = Random.between(0, 3*conf.nump)
          println(s"[${conf.id}] I guess $guess!")
          conf.refs.table ! GuessHandler.SendGuess(conf.id, guess, context.self)
          guessing(conf, coins)
        case SendGuessResp(alreadyChoosen) if !alreadyChoosen =>
          conf.refs.next ! PlayYourTurn() //Unblock next player to guess
          waitToReveal(conf, coins)
      })
  }

  def waitToReveal(conf : PConf, coins : Int) : Behavior[PEvent] = {
    Behaviors.receive((context, message) =>
      message match {
        case PlayYourTurn() if conf.init =>
          println(s"[${conf.id}] I'm INIT, Ok compute result!")
          conf.refs.table ! GuessHandler.ComputeTheResult(context.self)
          waitToReveal(conf, coins)
        case GiveMeYourCoins() =>
          println(s"[${conf.id}] Here my coins!")
          conf.refs.table ! GuessHandler.HereMyCoins(coins)//fire and forget
          waitRoundResult(conf)
      })
  }

  def waitRoundResult(conf : PConf) : Behavior[PEvent] = {
    Behaviors.receiveMessage(message =>
      message match {
        case EndGame(winnerId) if conf.id==winnerId =>
          conf.refs.next ! EndGame(winnerId)
          exits(conf)

        case EndGame(winnerId) if conf.id!=winnerId =>
          conf.refs.parent ! Game.End(conf.id)
          exits(conf)

        case EndRound(winnerId) if conf.init =>
          conf.refs.next ! RoundResult(winnerId, false)
          waitRoundResult(conf)

        case RoundResult(winnerId, _) if conf.init && conf.id==winnerId =>
          conf.refs.next ! StartRound()
          exits(conf)

        case RoundResult(winnerId, _) if conf.init && conf.id!=winnerId =>
          conf.refs.next ! StartRound()
          waitDrawingCoins(PConf(conf.id, false, conf.nump-1, conf.refs))

        case RoundResult(winnerId, initAlreadyChosen) if !conf.init && conf.id==winnerId =>
          conf.refs.next ! RoundResult(winnerId, initAlreadyChosen)
          exits(conf)

        case RoundResult(winnerId, initAlreadyChosen) if !conf.init && conf.id!=winnerId && !initAlreadyChosen =>
          conf.refs.next ! RoundResult(winnerId, true)
            waitDrawingCoins(PConf(conf.id, true, conf.nump-1, conf.refs))

        case RoundResult(winnerId, initAlreadyChosen) if !conf.init && conf.id!=winnerId && initAlreadyChosen =>
          conf.refs.next ! RoundResult(winnerId, initAlreadyChosen)
          waitDrawingCoins(PConf(conf.id, conf.init, conf.nump-1, conf.refs))
      })
  }

  def exits(conf : PConf): Behavior[PEvent] = {
    Behaviors.receiveMessage(message =>
      message match {
        case StartRound() =>
          conf.refs.next ! StartRound()
          Behaviors.same
        case PlayYourTurn() =>
          conf.refs.next ! PlayYourTurn()
          Behaviors.same
        case EndGame(winnerId) =>
          conf.refs.next ! EndGame(winnerId)
          Behaviors.same
        case RoundResult(winnerId, initAlreadyChosen) =>
          conf.refs.next ! RoundResult(winnerId, initAlreadyChosen)
          Behaviors.same
        case _ =>
          Behaviors.same
      })
  }
}


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