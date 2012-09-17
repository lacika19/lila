package lila
package round

import game.{ DbGame, GameRepo, PovRef }

import scalaz.effects._

final class Meddler(
    gameRepo: GameRepo,
    finisher: Finisher,
    socket: Socket) {

  def forceAbort(id: String): IO[Unit] = for {
    gameOption ← gameRepo game id
    _ ← gameOption.fold(
      game ⇒ (finisher forceAbort game).fold(
        err ⇒ putStrLn(err.shows),
        ioEvents ⇒ for {
          events ← ioEvents
          _ ← io { socket.send(game.id, events) }
        } yield ()
      ),
      putStrLn("Cannot abort missing game " + id)
    )
  } yield ()

  def resign(povRef: PovRef): IO[Unit] = for {
    povOption ← gameRepo pov povRef
    _ ← povOption.fold(
      pov ⇒ (finisher resign pov).fold(
        err ⇒ putStrLn(err.shows),
        ioEvents ⇒ for {
          events ← ioEvents
          _ ← io { socket.send(pov.game.id, events) }
        } yield ()
      ),
      putStrLn("Cannot resign missing game " + povRef)
    )
  } yield ()
}