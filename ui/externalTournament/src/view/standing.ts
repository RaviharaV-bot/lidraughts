import { h } from 'snabbdom'
import { VNode,  } from 'snabbdom/vnode';
import ExternalTournamentCtrl from '../ctrl';
import { player as lidraughtsPlayer, result as renderResult, fmjdPlayer, onInsert, bind } from './util';
import { MaybeVNodes, Pager, PlayerInfo } from '../interfaces';

function playerTr(ctrl: ExternalTournamentCtrl, p: PlayerInfo) {
  const noarg = ctrl.trans.noarg,
    draughtsResult = ctrl.data.draughtsResult,
    userId = p.user.id,
    winChar = draughtsResult ? '2' : '1',
    drawChar = draughtsResult ? '1' : '½';
  return h('tr', {
      key: userId,
      class: {
        me: ctrl.isMe(p.user),
        active: ctrl.playerInfoId === userId
      },
      hook: bind('click', _ => ctrl.showPlayerInfo(p), ctrl.redraw)
    }, [
      h('td.rank', p.rank ? [p.rank] : []),
      h('td.player', ctrl.data.displayFmjd ? fmjdPlayer(p, false, true) : lidraughtsPlayer(p, false, true, false)),
      h('td.games' + (ctrl.data.rounds ? '.rounds' : ''),
        h('div',
          p.sheet.map(r => {
            if (r.b) return h('bye', title(noarg('bye')), r.b === 2 ? winChar : drawChar)
            else if (r.b === 0) return h('r')
            const color = r.c ? 'white' : 'black';
            return h('a.glpt.' + (r.o ? 'ongoing' : (r.w === true ? 'win' : (r.w === false ? 'loss' : 'draw'))), {
              attrs: {
                key: r.g,
                href: `/${r.g}${color === 'white' ? '' : '/black'}`
              },
              hook: onInsert(window.lidraughts.powertip.manualGame)
            }, renderResult(r, draughtsResult))
          }).concat(
            [...Array(Math.max(ctrl.data.roundsPlayed || 0, p.sheet.length) - p.sheet.length)].map(_ => h('r'))
          )
        )),
      h('td.points', title(noarg('points')), '' + (draughtsResult ? p.points : p.points / 2)),
    ])
}

const title = (str: string) => ({ attrs: { title: str } });

let lastBody: MaybeVNodes | undefined;

const preloadUserTips = (vn: VNode) => window.lidraughts.powertip.manualUserIn(vn.elm as HTMLElement);

export default function standing(ctrl: ExternalTournamentCtrl, pag: Pager): VNode {
  const tableBody = pag.currentPageResults ?
    pag.currentPageResults.map(res => playerTr(ctrl, res)) : lastBody;
  if (pag.currentPageResults) lastBody = tableBody;
  return h('table.slist.tour-ext__standing', {
    class: {
      loading: !pag.currentPageResults,
      long: !!ctrl.data.rounds && ctrl.data.rounds > 10,
      xlong: !!ctrl.data.rounds && ctrl.data.rounds > 20,
    },
  }, [
    h('tbody', {
      hook: {
        insert: preloadUserTips,
        update(_, vnode) { preloadUserTips(vnode) }
      }
    }, tableBody || [])
  ]);
}
