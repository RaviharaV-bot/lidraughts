import { VNode } from 'snabbdom/vnode'

export type MaybeVNode = VNode | string | null | undefined;
export type MaybeVNodes = MaybeVNode[];
export type Redraw = () => void;

// modules/externalTournament/src/main/ExternalPlayer.scala

export const playerStatus = {
  invited: 0,
  rejected: 10,
  joined: 20
};

export interface ExternalTournamentOpts {
  data: ExternalTournamentData;
  element: HTMLElement;
  $side: JQuery;
  socketSend: SocketSend;
  chat: any;
  i18n: any;
  classes: string | null;
}

export interface ExternalTournamentData {
  id: string;
  createdBy: string;
  name: string;
  rounds?: number;
  nbPlayers: number;
  nbUpcoming: number;
  nbFinished: number;
  invited?: InvitedPlayer[];
  upcoming: Challenge[];
  ongoing: Board[];
  finished: Game[];
  standing: Standing;
  me?: MyInfo;
  playerInfo?: PlayerInfo;
  socketVersion?: number;
  draughtsResult: boolean;
}

export interface Standing {
  user?: LightUser;
  page: number;
  players: PlayerInfo[];
}

export interface MyInfo {
  userId: string;
  rank?: number;
  canJoin?: boolean;
  gameId?: string;
}

export interface BasePlayer {
  user: LightUser;
  rating: number;
  provisional?: boolean;
  rank?: number;
}

export interface PlayerInfo extends BasePlayer {
  sheet: GameResult[];
  points: number;
  fmjd?: FmjdPlayer;
}

export interface InvitedPlayer extends BasePlayer {
  status: number;
}

export interface GameResult extends BasePlayer {
  g: string; // game
  c: boolean; // color
  w?: boolean; // won
}

export interface BaseGame {
  id: string;
  variant: VariantData;
  white: BasePlayer;
  black: BasePlayer;
}

export interface Challenge extends BaseGame {
  startsAt?: string;
}

export interface Game extends BaseGame {
  createdAt: string;
  winner?: Color;
}

export interface Board extends BaseGame {
  fen: string;
  lastMove?: string;
  orientation: Color;
  clock?: {
    white: number;
    black: number;
  }
  winner?: Color;
}

export interface FmjdPlayer {
  id: string;
  name: string;
  picUrl: string;
  profileUrl: string;
  country: string;
  title?: string;
  rating?: number;
}

export interface Pager {
  nbResults: number;
  nbPages: number;
  from: number;
  to: number;
  currentPageResults: Page;
}

export type Page = PlayerInfo[];

export interface Pages {
  [n: number]: Page
}

export interface VariantData {
  key: VariantKey
  board: BoardData
}
