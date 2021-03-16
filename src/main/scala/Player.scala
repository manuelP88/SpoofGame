import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.util.Random

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
