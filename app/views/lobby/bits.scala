package views.html.lobby

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object bits {

  def underboards(
    tours: List[lidraughts.tournament.Tournament],
    simuls: List[lidraughts.simul.Simul],
    leaderboard: List[lidraughts.user.User.LightPerf],
    tournamentWinners: List[lidraughts.tournament.Winner]
  )(implicit ctx: Context) = frag(
    div(cls := "leaderboards undertable")(
      div(
        div(cls := "undertable_top")(
          a(cls := "more", href := routes.User.list)(trans.more(), " »"),
          span(cls := "title text", dataIcon := "C")(trans.leaderboard())
        ),
        div(cls := "undertable_inner scroll-shadow-hard")(
          table(tbody(
            leaderboard map { l =>
              tr(
                td(lightUserLink(l.user)),
                lidraughts.rating.PerfType(l.perfKey) map { pt =>
                  td(cls := "text", dataIcon := pt.iconChar)(l.rating)
                },
                td(showProgress(l.progress, withTitle = false))
              )
            }
          ))
        )
      ),
      div(
        div(cls := "undertable_top")(
          a(cls := "more", href := routes.Tournament.leaderboard)(trans.more(), " »"),
          span(cls := "title text", dataIcon := "g")(trans.tournamentWinners())
        ),
        div(cls := "undertable_inner scroll-shadow-hard")(
          table(tbody(
            tournamentWinners take 10 map { w =>
              tr(
                td(userIdLink(w.userId.some)),
                td(a(title := w.tourName, href := routes.Tournament.show(w.tourId))(scheduledTournamentNameShortHtml(w.tourName)))
              )
            }
          ))
        )
      )
    ),
    div(cls := "undertable")(
      div(cls := "undertable_top")(
        a(cls := "more", href := routes.Tournament.home())(frag(trans.more(), " »")),
        span(cls := "title text", dataIcon := "g")(trans.openTournaments())
      ),
      div(id := "enterable_tournaments", cls := "enterable_list undertable_inner scroll-shadow-hard")(
        views.html.tournament.enterable(tours)
      )
    ),
    div(cls := List("undertable" -> true, "none" -> simuls.isEmpty))(
      div(cls := "undertable_top")(
        a(cls := "more", href := routes.Simul.home())(frag(trans.more(), " »")),
        span(cls := "title text", dataIcon := "|")(trans.simultaneousExhibitions())
      ),
      div(id := "enterable_simuls", cls := "enterable_list undertable_inner")(
        views.html.simul.bits.allCreated(simuls)
      )
    )
  )

  def lastPosts(posts: List[lidraughts.blog.MiniPost])(implicit ctx: Context): Option[Frag] = posts.nonEmpty option
    div(cls := "blog undertable")(
      div(
        div(cls := "undertable_top")(
          a(cls := "more", href := routes.Blog.index())(trans.more(), " »"),
          span(cls := "title text", dataIcon := "6")(trans.latestUpdates())
        ),
        div(cls := "undertable_inner")(
          posts map { post =>
            a(cls := "post", href := routes.Blog.show(post.id, post.slug))(
              img(src := post.image),
              span(cls := "text")(
                span(cls := "title")(post.title),
                p(cls := "shortlede")(post.shortlede)
              ),
              semanticDate(post.date)
            )
          }
        )
      )
    )

  def currentGameInfo(current: lidraughts.app.mashup.Preload.CurrentGame)(implicit ctx: Context) =
    div(id := "lobby_current_game")(
      h2("Hang on!"),
      p("You have a game in progress with ", strong(current.opponent), "."),
      br, br,
      a(cls := "big text button", dataIcon := "G", href := routes.Round.player(current.pov.fullId))("Join the game"),
      br, br,
      "or",
      br, br,
      form(action := routes.Round.resign(current.pov.fullId), method := "post")(
        button(cls := "big text button", dataIcon := "L")(
          if (current.pov.game.abortable) "Abort" else "Resign", " the game"
        )
      ),
      br,
      p("You can't start a new game until this one is finished."),
      br, br,
      p(
        "If you want to play several games simultaneously,",
        br,
        a(href := routes.Simul.home)("create a simultaneous exhibition event"),
        "!"
      )
    )

  def spotlight(e: lidraughts.event.Event)(implicit ctx: Context) = a(
    href := routes.Event.show(e.id).url,
    cls := List(
      s"tour_spotlight event_spotlight id_${e.id}" -> true,
      "invert" -> e.isNowOrSoon
    )
  )(
      i(cls := "img", dataIcon := ""),
      span(cls := "content")(
        span(cls := "name")(e.title),
        span(cls := "headline")(e.headline),
        span(cls := "more")(
          if (e.isNow) trans.eventInProgress() else momentFromNow(e.startsAt)
        )
      )
    )

  def spotlight(r: lidraughts.relay.Relay)(implicit ctx: Context) = {
    val splitName = r.name.split(" - ")
    val description = splitName.tail.lastOption.getOrElse(shorten(r.description, 30))
    a(
      href := routes.Relay.show(r.slug, r.id.value).url,
      cls := List(
        s"tour_spotlight event_spotlight id_${r.id}" -> true,
        "invert" -> r.isNowOrSoon
      )
    )(
        i(cls := "img", dataIcon := ""),
        span(cls := "content")(
          span(cls := "name")(splitName.head),
          span(cls := "headline")(description),
          span(cls := "more")(
            if (r.hasStarted) trans.eventInProgress() else r.startsAt.fold(emptyHtml)(momentFromNow(_))
          )
        )
      )
  }
}
