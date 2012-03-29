package lila.system

import model._
import memo._
import db.{ GameRepo }
import lila.chess._
import Pos.posAt
import scalaz.effects._

final class AppXhr(
    val gameRepo: GameRepo,
    messenger: Messenger,
    ai: Ai,
    finisher: Finisher,
    val versionMemo: VersionMemo,
    aliveMemo: AliveMemo,
    moretimeSeconds: Int) extends IOTools {

  type IOValid = IO[Valid[Unit]]

  def play(
    fullId: String,
    origString: String,
    destString: String,
    promString: Option[String] = None): IOValid = fromPov(fullId) {
    case Pov(g1, color) ⇒ (for {
      g2 ← (g1.playable).fold(success(g1), failure("Game not playable" wrapNel))
      orig ← posAt(origString) toValid "Wrong orig " + origString
      dest ← posAt(destString) toValid "Wrong dest " + destString
      promotion ← Role promotable promString toValid "Wrong promotion"
      newChessGameAndMove ← g2.toChess(orig, dest, promotion)
      (newChessGame, move) = newChessGameAndMove
    } yield g2.update(newChessGame, move)).fold(
      e ⇒ io(failure(e)),
      g2 ⇒ for {
        g3 ← if (g2.player.isAi && g2.playable) for {
          aiResult ← ai(g2) map (_.toOption err "AI failure")
          (newChessGame, move) = aiResult
        } yield g2.update(newChessGame, move)
        else io(g2)
        _ ← save(g1, g3)
        _ ← aliveMemo.put(g3.id, color)
      } yield success()
    )
  }

  def abort(fullId: String): IOValid = attempt(fullId, finisher.abort)

  def resign(fullId: String): IOValid = attempt(fullId, finisher.resign)

  def forceResign(fullId: String): IOValid = attempt(fullId, finisher.forceResign)

  def claimDraw(fullId: String): IOValid = attempt(fullId, finisher.claimDraw)

  def outoftime(fullId: String): IOValid = attempt(fullId, finisher.outoftime)

  def drawAccept(fullId: String): IOValid = attempt(fullId, finisher.drawAccept)

  def talk(fullId: String, message: String): IO[Unit] = fromPov(fullId) { pov ⇒
    messenger.playerMessage(pov.game, pov.color, message) flatMap { g2 ⇒
      save(pov.game, g2)
    }
  }

  def moretime(fullId: String): IO[Valid[Float]] = attempt(fullId, pov ⇒
    pov.game.clock filter (_ ⇒ pov.game.playable) map { clock ⇒
      val color = !pov.color
      val newClock = clock.giveTime(color, moretimeSeconds)
      val g2 = pov.game withEvents List(MoretimeEvent(color, moretimeSeconds))
      val g3 = g2 withClock newClock
      save(pov.game, g3) map { _ ⇒ newClock remainingTime color }
    } toValid "cannot add moretime"
  )

  private def attempt[A](
    fullId: String,
    action: Pov ⇒ Valid[IO[A]]): IO[Valid[A]] =
    fromPov(fullId) { pov ⇒ action(pov).sequence }

  private def fromPov[A](fullId: String)(op: Pov ⇒ IO[A]): IO[A] =
    gameRepo pov fullId flatMap op
}