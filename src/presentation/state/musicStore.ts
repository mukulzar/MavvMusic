import { create } from 'zustand';
import {
  dispatchNativeIntent,
  getNativeMusicState,
  subscribeToNativeMusic,
  type MusicAppState,
  type MusicEffect,
  type PlaybackTick,
  type PlayerState,
} from '../../native/MusicBridge';

const initialState: MusicAppState = {
  home: {
    tracks: [],
    isLoading: true,
    activeFilter: 'For you',
    libraryFilter: 'All',
    likedTrackIds: [],
    error: null,
  },
  search: {
    query: '',
    results: [],
    isLoading: false,
    error: null,
  },
  player: {
    currentTrack: null,
    isPlaying: false,
    positionMs: 0,
    durationMs: 0,
    cacheStatus: 'not_cached',
    isLoading: false,
    error: null,
  },
  cache: {
    cachedTrackIds: [],
    isClearing: false,
    error: null,
  },
  settings: {
    autoCacheOnPlay: true,
    isSaving: false,
    error: null,
  },
};

interface MusicStore {
  state: MusicAppState;
  bridgeError: string | null;
  notice: string | null;
  playerExpanded: boolean;
  connect: () => Promise<void>;
  dispatch: (name: string, payload?: Record<string, string | number | boolean>) => void;
  dismissNotice: () => void;
  closePlayer: () => void;
  setPlayerExpanded: (expanded: boolean) => void;
}

let removeNativeListeners: (() => void) | null = null;

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'The music service could not be reached.';
}

export const useMusicStore = create<MusicStore>((set) => ({
  state: initialState,
  bridgeError: null,
  notice: null,
  playerExpanded: false,
  connect: async () => {
    removeNativeListeners?.();
    removeNativeListeners = null;
    try {
      removeNativeListeners = subscribeToNativeMusic(
        (state) => set({ state, bridgeError: null }),
        (player: PlayerState) => set((current) => ({
          state: {
            ...current.state,
            player: { ...current.state.player, ...player },
          },
          bridgeError: null,
        })),
        (tick: PlaybackTick) => set((current) => ({
          state: {
            ...current.state,
            player: { ...current.state.player, ...tick },
          },
          bridgeError: null,
        })),
        (effect: MusicEffect) => {
          if (effect.type === 'showError') set({ notice: effect.message });
          if (effect.type === 'openPlayerSheet') set({ playerExpanded: true });
        },
      );
      const state = await getNativeMusicState();
      set({ state, bridgeError: null });
    } catch (error) {
      set({ bridgeError: errorMessage(error) });
    }
  },
  dispatch: (action, payload = {}) => {
    console.log('JS DISPATCHING TO KOTLIN:', action, payload);
    try {
      dispatchNativeIntent(action, payload);
    } catch (error) {
      console.error('BRIDGE DISPATCH ERROR:', error);
      set({ bridgeError: errorMessage(error) });
    }
  },
  dismissNotice: () => set({ notice: null }),
  closePlayer: () => set({ playerExpanded: false }),
  setPlayerExpanded: (playerExpanded) => set({ playerExpanded }),
}));
