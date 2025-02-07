package views.html.team

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.app.mashup.TeamInfo

import controllers.routes

object tournaments {

  def page(t: lidraughts.team.Team, tours: List[TeamInfo.AnyTour])(implicit ctx: Context) = {
    bits.layout(title = s"${t.name} • ${trans.tournaments()}") {
      main(cls := "page-small")(
        div(cls := "box box-pad")(
          h1(
            views.html.team.bits.link(t),
            " • ",
            trans.tournaments()
          ),
          div(cls := "team-tournaments")(
            widget(tours)
          )
        )
      )
    }
  }

  def widget(tours: List[TeamInfo.AnyTour])(implicit ctx: Context) =
    table(cls := "slist")(
      tbody(
        tours map { t =>
          tr(
            cls := List(
              "enterable" -> t.isEnterable,
              "soon" -> t.isNowOrSoon
            )
          )(
              td(cls := "icon")(iconTag(t.any.fold(tournamentIconChar, views.html.swiss.bits.iconChar))),
              td(cls := "header")(
                t.any.fold(
                  t =>
                    a(href := routes.Tournament.show(t.id))(
                      span(cls := "name")(t.fullName),
                      span(cls := "setup")(
                        t.clock.show,
                        " • ",
                        if (t.variant.exotic) t.variant.name else t.perfType.map(_.name),
                        t.isThematic option frag(" • ", trans.thematic()),
                        " • ",
                        t.mode.fold(trans.casualTournament, trans.ratedTournament)(),
                        " • ",
                        t.durationString
                      )
                    ),
                  s =>
                    a(href := routes.Swiss.show(s.id.value))(
                      span(cls := "name")(s.name),
                      span(cls := "setup")(
                        s.clock.show,
                        " • ",
                        if (s.variant.exotic) s.variant.name else s.perfType.map(_.name),
                        " • ",
                        (if (s.settings.rated) trans.ratedTournament else trans.casualTournament)()
                      )
                    )
                )
              ),
              td(cls := "infos")(
                t.any.fold(
                  t =>
                    frag(
                      t.teamBattle map { battle =>
                        frag(battle.teams.size, " teams battle")
                      } getOrElse "Inner team",
                      br,
                      momentFromNowOnce(t.startsAt)
                    ),
                  s =>
                    frag(
                      s.actualNbRounds,
                      " rounds swiss",
                      br,
                      momentFromNowOnce(s.startsAt)
                    )
                )
              ),
              td(cls := "text", dataIcon := "r")(t.nbPlayers.localize)
            )
        }
      )
    )
}
