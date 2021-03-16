import Player.PEvent
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

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