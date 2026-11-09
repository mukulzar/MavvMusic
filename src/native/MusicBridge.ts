import {
  NativeModules,
  NativeEventEmitter,
  type TurboModule,
} from 'react-native';

export type CacheStatus = 'not_cached' | 'caching' | 'cached' | 'failed';

export interface TrackState {
  id: string;
  title: string;
  artist: string;
  artistId: string;
  album: string;
  albumId: string;
  durationMs: number;
  artworkUri: string | null;
}

export interface HomeState {
  tracks: TrackState[];
  isLoading: boolean;
  activeFilter: string;
  libraryFilter: string;
  likedTrackIds: string[];
  error: string | null;
}

export interface SearchState {
  query: string;
  results: TrackState[];
  isLoading: boolean;
  error: string | null;
}

export interface PlayerState {
  currentTrack: TrackState | null;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  cacheStatus: CacheStatus;
  isLoading: boolean;
  error: string | null;
}

export interface CacheState {
  cachedTrackIds: string[];
  isClearing: boolean;
  error: string | null;
}

export interface SettingsState {
  autoCacheOnPlay: boolean;
  isSaving: boolean;
  error: string | null;
}

export interface MusicAppState {
  home: HomeState;
  search: SearchState;
  player: PlayerState;
  cache: CacheState;
  settings: SettingsState;
}

export type MusicEffect =
  | { type: 'showError'; message: string }
  | { type: 'openPlayerSheet' };

interface NativeMavvMusicSpec extends TurboModule {
  getState(): Promise<string>;
  dispatch(name: string, payload: Record<string, string | number | boolean>): void;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const nativeModule = NativeModules.MavvMusic as NativeMavvMusicSpec | undefined;

function requireNativeModule(): NativeMavvMusicSpec {
  if (!nativeModule) {
    throw new Error('The MavvMusic native module is not registered.');
  }
  return nativeModule;
}

function record(value: unknown, label: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error(`The native ${label} state is invalid.`);
  }
  return value as Record<string, unknown>;
}

function stringField(source: Record<string, unknown>, key: string): string {
  const value = source[key];
  if (typeof value !== 'string') throw new Error(`The native state field "${key}" is invalid.`);
  return value;
}

function nullableStringField(source: Record<string, unknown>, key: string): string | null {
  const value = source[key];
  if (value === null || value === undefined) return null;
  if (typeof value !== 'string') throw new Error(`The native state field "${key}" is invalid.`);
  return value;
}

function booleanField(source: Record<string, unknown>, key: string): boolean {
  const value = source[key];
  if (typeof value !== 'boolean') throw new Error(`The native state field "${key}" is invalid.`);
  return value;
}

function numberField(source: Record<string, unknown>, key: string): number {
  const value = source[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`The native state field "${key}" is invalid.`);
  }
  return value;
}

function stringArrayField(source: Record<string, unknown>, key: string): string[] {
  const value = source[key];
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) {
    throw new Error(`The native state field "${key}" is invalid.`);
  }
  return value;
}

function isCacheStatus(value: string): value is CacheStatus {
  return value === 'not_cached'
    || value === 'caching'
    || value === 'cached'
    || value === 'failed';
}

function track(value: unknown): TrackState {
  const source = record(value, 'track');
  return {
    id: stringField(source, 'id'),
    title: stringField(source, 'title'),
    artist: stringField(source, 'artist'),
    artistId: stringField(source, 'artistId'),
    album: stringField(source, 'album'),
    albumId: stringField(source, 'albumId'),
    durationMs: numberField(source, 'durationMs'),
    artworkUri: nullableStringField(source, 'artworkUri'),
  };
}

function trackArray(value: unknown, label: string): TrackState[] {
  if (!Array.isArray(value)) throw new Error(`The native ${label} list is invalid.`);
  return value.map(track);
}

export function parseMusicState(serialized: string): MusicAppState {
  let decoded: unknown;
  try {
    decoded = JSON.parse(serialized);
  } catch {
    throw new Error('The native music state could not be decoded.');
  }

  const source = record(decoded, 'app');
  const home = record(source.home, 'home');
  const search = record(source.search, 'search');
  const player = record(source.player, 'player');
  const cache = record(source.cache, 'cache');
  const settings = record(source.settings, 'settings');
  const cacheStatus = stringField(player, 'cacheStatus');
  if (!isCacheStatus(cacheStatus)) {
    throw new Error('The native player cache state is invalid.');
  }
  const currentTrack = player.currentTrack == null ? null : track(player.currentTrack);

  return {
    home: {
      tracks: trackArray(home.tracks, 'home'),
      isLoading: booleanField(home, 'isLoading'),
      activeFilter: stringField(home, 'activeFilter'),
      libraryFilter: stringField(home, 'libraryFilter'),
      likedTrackIds: stringArrayField(home, 'likedTrackIds'),
      error: nullableStringField(home, 'error'),
    },
    search: {
      query: stringField(search, 'query'),
      results: trackArray(search.results, 'search'),
      isLoading: booleanField(search, 'isLoading'),
      error: nullableStringField(search, 'error'),
    },
    player: {
      currentTrack,
      isPlaying: booleanField(player, 'isPlaying'),
      positionMs: numberField(player, 'positionMs'),
      durationMs: numberField(player, 'durationMs'),
      cacheStatus,
      isLoading: booleanField(player, 'isLoading'),
      error: nullableStringField(player, 'error'),
    },
    cache: {
      cachedTrackIds: stringArrayField(cache, 'cachedTrackIds'),
      isClearing: booleanField(cache, 'isClearing'),
      error: nullableStringField(cache, 'error'),
    },
    settings: {
      autoCacheOnPlay: booleanField(settings, 'autoCacheOnPlay'),
      isSaving: booleanField(settings, 'isSaving'),
      error: nullableStringField(settings, 'error'),
    },
  };
}

export function getNativeMusicState(): Promise<MusicAppState> {
  return requireNativeModule().getState().then(parseMusicState);
}

export function dispatchNativeIntent(
  name: string,
  payload: Record<string, string | number | boolean> = {},
): void {
  requireNativeModule().dispatch(name, payload);
}

export function subscribeToNativeMusic(
  onState: (state: MusicAppState) => void,
  onEffect: (effect: MusicEffect) => void,
): () => void {
  const module = requireNativeModule();
  const events = new NativeEventEmitter(module);
  const stateSubscription = events.addListener('mavvMusic.state', (event: { stateJson?: string }) => {
    if (typeof event.stateJson !== 'string') throw new Error('The native state event is invalid.');
    onState(parseMusicState(event.stateJson));
  });
  const effectSubscription = events.addListener('mavvMusic.effect', (value: Object) => {
    if (typeof value !== 'object' || value === null) {
      throw new Error('The native music effect is invalid.');
    }
    const event = value as { type?: unknown; message?: unknown };
    if (event.type === 'showError' && typeof event.message === 'string') {
      onEffect({ type: 'showError', message: event.message });
      return;
    }
    if (event.type === 'openPlayerSheet') {
      onEffect({ type: 'openPlayerSheet' });
      return;
    }
    throw new Error('The native music effect is invalid.');
  });
  return () => {
    stateSubscription.remove();
    effectSubscription.remove();
  };
}
