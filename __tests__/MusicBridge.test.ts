import { parseMusicState, parsePlaybackTick, parsePlayerState } from '../src/native/MusicBridge';

const stateFixture = {
  home: {
    tracks: [],
    isLoading: false,
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
    autoCacheOnPlay: false,
    isSaving: false,
    error: null,
  },
};

test('parses persisted auto-cache preference from native state', () => {
  expect(parseMusicState(JSON.stringify(stateFixture)).settings.autoCacheOnPlay).toBe(false);
});

test('rejects invalid auto-cache values in native state', () => {
  const invalidState = {
    ...stateFixture,
    settings: { ...stateFixture.settings, autoCacheOnPlay: 'disabled' },
  };

  expect(() => parseMusicState(JSON.stringify(invalidState))).toThrow(
    'The native state field "autoCacheOnPlay" is invalid.',
  );
});

test('parses native state when a null current track is omitted from JSON', () => {
  const stateWithoutTrack = {
    ...stateFixture,
    player: { ...stateFixture.player, currentTrack: undefined },
  };

  expect(parseMusicState(JSON.stringify(stateWithoutTrack)).player.currentTrack).toBeNull();
});

test('parses lightweight native playback ticks', () => {
  expect(parsePlaybackTick(JSON.stringify({
    positionMs: 1250,
    durationMs: 180000,
    isPlaying: true,
  }))).toEqual({
    positionMs: 1250,
    durationMs: 180000,
    isPlaying: true,
  });
});

test('parses a player-only native update', () => {
  expect(parsePlayerState(JSON.stringify(stateFixture.player))).toEqual(stateFixture.player);
});
