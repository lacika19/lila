package lila
package game

import chess.format.Forsyth
import chess.{ Status, Variant, Color, Clock }
import user.{ User, UserHelper }
import http.Context
import i18n.I18nHelper

import controllers.routes

import play.api.templates.Html
import play.api.mvc.Call

trait GameHelper { self: I18nHelper with UserHelper ⇒

  val aiName = "Crafty A.I."

  def variantName(variant: Variant)(implicit ctx: Context) = variant match {
    case Variant.Standard ⇒ trans.standard.str()
    case Variant.Chess960 ⇒ "chess960"
  }

  def clockName(clock: Option[Clock])(implicit ctx: Context): String = 
    clock.fold(clockName, trans.unlimited.str())

  def clockName(clock: Clock): String = Namer clock clock

  def usernameWithElo(player: DbPlayer) = Namer.player(player)(userIdToUsername)

  def playerLink(player: DbPlayer, cssClass: Option[String] = None) = Html {
    player.userId.fold(
      userId ⇒ userIdToUsername(userId) |> { username ⇒
        """<a class="user_link%s%s" href="%s">%s</a>""".format(
          cssClass.fold(" " + _, ""),
          isUsernameOnline(username).fold(" online", ""),
          routes.User.show(username),
          usernameWithElo(player) + player.eloDiff.fold(
            diff ⇒ " (%s)".format((diff < 0).fold(diff, "+ " + diff)), "")
        )
      },
      usernameWithElo(player)
    )
  }

  def gameEndStatus(game: DbGame)(implicit ctx: Context): Html = game.status match {
    case Status.Aborted ⇒ trans.gameAborted()
    case Status.Mate    ⇒ trans.checkmate()
    case Status.Resign ⇒ game.loser match {
      case Some(p) if p.color.white ⇒ trans.whiteResigned()
      case _                        ⇒ trans.blackResigned()
    }
    case Status.Stalemate ⇒ trans.stalemate()
    case Status.Timeout ⇒ game.loser match {
      case Some(p) if p.color.white ⇒ trans.whiteLeftTheGame()
      case _                        ⇒ trans.blackLeftTheGame()
    }
    case Status.Draw      ⇒ trans.draw()
    case Status.Outoftime ⇒ trans.timeOut()
    case Status.Cheat     ⇒ Html("Cheat detected")
    case _                ⇒ Html("")
  }

  def gameFen(game: DbGame, user: Option[User] = None)(implicit ctx: Context) = Html {
    val color = ((user flatMap game.player) | game.creator).color
    val url = (ctx.me flatMap game.player).fold(
      p ⇒ routes.Round.player(game fullIdOf p.color),
      routes.Round.watcher(game.id, chess.Color.White.name)
    )
    """<a href="%s" title="%s" class="mini_board parse_fen" data-color="%s" data-fen="%s"></a>""".format(
      url,
      trans.viewInFullSize(),
      color,
      Forsyth exportBoard game.toChess.board)
  }
}