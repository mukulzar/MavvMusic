import React, { useEffect, useMemo, useState } from 'react';
import {
  Image,
  ImageBackground,
  BackHandler,
  Keyboard,
  Modal,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  useColorScheme,
  useWindowDimensions,
  View,
  type ImageStyle,
} from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import { SafeAreaProvider, SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import type { TrackState } from './src/native/MusicBridge';
import { useMusicStore } from './src/presentation/state/musicStore';

type Destination = 'Home' | 'Search' | 'Library' | 'Settings';
type IconName =
  | 'download'
  | 'search'
  | 'sparkles'
  | 'play'
  | 'pause'
  | 'next'
  | 'home'
  | 'compass'
  | 'library'
  | 'heart'
  | 'wave'
  | 'clock'
  | 'headphones'
  | 'arrow'
  | 'radio'
  | 'plus'
  | 'settings'
  | 'close'
  | 'check'
  | 'trash';

const photos = {
  hero: 'https://images.unsplash.com/photo-1779399162170-aa54e19ab161?auto=format&fit=crop&w=900&q=85',
  album: 'https://images.unsplash.com/photo-1706821177425-cecc34309a94?auto=format&fit=crop&w=400&q=85',
  portrait: 'https://images.unsplash.com/photo-1773592617447-18c997917ff4?auto=format&fit=crop&w=200&q=80',
  afterglow: 'https://images.unsplash.com/photo-1706821177427-26ba27bd43b7?auto=format&fit=crop&w=500&q=85',
  neon: 'https://images.unsplash.com/photo-1773592617122-12b44bd81ffd?auto=format&fit=crop&w=500&q=85',
  night: 'https://images.unsplash.com/photo-1792963762172-5f333e733ec9?auto=format&fit=crop&w=500&q=85',
};

const filters = ['For you', 'New releases', 'Moods', 'Podcasts', 'Live'];
const destinations: Array<{ label: Destination; icon: IconName }> = [
  { label: 'Home', icon: 'home' },
  { label: 'Search', icon: 'compass' },
  { label: 'Library', icon: 'library' },
  { label: 'Settings', icon: 'settings' },
];

const quickItems: Array<{ title: string; subtitle: string; icon: IconName; color: string }> = [
  { title: 'Liked songs', subtitle: 'Your favorites', icon: 'heart', color: '#e5d8f8' },
  { title: 'Daily flow', subtitle: 'Made for you', icon: 'wave', color: '#f4d8cb' },
  { title: 'Recently played', subtitle: 'Your history', icon: 'clock', color: '#d8e5f7' },
  { title: 'New frequencies', subtitle: 'Fresh finds', icon: 'radio', color: '#e5ebcc' },
];

const mixes = [
  { title: 'Afterglow', subtitle: 'Soulful & slow', image: photos.afterglow },
  { title: 'Neon pulse', subtitle: 'Alt-pop energy', image: photos.neon },
  { title: 'Night shift', subtitle: 'Electronic focus', image: photos.night },
];

const genres: Array<{ name: string; note: string; color: string; icon: IconName }> = [
  { name: 'Alternative', note: 'left of center', color: '#ded1ed', icon: 'wave' },
  { name: 'Electronic', note: 'future sounds', color: '#cbd8f0', icon: 'radio' },
  { name: 'R&B / Soul', note: 'slow & warm', color: '#f1d2c9', icon: 'heart' },
  { name: 'Focus', note: 'deep work', color: '#dce3c8', icon: 'headphones' },
];

const libraryFilters = ['All', 'Liked', 'Downloaded', 'Recent'];

function Icon({
  name,
  size = 20,
  color = '#4a4650',
  strokeWidth = 1.8,
}: {
  name: IconName;
  size?: number;
  color?: string;
  strokeWidth?: number;
}) {
  const paths: Record<IconName, React.ReactNode> = {
    download: <><Path d="M12 3v11m0 0 4-4m-4 4-4-4" /><Path d="M5 17v3h14v-3" /></>,
    search: <><Circle cx="11" cy="11" r="6.5" /><Path d="m16 16 4 4" /></>,
    sparkles: <><Path d="m12 2 1.2 4.1L17 8l-3.8 1.9L12 14l-1.2-4.1L7 8l3.8-1.9L12 2Z" /><Path d="m5 14 .7 2.3L8 17.5l-2.3 1.2L5 21l-.7-2.3L2 17.5l2.3-1.2L5 14Z" /></>,
    play: <Path d="m9 7 9 5-9 5V7Z" fill={color} stroke="none" />,
    pause: <><Path d="M9 7v10" /><Path d="M15 7v10" /></>,
    next: <><Path d="m7 7 8 5-8 5V7Z" fill={color} stroke="none" /><Path d="M17 7v10" /></>,
    home: <><Path d="m3 11 9-8 9 8" /><Path d="M5.5 9.5V21h13V9.5M9.5 21v-6h5v6" /></>,
    compass: <><Circle cx="12" cy="12" r="9" /><Path d="m15.5 8.5-2.1 4.9-4.9 2.1 2.1-4.9 4.9-2.1Z" /></>,
    library: <><Path d="M5 4v16M10 4v16M15 5l4 14" /></>,
    heart: <Path d="M20.8 5.8c-2.2-2.3-5.9-1.7-7.4.9L12 9l-1.4-2.3C9 4.1 5.4 3.5 3.2 5.8.9 8.2 1.4 12 3.8 14.2L12 21l8.2-6.8c2.4-2.2 2.9-6 .6-8.4Z" />,
    wave: <><Path d="M3 12h2M7 8v8M11 5v14M15 8v8M19 10v4M23 12h-2" /></>,
    clock: <><Circle cx="12" cy="12" r="9" /><Path d="M12 7v5l3 2" /></>,
    headphones: <><Path d="M4 14v-2a8 8 0 0 1 16 0v2" /><Path d="M4 14h3v6H5a1 1 0 0 1-1-1v-5ZM20 14h-3v6h2a1 1 0 0 0 1-1v-5Z" /></>,
    arrow: <Path d="M5 12h14m-5-5 5 5-5 5" />,
    radio: <><Circle cx="12" cy="13" r="3" /><Path d="M7.8 17.2a6 6 0 0 1 0-8.4M16.2 8.8a6 6 0 0 1 0 8.4M4.3 20.7a11 11 0 0 1 0-15.4M19.7 5.3a11 11 0 0 1 0 15.4" /></>,
    plus: <Path d="M12 5v14M5 12h14" />,
    settings: <><Circle cx="12" cy="12" r="3" /><Path d="m19.4 15 .1.1 1.1.8-1.1 1.9-1.3-.5a7.8 7.8 0 0 1-1.7 1l-.2 1.4h-2.2l-.2-1.4a7.8 7.8 0 0 1-1.7-1l-1.3.5-1.1-1.9 1.1-.8a7 7 0 0 1 0-2l-1.1-.8 1.1-1.9 1.3.5a7.8 7.8 0 0 1 1.7-1l.2-1.4h2.2l.2 1.4a7.8 7.8 0 0 1 1.7 1l1.3-.5 1.1 1.9-1.1.8a7 7 0 0 1 0 2Z" transform="translate(-1 -1)" /></>,
    close: <><Path d="m6 6 12 12M18 6 6 18" /></>,
    check: <Path d="m5 12 4 4L19 6" />,
    trash: <><Path d="M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3" /></>,
  };

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" accessibilityElementsHidden>
      {paths[name]}
    </Svg>
  );
}

function AppSurface() {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const isDark = useColorScheme() === 'dark';
  const expanded = width >= 700;
  const theme = useMemo(() => ({
    background: isDark ? '#17151b' : '#f7f4f9',
    surface: isDark ? '#24212a' : 'rgba(255,255,255,0.82)',
    surfaceAlt: isDark ? '#302c36' : 'rgba(255,255,255,0.58)',
    ink: isDark ? '#f5f0f8' : '#28242d',
    muted: isDark ? '#b7afbf' : '#726b78',
    line: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(78,69,91,0.09)',
  }), [isDark]);

  const music = useMusicStore((store) => store.state);
  const connect = useMusicStore((store) => store.connect);
  const dispatch = useMusicStore((store) => store.dispatch);
  const bridgeError = useMusicStore((store) => store.bridgeError);
  const notice = useMusicStore((store) => store.notice);
  const dismissNotice = useMusicStore((store) => store.dismissNotice);
  const playerExpanded = useMusicStore((store) => store.playerExpanded);
  const setPlayerExpanded = useMusicStore((store) => store.setPlayerExpanded);

  const [activeTab, setActiveTab] = useState<Destination>('Home');

  useEffect(() => {
    connect();
  }, [connect]);

  useEffect(() => {
    console.log("RN STATE UPDATE - Search Results:", music.search.results.length, "Query:", music.search.query);
    }, [music.search.results, music.search.query]);

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (playerExpanded) {
        setPlayerExpanded(false);
        return true;
      }
      if (activeTab !== 'Home') {
        setActiveTab('Home');
        Keyboard.dismiss();
        return true;
      }
      return false;
    });
    return () => subscription.remove();
  }, [activeTab, playerExpanded, setPlayerExpanded]);

  const feed = music.home.tracks;
  const currentTrack = music.player.currentTrack ?? feed[0] ?? null;
  const likedIds = music.home.likedTrackIds;
  const likedTracks = feed.filter((track) => likedIds.includes(track.id));
  const cachedTracks = feed.filter((track) => music.cache.cachedTrackIds.includes(track.id));
  const libraryFilter = music.home.libraryFilter;
  const visibleLibraryTracks = libraryFilter === 'Liked'
    ? likedTracks
    : libraryFilter === 'Downloaded'
      ? cachedTracks
      : libraryFilter === 'Recent'
        ? feed.slice(0, 3)
        : feed;

  const playTrack = (trackId?: string) => {
    const id = trackId ?? currentTrack?.id;
    if (id) dispatch('player.play', { trackId: id });
  };

  const togglePlayback = () => {
    if (music.player.isPlaying) dispatch('player.pause');
    else if (music.player.currentTrack) dispatch('player.resume');
    else playTrack();
  };

  const navigate = (destination: Destination) => {
    Keyboard.dismiss();
    setActiveTab(destination);
  };

  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={[styles.safeArea, { backgroundColor: theme.background }]}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      <View style={[styles.layout, expanded && styles.expandedLayout]}>
        {expanded && (
          <View style={[styles.rail, { backgroundColor: theme.background, borderColor: theme.line }]}>
            <Text style={[styles.brandMark, { color: theme.ink }]}>M</Text>
            {destinations.map((destination) => (
              <NavButton key={destination.label} item={destination} active={activeTab === destination.label}
                expanded onPress={() => navigate(destination.label)} theme={theme} />
            ))}
          </View>
        )}

        <View style={[styles.main, expanded && styles.expandedMain]}>
          <View style={[styles.topBar, { borderBottomColor: theme.line, backgroundColor: theme.background }]}>
            <View style={styles.topBarRow}>
              <Text style={[styles.pageTitle, { color: theme.ink }]}>{activeTab}</Text>
              <View style={styles.headerActions}>
                <IconButton label="Open settings" onPress={() => navigate('Settings')} theme={theme}>
                  <Icon name="download" size={19} color={theme.ink} />
                </IconButton>
                <IconButton label="Search music" onPress={() => navigate('Search')} theme={theme}>
                  <Icon name="search" size={19} color={theme.ink} />
                </IconButton>
                <Pressable onPress={() => navigate('Settings')} accessibilityRole="button"
                  accessibilityLabel="Open settings" style={styles.avatar}>
                  <Image source={{ uri: photos.portrait }} style={styles.avatarImage as ImageStyle} />
                  <View style={styles.onlineDot} />
                </Pressable>
              </View>
            </View>
            {activeTab === 'Home' && (
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
                {filters.map((filter) => (
                  <Pressable key={filter} onPress={() => {
                    dispatch('home.filterChanged', { filter });
                  }} style={[styles.filterPill, { borderColor: theme.line, backgroundColor: theme.surfaceAlt },
                    music.home.activeFilter === filter && styles.filterPillActive]}
                    accessibilityRole="button" accessibilityState={{ selected: music.home.activeFilter === filter }}>
                    <Text style={[styles.filterText, { color: theme.muted }, music.home.activeFilter === filter && styles.filterTextActive]}>
                      {filter}
                    </Text>
                  </Pressable>
                ))}
              </ScrollView>
            )}
          </View>

          <ScrollView
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
            contentContainerStyle={[styles.content, expanded ? styles.contentExpanded : styles.contentCompact]}
          >
            {activeTab === 'Home' && (
              <HomeScreen
                tracks={feed}
                loading={music.home.isLoading}
                error={music.home.error}
                theme={theme}
                onPlay={playTrack}
                onOpenSearch={() => navigate('Search')}
                onOpenLibrary={() => navigate('Library')}
              />
            )}
            {activeTab === 'Search' && (
              <SearchScreen
                query={music.search.query}
                results={music.search.results}
                loading={music.search.isLoading}
                error={music.search.error}
                theme={theme}
                likedIds={likedIds}
                onQueryChange={(query) => dispatch('search.queryChanged', { query })}
                onPlay={playTrack}
                onLike={(trackId) => dispatch('library.toggleLiked', { trackId })}
                
              />
            )}
            {activeTab === 'Library' && (
              <LibraryScreen
                tracks={visibleLibraryTracks}
                likedCount={likedTracks.length}
                cachedCount={cachedTracks.length}
                likedIds={likedIds}
                activeFilter={libraryFilter}
                theme={theme}
                onFilterChange={(filter) => {
                  dispatch('library.filterChanged', { filter });
                }}
                onPlay={playTrack}
                onLike={(trackId) => dispatch('library.toggleLiked', { trackId })}
              />
            )}
            {activeTab === 'Settings' && (
              <SettingsScreen
                cacheCount={cachedTracks.length}
                isClearing={music.cache.isClearing}
                cacheError={music.cache.error}
                autoCacheOnPlay={music.settings.autoCacheOnPlay}
                isSavingSettings={music.settings.isSaving}
                settingsError={music.settings.error}
                theme={theme}
                onClear={() => dispatch('cache.clear')}
                onAutoCacheChange={(enabled) => dispatch('settings.autoCacheChanged', { enabled })}
              />
            )}
          </ScrollView>

          {!expanded && (
            <View style={[styles.mobileDock, { bottom: Math.max(insets.bottom, 8) }]}>
              <MiniPlayer track={currentTrack} isPlaying={music.player.isPlaying}
                liked={currentTrack ? likedIds.includes(currentTrack.id) : false} theme={theme}
                onOpen={() => setPlayerExpanded(true)} onToggle={togglePlayback}
                onNext={() => dispatch('player.next')}
                onLike={() => currentTrack && dispatch('library.toggleLiked', { trackId: currentTrack.id })} />
              <View style={[styles.bottomNav, { borderColor: theme.line, backgroundColor: theme.surface }]}>
                {destinations.map((destination) => (
                  <NavButton key={destination.label} item={destination} active={activeTab === destination.label}
                    onPress={() => navigate(destination.label)} theme={theme} />
                ))}
              </View>
            </View>
          )}

          {expanded && (
            <View style={[styles.tabletPlayer, { bottom: Math.max(insets.bottom, 12) }]}>
              <MiniPlayer track={currentTrack} isPlaying={music.player.isPlaying}
                liked={currentTrack ? likedIds.includes(currentTrack.id) : false} theme={theme}
                onOpen={() => setPlayerExpanded(true)} onToggle={togglePlayback}
                onNext={() => dispatch('player.next')}
                onLike={() => currentTrack && dispatch('library.toggleLiked', { trackId: currentTrack.id })} />
            </View>
          )}
        </View>
      </View>

      <Modal visible={playerExpanded} transparent animationType="slide" onRequestClose={() => setPlayerExpanded(false)}>
        <View style={styles.modalScrim}>
          <View style={[styles.playerSheet, { backgroundColor: theme.background }]}>
            <View style={styles.sheetHandle} />
            <Pressable style={styles.closeButton} onPress={() => setPlayerExpanded(false)}
              accessibilityRole="button" accessibilityLabel="Close player">
              <Icon name="close" size={21} color={theme.ink} />
            </Pressable>
            <Text style={[styles.sectionKicker, { color: theme.muted }]}>NOW PLAYING</Text>
            {currentTrack ? (
              <>
                <Image source={{ uri: currentTrack.artworkUri ?? photos.album }} style={styles.sheetArtwork as ImageStyle} />
                <Text style={[styles.sheetTitle, { color: theme.ink }]} numberOfLines={1}>{currentTrack.title}</Text>
                <Text style={[styles.bodyMuted, { color: theme.muted }]}>{currentTrack.artist} · {currentTrack.album}</Text>
                <View style={styles.progressTrack}>
                  <View style={[styles.progressFill, {
                    width: `${music.player.durationMs > 0
                      ? Math.min(100, (music.player.positionMs / music.player.durationMs) * 100)
                      : 0}%`,
                  }]} />
                </View>
                <View style={styles.playerActions}>
                  <Pressable style={styles.sheetControl} onPress={() => dispatch('player.next')}
                    accessibilityRole="button" accessibilityLabel="Next track">
                    <Icon name="next" size={26} color={theme.ink} />
                  </Pressable>
                  <Pressable style={styles.sheetPlay} onPress={togglePlayback}
                    accessibilityRole="button" accessibilityLabel={music.player.isPlaying ? 'Pause' : 'Play'}>
                    <Icon name={music.player.isPlaying ? 'pause' : 'play'} size={25} color="#fff" />
                  </Pressable>
                  <Pressable style={styles.sheetControl} onPress={() => currentTrack && dispatch('library.toggleLiked', { trackId: currentTrack.id })}
                    accessibilityRole="button" accessibilityLabel={currentTrack && likedIds.includes(currentTrack.id) ? 'Unlike track' : 'Like track'}>
                    <Icon name="heart" size={24} color={currentTrack && likedIds.includes(currentTrack.id) ? '#7955af' : theme.ink} />
                  </Pressable>
                </View>
                <Text style={[styles.cacheNote, { color: theme.muted }]}>
                  {music.player.cacheStatus === 'cached'
                    ? 'Available offline'
                    : music.settings.autoCacheOnPlay
                      ? 'Caching for offline listening'
                      : 'Auto-cache is turned off'}
                </Text>
              </>
            ) : (
              <Text style={[styles.bodyMuted, { color: theme.muted }]}>Choose a track to start listening.</Text>
            )}
          </View>
        </View>
      </Modal>

      {(bridgeError || notice) && (
        <Pressable style={[styles.snackbar, { bottom: Math.max(insets.bottom, 12) + (expanded ? 12 : 140) }]}
          onPress={notice ? dismissNotice : undefined} accessibilityRole={notice ? 'button' : undefined}>
          <Text style={styles.snackbarText}>{notice ?? bridgeError}</Text>
          {notice && <Text style={styles.snackbarAction}>DISMISS</Text>}
        </Pressable>
      )}
    </SafeAreaView>
  );
}

function HomeScreen({
  tracks, loading, error, theme, onPlay, onOpenSearch, onOpenLibrary,
}: {
  tracks: TrackState[];
  loading: boolean;
  error: string | null;
  theme: Theme;
  onPlay: (trackId?: string) => void;
  onOpenSearch: () => void;
  onOpenLibrary: () => void;
}) {
  return (
    <View>
      <View style={styles.welcome}>
        <Text style={[styles.welcomeTitle, { color: theme.ink }]}>Good evening</Text>
        <Text style={[styles.bodyMuted, { color: theme.muted }]}>Ease into the night with something unexpected.</Text>
      </View>

      <View style={styles.heroCard}>
        <ImageBackground source={{ uri: photos.hero }} style={styles.heroImage} imageStyle={styles.heroImageStyle as ImageStyle}>
          <View style={styles.heroWash} />
          <View style={styles.heroCopy}>
            <View style={styles.badge}>
              <Icon name="sparkles" size={14} color="#6a5877" />
              <Text style={styles.badgeText}>MADE FOR YOU</Text>
            </View>
            <Text style={styles.heroTitle}>A soft place{'\n'}to <Text style={styles.heroItalic}>land.</Text></Text>
            <Text style={styles.heroDescription}>Dreamy alt-pop, velvet soul and a little bit of magic.</Text>
            <Pressable style={styles.listenButton} onPress={() => onPlay(tracks[0]?.id)}
              disabled={!tracks.length} accessibilityRole="button" accessibilityLabel="Listen now">
              <Text style={styles.listenText}>Listen now</Text>
              <View style={styles.listenPlay}><Icon name="play" size={16} color="#332f39" /></View>
            </Pressable>
          </View>
          <View style={styles.trackCount}><Text style={styles.trackCountText}>{tracks.length} demo tracks</Text></View>
        </ImageBackground>
      </View>

      <SectionHeading kicker="YOUR SHORTCUTS" title="Quick access" theme={theme}
        action="See all" onAction={onOpenLibrary} />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.quickRow}>
        {quickItems.map((item, index) => (
          <Pressable key={item.title} onPress={item.title === 'Liked songs' ? onOpenLibrary : () => onPlay(tracks[index % Math.max(1, tracks.length)]?.id)}
            style={[styles.quickCard, { backgroundColor: item.color }]} accessibilityRole="button">
            <View style={styles.quickIcon}><Icon name={item.icon} size={25} color="#48404e" /></View>
            <Text style={styles.quickTitle}>{item.title}</Text>
            <Text style={styles.quickSubtitle}>{item.subtitle}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <SectionHeading kicker="CURATED FOR 8 PM" title="Evening rotation" theme={theme}
        actionIcon="plus" onAction={onOpenSearch} />
      <View style={styles.mixGrid}>
        {mixes.map((mix, index) => (
          <Pressable key={mix.title} style={styles.mixCard} onPress={() => onPlay(tracks[index % Math.max(1, tracks.length)]?.id)}
            accessibilityRole="button" accessibilityLabel={`Play ${mix.title}`}>
            <View style={styles.mixArtWrap}>
              <Image source={{ uri: mix.image }} style={styles.mixArt as ImageStyle} />
              <Text style={styles.mixNumber}>0{index + 1}</Text>
              <View style={styles.mixPlay}><Icon name="play" size={14} color="#322e35" /></View>
            </View>
            <Text style={[styles.mixTitle, { color: theme.ink }]} numberOfLines={1}>{mix.title}</Text>
            <Text style={[styles.mixSubtitle, { color: theme.muted }]} numberOfLines={1}>{mix.subtitle}</Text>
          </Pressable>
        ))}
      </View>

      <SectionHeading kicker="FROM YOUR DEMO CATALOG" title="Made to play" theme={theme}
        action="Find more" onAction={onOpenSearch} />
      {loading && <Text style={[styles.bodyMuted, { color: theme.muted }]}>Loading your music…</Text>}
      {error && <Text style={styles.inlineError}>{error}</Text>}
      {!loading && tracks.slice(0, 4).map((track) => (
        <TrackRow key={track.id} track={track} theme={theme} onPress={() => onPlay(track.id)} />
      ))}
    </View>
  );
}

function SearchScreen({
  query, results, loading, error, theme, likedIds, onQueryChange, onPlay, onLike,
}: {
  query: string;
  results: TrackState[];
  loading: boolean;
  error: string | null;
  theme: Theme;
  likedIds: string[];
  onQueryChange: (query: string) => void;
  onPlay: (trackId?: string) => void;
  onLike: (trackId: string) => void;
}) {
  const [draftQuery, setDraftQuery] = useState(query);
  console.log("SearchScreen RENDERING -> Results:", results.length, "| draftQuery:", draftQuery);
  const changeQuery = (nextQuery: string) => {
    setDraftQuery(nextQuery);
    onQueryChange(nextQuery);
  };

  return (
    <View>
      <View style={[styles.searchField, { borderColor: theme.line, backgroundColor: theme.surfaceAlt }]}>
        <Icon name="search" size={21} color={theme.muted} />
        <TextInput value={draftQuery} onChangeText={changeQuery} placeholder="Artists, songs, moods…"
          placeholderTextColor={theme.muted} style={[styles.searchInput, { color: theme.ink }]}
          returnKeyType="search" accessibilityLabel="Search music" />
        {draftQuery.length > 0 && (
          <Pressable onPress={() => changeQuery('')} style={styles.clearSearch}
            accessibilityRole="button" accessibilityLabel="Clear search">
            <Icon name="close" size={18} color={theme.muted} />
          </Pressable>
        )}
      </View>

      {draftQuery.trim().length === 0 ? (
        <>
          <View style={styles.exploreFeature}>
            <ImageBackground source={{ uri: photos.neon }} style={styles.exploreImage}>
              <View style={styles.exploreWash} />
              <View style={styles.exploreCopy}>
                <Text style={styles.lightKicker}>ARTIST TO WATCH</Text>
                <Text style={styles.exploreTitle}>Meet the sound{'\n'}after midnight.</Text>
                <Text style={styles.exploreDescription}>Get to know Nova Rue through five essential tracks.</Text>
                <Pressable style={styles.spotlightButton} onPress={() => onPlay(results[0]?.id)}
                  accessibilityRole="button">
                  <Text style={styles.spotlightText}>Play spotlight</Text>
                  <Icon name="play" size={14} color="#342d38" />
                </Pressable>
              </View>
            </ImageBackground>
          </View>
          <SectionHeading kicker="FIND YOUR FREQUENCY" title="Browse by feeling" theme={theme} />
          <View style={styles.genreGrid}>
            {genres.map((genre) => (
              <Pressable key={genre.name} style={[styles.genreCard, { backgroundColor: genre.color }]}
                onPress={() => changeQuery(genre.name)} accessibilityRole="button">
                <Icon name={genre.icon} size={24} color="#514857" />
                <Text style={styles.genreTitle}>{genre.name}</Text>
                <Text style={styles.genreNote}>{genre.note}</Text>
              </Pressable>
            ))}
          </View>
          <SectionHeading kicker="WHAT'S HAPPENING" title="Fresh this week" theme={theme} />
          {mixes.map((mix, index) => (
            <Pressable key={mix.title} style={styles.chartRow} onPress={() => onPlay()}>
              <Text style={[styles.chartRank, { color: theme.muted }]}>{index + 1}</Text>
              <Image source={{ uri: mix.image }} style={styles.chartImage as ImageStyle} />
              <View style={styles.flexText}>
                <Text style={[styles.trackTitle, { color: theme.ink }]}>{mix.title}</Text>
                <Text style={[styles.trackSubtitle, { color: theme.muted }]}>{mix.subtitle}</Text>
              </View>
              <Icon name="play" size={17} color={theme.ink} />
            </Pressable>
          ))}
        </>
      ) : (
        <View style={styles.searchResults}>
          <Text style={[styles.resultsTitle, { color: theme.ink }]}>
            {loading ? 'Searching…' : `${results.length} ${results.length === 1 ? 'result' : 'results'}`}
          </Text>
          {error && <Text style={styles.inlineError}>{error}</Text>}
          {!loading && results.length === 0 && !error && (
            <Text style={[styles.bodyMuted, { color: theme.muted }]}>No matches yet. Try a song, artist, or album.</Text>
          )}
          {results.map((track) => (
            <TrackRow key={track.id} track={track} theme={theme} onPress={() => onPlay(track.id)}
              liked={likedIds.includes(track.id)} onLike={() => onLike(track.id)} />
          ))}
        </View>
      )}
    </View>
  );
}

function LibraryScreen({
  tracks, likedCount, cachedCount, likedIds, activeFilter, theme, onFilterChange, onPlay, onLike,
}: {
  tracks: TrackState[];
  likedCount: number;
  cachedCount: number;
  likedIds: string[];
  activeFilter: string;
  theme: Theme;
  onFilterChange: (filter: string) => void;
  onPlay: (trackId?: string) => void;
  onLike: (trackId: string) => void;
}) {
  return (
    <View>
      <View style={styles.libraryOverview}>
        <View style={styles.flexText}>
          <Text style={[styles.sectionKicker, { color: theme.muted }]}>YOUR COLLECTION</Text>
          <Text style={[styles.libraryTitle, { color: theme.ink }]}>Everything you love,{' '}
            <Text style={styles.heroItalic}>kept close.</Text>
          </Text>
        </View>
        <View style={styles.libraryCount}>
          <Text style={[styles.libraryCountNumber, { color: theme.ink }]}>{tracks.length}</Text>
          <Text style={[styles.trackSubtitle, { color: theme.muted }]}>tracks</Text>
        </View>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.libraryFilters}>
        {libraryFilters.map((filter) => (
          <Pressable key={filter} onPress={() => onFilterChange(filter)}
            style={[styles.libraryFilter, { borderColor: theme.line, backgroundColor: theme.surfaceAlt },
              activeFilter === filter && styles.libraryFilterActive]}
            accessibilityRole="button" accessibilityState={{ selected: activeFilter === filter }}>
            <Text style={[styles.libraryFilterText, { color: theme.muted }, activeFilter === filter && styles.filterTextActive]}>
              {filter}
            </Text>
          </Pressable>
        ))}
      </ScrollView>

      <Pressable style={styles.likedBanner} onPress={() => tracks[0] && onPlay(tracks[0].id)}
        accessibilityRole="button" accessibilityLabel="Play liked songs">
        <View style={styles.likedHeart}><Icon name="heart" size={28} color="#684d8d" /></View>
        <View style={styles.flexText}>
          <Text style={styles.likedKicker}>AUTOMATIC PLAYLIST</Text>
          <Text style={styles.likedTitle}>Liked songs</Text>
          <Text style={styles.likedMeta}>{likedCount} {likedCount === 1 ? 'track' : 'tracks'} saved</Text>
        </View>
        <View style={styles.likedPlay}><Icon name="play" size={17} color="#fff" /></View>
      </Pressable>

      <SectionHeading kicker={activeFilter === 'Downloaded' ? 'AVAILABLE OFFLINE' : 'RECENTLY ADDED'}
        title={activeFilter === 'Downloaded' ? `${cachedCount} downloaded` : 'Back on the shelf'} theme={theme} />
      {tracks.length === 0 ? (
        <View style={[styles.emptyState, { backgroundColor: theme.surfaceAlt }]}>
          <Icon name={activeFilter === 'Downloaded' ? 'download' : 'library'} size={27} color={theme.muted} />
          <Text style={[styles.emptyTitle, { color: theme.ink }]}>
            {activeFilter === 'Liked' ? 'No liked tracks yet' : activeFilter === 'Downloaded' ? 'Nothing saved offline' : 'Your library is ready'}
          </Text>
          <Text style={[styles.bodyMuted, { color: theme.muted }]}>
            {activeFilter === 'Liked' ? 'Tap the heart beside a track to keep it here.' : 'Play a demo track to add it to your collection.'}
          </Text>
        </View>
      ) : (
        tracks.map((track, index) => (
          <TrackRow key={track.id} track={track} theme={theme} onPress={() => onPlay(track.id)}
            liked={likedIds.includes(track.id)} onLike={() => onLike(track.id)} index={index} />
        ))
      )}
    </View>
  );
}

function SettingsScreen({
  cacheCount, isClearing, cacheError, autoCacheOnPlay, isSavingSettings, settingsError,
  theme, onClear, onAutoCacheChange,
}: {
  cacheCount: number;
  isClearing: boolean;
  cacheError: string | null;
  autoCacheOnPlay: boolean;
  isSavingSettings: boolean;
  settingsError: string | null;
  theme: Theme;
  onClear: () => void;
  onAutoCacheChange: (enabled: boolean) => void;
}) {
  return (
    <View>
      <View style={styles.settingsIntro}>
        <Text style={[styles.settingsTitle, { color: theme.ink }]}>Listening, your way.</Text>
        <Text style={[styles.bodyMuted, { color: theme.muted }]}>Manage offline storage and playback preferences.</Text>
      </View>
      <Text style={[styles.sectionKicker, { color: theme.muted }]}>OFFLINE LISTENING</Text>
      <View style={[styles.settingsCard, { backgroundColor: theme.surface, borderColor: theme.line }]}>
        <View style={styles.settingsRow}>
          <View style={styles.settingsIcon}><Icon name="download" size={21} color="#6a5584" /></View>
          <View style={styles.flexText}>
            <Text style={[styles.settingsRowTitle, { color: theme.ink }]}>Automatic caching</Text>
            <Text style={[styles.settingsDescription, { color: theme.muted }]}>Tracks are saved locally when playback begins.</Text>
          </View>
          <Switch
            value={autoCacheOnPlay}
            onValueChange={onAutoCacheChange}
            disabled={isSavingSettings}
            trackColor={{ false: '#b8b2bf', true: '#8a6ab0' }}
            thumbColor={autoCacheOnPlay ? '#fff' : '#f4f1f7'}
            accessibilityLabel="Automatically cache tracks when playback starts"
          />
        </View>
        {settingsError && <Text style={styles.inlineError}>{settingsError}</Text>}
        <View style={[styles.settingsDivider, { backgroundColor: theme.line }]} />
        <View style={styles.settingsRow}>
          <View style={styles.settingsIcon}><Icon name="library" size={21} color="#6a5584" /></View>
          <View style={styles.flexText}>
            <Text style={[styles.settingsRowTitle, { color: theme.ink }]}>Saved for offline</Text>
            <Text style={[styles.settingsDescription, { color: theme.muted }]}>{cacheCount} {cacheCount === 1 ? 'track' : 'tracks'} in your cache</Text>
          </View>
        </View>
        <Pressable style={[styles.clearCacheButton, isClearing && styles.disabledButton]}
          onPress={onClear} disabled={isClearing || cacheCount === 0} accessibilityRole="button">
          <Icon name="trash" size={17} color={cacheCount > 0 ? '#754f5b' : theme.muted} />
          <Text style={[styles.clearCacheText, cacheCount > 0 ? styles.clearCacheTextEnabled : styles.clearCacheTextDisabled,
            !cacheCount && { color: theme.muted }]}>
            {isClearing ? 'Clearing cache…' : 'Clear cached tracks'}
          </Text>
        </Pressable>
        {cacheError && <Text style={styles.inlineError}>{cacheError}</Text>}
      </View>

      <Text style={[styles.sectionKicker, styles.settingsSectionLabel, { color: theme.muted }]}>PLAYBACK</Text>
      <View style={[styles.settingsCard, { backgroundColor: theme.surface, borderColor: theme.line }]}>
        <View style={styles.settingsRow}>
          <View style={styles.settingsIcon}><Icon name="wave" size={21} color="#6a5584" /></View>
          <View style={styles.flexText}>
            <Text style={[styles.settingsRowTitle, { color: theme.ink }]}>Audio quality</Text>
            <Text style={[styles.settingsDescription, { color: theme.muted }]}>Source default for this local demo.</Text>
          </View>
          <Text style={[styles.settingsValue, { color: theme.muted }]}>Auto</Text>
        </View>
      </View>

      <Text style={[styles.settingsFootnote, { color: theme.muted }]}>
        MavvMusic is using a local demo catalog. Online music providers are not connected in this build.
      </Text>
    </View>
  );
}

type Theme = { background: string; surface: string; surfaceAlt: string; ink: string; muted: string; line: string };

function SectionHeading({
  kicker, title, theme, action, actionIcon, onAction,
}: {
  kicker: string;
  title: string;
  theme: Theme;
  action?: string;
  actionIcon?: IconName;
  onAction?: () => void;
}) {
  return (
    <View style={styles.sectionHeading}>
      <View style={styles.flexText}>
        <Text style={[styles.sectionKicker, { color: theme.muted }]}>{kicker}</Text>
        <Text style={[styles.sectionTitle, { color: theme.ink }]}>{title}</Text>
      </View>
      {action && (
        <Pressable style={styles.textAction} onPress={onAction} accessibilityRole="button">
          <Text style={[styles.actionText, { color: theme.muted }]}>{action}</Text>
          <Icon name="arrow" size={15} color={theme.muted} />
        </Pressable>
      )}
      {actionIcon && (
        <Pressable style={[styles.roundPlus, { borderColor: theme.line, backgroundColor: theme.surfaceAlt }]}
          onPress={onAction} accessibilityRole="button" accessibilityLabel="Create playlist">
          <Icon name={actionIcon} size={18} color={theme.ink} />
        </Pressable>
      )}
    </View>
  );
}

function TrackRow({
  track, theme, onPress, liked, onLike, index,
}: {
  track: TrackState;
  theme: Theme;
  onPress: () => void;
  liked?: boolean;
  onLike?: () => void;
  index?: number;
}) {
  return (
    <View style={styles.trackRow}>
      <Pressable style={styles.trackMain} onPress={onPress} accessibilityRole="button"
        accessibilityLabel={`Play ${track.title} by ${track.artist}`}>
        <Image source={{ uri: track.artworkUri ?? photos.album }} style={styles.trackArtwork as ImageStyle} />
        <View style={styles.flexText}>
          <Text style={[styles.trackTitle, { color: theme.ink }]} numberOfLines={1}>{track.title}</Text>
          <Text style={[styles.trackSubtitle, { color: theme.muted }]} numberOfLines={1}>{track.artist} · {track.album}</Text>
        </View>
      </Pressable>
      {typeof index === 'number' && <Text style={[styles.listIndex, { color: theme.muted }]}>{String(index + 1).padStart(2, '0')}</Text>}
      {onLike && (
        <Pressable onPress={onLike} style={styles.rowAction} accessibilityRole="button"
          accessibilityLabel={liked ? 'Remove from liked songs' : 'Add to liked songs'}>
          <Icon name="heart" size={18} color={liked ? '#7955af' : theme.muted} />
        </Pressable>
      )}
    </View>
  );
}

function MiniPlayer({
  track, isPlaying, liked, theme, onOpen, onToggle, onNext, onLike,
}: {
  track: TrackState | null;
  isPlaying: boolean;
  liked: boolean;
  theme: Theme;
  onOpen: () => void;
  onToggle: () => void;
  onNext: () => void;
  onLike: () => void;
}) {
  return (
    <View style={[styles.miniPlayer, { backgroundColor: theme.surface, borderColor: theme.line }]}>
      <Pressable onPress={onOpen} style={styles.miniArtworkButton} accessibilityRole="button" accessibilityLabel="Open player">
        {track ? <Image source={{ uri: track.artworkUri ?? photos.album }} style={styles.miniArtwork as ImageStyle} /> : <View style={styles.miniArtworkPlaceholder} />}
        {isPlaying && <View style={styles.playingOverlay}><Icon name="wave" size={20} color="#fff" /></View>}
      </Pressable>
      <Pressable onPress={onOpen} style={styles.miniTrackMeta} accessibilityRole="button">
        <Text style={[styles.miniTrackTitle, { color: theme.ink }]} numberOfLines={1}>{track?.title ?? 'Choose a track'}</Text>
        <Text style={[styles.miniTrackSubtitle, { color: theme.muted }]} numberOfLines={1}>
          {track ? `${track.artist} · ${track.album}` : 'Your local demo catalog'}
        </Text>
      </Pressable>
      <Pressable onPress={onLike} style={styles.miniIconButton} accessibilityRole="button"
        accessibilityLabel={liked ? 'Unlike track' : 'Like track'} disabled={!track}>
        <Icon name="heart" size={19} color={liked ? '#7955af' : theme.muted} />
      </Pressable>
      <Pressable onPress={onToggle} style={styles.miniPlayButton} accessibilityRole="button"
        accessibilityLabel={isPlaying ? 'Pause' : 'Play'} disabled={!track}>
        <Icon name={isPlaying ? 'pause' : 'play'} size={19} color="#fff" />
      </Pressable>
      <Pressable onPress={onNext} style={styles.miniNextButton} accessibilityRole="button" accessibilityLabel="Next track">
        <Icon name="next" size={18} color={theme.ink} />
      </Pressable>
      <View style={styles.miniProgressTrack}>
        <View style={[styles.miniProgressFill, isPlaying ? styles.miniProgressActive : styles.miniProgressPaused]} />
      </View>
    </View>
  );
}

function IconButton({
  label, onPress, theme, children,
}: {
  label: string;
  onPress: () => void;
  theme: Theme;
  children: React.ReactNode;
}) {
  return (
    <Pressable style={[styles.iconButton, { borderColor: theme.line, backgroundColor: theme.surfaceAlt }]}
      onPress={onPress} accessibilityRole="button" accessibilityLabel={label}>
      {children}
    </Pressable>
  );
}

function NavButton({
  item, active, onPress, theme, expanded = false,
}: {
  item: { label: Destination; icon: IconName };
  active: boolean;
  onPress: () => void;
  theme: Theme;
  expanded?: boolean;
}) {
  return (
    <Pressable onPress={onPress} style={[
      expanded ? styles.railButton : styles.navButton,
      active && (expanded ? styles.railButtonActive : styles.navButtonActive),
    ]} accessibilityRole="tab" accessibilityState={{ selected: active }} accessibilityLabel={item.label}>
      <Icon name={item.icon} size={20} color={active ? '#fff' : theme.muted} />
      <Text style={[
        expanded ? styles.railLabel : styles.navLabel,
        active ? styles.navLabelActive : { color: theme.muted },
      ]}>{item.label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1 },
  layout: { flex: 1 },
  expandedLayout: { flexDirection: 'row' },
  main: { flex: 1 },
  expandedMain: { maxWidth: 980, width: '100%', alignSelf: 'center' },
  rail: { width: 86, borderRightWidth: StyleSheet.hairlineWidth, alignItems: 'center', paddingTop: 16 },
  brandMark: { fontSize: 23, fontWeight: '900', marginBottom: 24 },
  railButton: { width: 68, minHeight: 64, alignItems: 'center', justifyContent: 'center', gap: 5, marginBottom: 8, borderRadius: 22 },
  railButtonActive: { backgroundColor: '#332f39' },
  railLabel: { fontSize: 10, fontWeight: '700' },
  topBar: { paddingHorizontal: 20, paddingTop: 8, paddingBottom: 6, borderBottomWidth: StyleSheet.hairlineWidth },
  topBarRow: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  pageTitle: { fontSize: 28, fontWeight: '800', letterSpacing: -0.8 },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  iconButton: { width: 48, height: 48, borderWidth: 1, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  avatar: { width: 48, height: 48, marginLeft: 2, padding: 3, borderRadius: 24, backgroundColor: '#c9afe7' },
  avatarImage: { width: '100%', height: '100%', borderRadius: 22, borderWidth: 2, borderColor: '#f8f5fa' },
  onlineDot: { position: 'absolute', right: 0, bottom: 1, width: 11, height: 11, borderRadius: 6, borderWidth: 2, borderColor: '#f8f5fa', backgroundColor: '#4ac08a' },
  filterRow: { alignItems: 'center', gap: 8, paddingTop: 10, paddingBottom: 3, paddingRight: 20 },
  filterPill: { minHeight: 48, paddingHorizontal: 15, borderWidth: 1, borderRadius: 25, justifyContent: 'center' },
  filterPillActive: { borderColor: '#302d35', backgroundColor: '#302d35' },
  filterText: { fontSize: 12, fontWeight: '600' },
  filterTextActive: { color: '#fff' },
  content: { paddingHorizontal: 20, paddingTop: 25, maxWidth: 800, width: '100%', alignSelf: 'center' },
  contentCompact: { paddingBottom: 190 },
  contentExpanded: { paddingBottom: 150 },
  welcome: { marginBottom: 20 },
  welcomeTitle: { fontSize: 25, fontWeight: '700', letterSpacing: -0.5 },
  bodyMuted: { marginTop: 5, fontSize: 13, lineHeight: 19 },
  heroCard: { height: 365, borderRadius: 32, overflow: 'hidden', backgroundColor: '#e5d8f4' },
  heroImage: { flex: 1, justifyContent: 'center' },
  heroImageStyle: { opacity: 0.62 },
  heroWash: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(232,222,247,0.78)' },
  heroCopy: { width: '68%', padding: 23, zIndex: 2 },
  badge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 5, paddingVertical: 7, paddingHorizontal: 10, borderRadius: 18, backgroundColor: 'rgba(255,255,255,0.68)' },
  badgeText: { color: '#6a5877', fontSize: 9, fontWeight: '800', letterSpacing: 0.8 },
  heroTitle: { marginTop: 20, color: '#25212b', fontSize: 40, fontWeight: '700', lineHeight: 43, letterSpacing: -1.6 },
  heroItalic: { color: '#7154a6', fontStyle: 'italic', fontWeight: '500' },
  heroDescription: { marginTop: 12, maxWidth: 215, color: '#71697a', fontSize: 12, lineHeight: 18 },
  listenButton: { minHeight: 48, alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 20, paddingVertical: 6, paddingLeft: 16, paddingRight: 7, borderRadius: 25, backgroundColor: '#332f39' },
  listenText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  listenPlay: { width: 34, height: 34, borderRadius: 18, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' },
  trackCount: { position: 'absolute', right: 17, bottom: 17, paddingHorizontal: 11, paddingVertical: 8, borderRadius: 18, backgroundColor: 'rgba(37,29,41,0.58)' },
  trackCountText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  sectionHeading: { minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 30, marginBottom: 13 },
  sectionKicker: { fontSize: 9, fontWeight: '800', letterSpacing: 1.4 },
  sectionTitle: { marginTop: 4, fontSize: 21, fontWeight: '700', letterSpacing: -0.4 },
  flexText: { flex: 1, minWidth: 0 },
  textAction: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 5, paddingLeft: 8 },
  actionText: { fontSize: 11, fontWeight: '600' },
  quickRow: { gap: 12, paddingVertical: 1, paddingRight: 20 },
  quickCard: { width: 150, height: 157, padding: 15, borderRadius: 24, justifyContent: 'flex-end' },
  quickIcon: { position: 'absolute', top: 15, left: 15, width: 50, height: 50, borderRadius: 17, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.58)' },
  quickTitle: { color: '#37313c', fontSize: 13, fontWeight: '700' },
  quickSubtitle: { marginTop: 3, color: '#625a69', fontSize: 10 },
  roundPlus: { width: 48, height: 48, borderWidth: 1, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  mixGrid: { flexDirection: 'row', gap: 11 },
  mixCard: { flex: 1, minWidth: 0 },
  mixArtWrap: { aspectRatio: 0.91, borderRadius: 20, overflow: 'hidden', backgroundColor: '#ded4e7' },
  mixArt: { width: '100%', height: '100%' },
  mixNumber: { position: 'absolute', top: 10, left: 10, color: 'rgba(255,255,255,0.92)', fontSize: 10, fontWeight: '700' },
  mixPlay: { position: 'absolute', right: 9, bottom: 9, width: 34, height: 34, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.92)' },
  mixTitle: { marginTop: 9, fontSize: 12, fontWeight: '700' },
  mixSubtitle: { marginTop: 3, fontSize: 10 },
  trackRow: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 7 },
  trackMain: { flex: 1, minWidth: 0, minHeight: 60, flexDirection: 'row', alignItems: 'center', gap: 12 },
  trackArtwork: { width: 54, height: 54, borderRadius: 15, backgroundColor: '#e2d9e8' },
  trackTitle: { fontSize: 13, fontWeight: '700' },
  trackSubtitle: { marginTop: 4, fontSize: 10 },
  rowAction: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  listIndex: { paddingHorizontal: 8, fontSize: 10, fontWeight: '700' },
  inlineError: { marginTop: 10, color: '#9c3d46', fontSize: 12, lineHeight: 18 },
  searchField: { minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: 11, paddingHorizontal: 15, borderWidth: 1, borderRadius: 19 },
  searchInput: { flex: 1, minHeight: 52, fontSize: 14 },
  clearSearch: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  exploreFeature: { height: 350, marginTop: 19, overflow: 'hidden', borderRadius: 31, backgroundColor: '#33283d' },
  exploreImage: { flex: 1 },
  exploreWash: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(34,24,40,0.62)' },
  exploreCopy: { width: '74%', paddingTop: 45, paddingLeft: 24 },
  lightKicker: { color: '#e4d7ed', fontSize: 9, fontWeight: '800', letterSpacing: 1.2 },
  exploreTitle: { marginTop: 13, color: '#fff', fontSize: 31, fontWeight: '700', lineHeight: 36, letterSpacing: -0.8 },
  exploreDescription: { marginTop: 12, color: 'rgba(255,255,255,0.82)', fontSize: 12, lineHeight: 18 },
  spotlightButton: { minHeight: 46, alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 9, marginTop: 18, paddingHorizontal: 14, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.94)' },
  spotlightText: { color: '#342d38', fontSize: 11, fontWeight: '700' },
  genreGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  genreCard: { width: '48%', minHeight: 135, flexGrow: 1, padding: 16, borderRadius: 22, justifyContent: 'flex-end' },
  genreTitle: { marginTop: 18, color: '#332f39', fontSize: 13, fontWeight: '700' },
  genreNote: { marginTop: 3, color: 'rgba(51,47,57,0.68)', fontSize: 10 },
  chartRow: { minHeight: 68, flexDirection: 'row', alignItems: 'center', gap: 11, paddingHorizontal: 8 },
  chartRank: { width: 18, textAlign: 'center', fontSize: 11, fontWeight: '700' },
  chartImage: { width: 46, height: 46, borderRadius: 14 },
  searchResults: { marginTop: 23 },
  resultsTitle: { marginBottom: 10, fontSize: 18, fontWeight: '700' },
  libraryOverview: { flexDirection: 'row', alignItems: 'flex-end', gap: 12, paddingVertical: 8, marginBottom: 12 },
  libraryTitle: { marginTop: 7, fontSize: 29, fontWeight: '700', lineHeight: 34, letterSpacing: -0.8 },
  libraryCount: { alignItems: 'flex-end', paddingBottom: 5 },
  libraryCountNumber: { fontSize: 22, fontWeight: '800' },
  libraryFilters: { gap: 7, paddingBottom: 18, paddingRight: 20 },
  libraryFilter: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 13, borderWidth: 1, borderRadius: 24 },
  libraryFilterActive: { borderColor: '#353039', backgroundColor: '#353039' },
  libraryFilterText: { fontSize: 11, fontWeight: '600' },
  likedBanner: { minHeight: 102, flexDirection: 'row', alignItems: 'center', gap: 13, padding: 13, borderRadius: 24, backgroundColor: '#e2d6f1' },
  likedHeart: { width: 62, height: 62, borderRadius: 19, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.55)' },
  likedKicker: { color: '#675b72', fontSize: 8, fontWeight: '800', letterSpacing: 0.8 },
  likedTitle: { marginTop: 4, color: '#332f39', fontSize: 16, fontWeight: '700' },
  likedMeta: { marginTop: 3, color: '#635a69', fontSize: 10 },
  likedPlay: { width: 42, height: 42, borderRadius: 22, alignItems: 'center', justifyContent: 'center', backgroundColor: '#37313c' },
  emptyState: { alignItems: 'center', padding: 25, borderRadius: 23 },
  emptyTitle: { marginTop: 13, fontSize: 15, fontWeight: '700' },
  settingsIntro: { marginBottom: 26 },
  settingsTitle: { fontSize: 26, fontWeight: '700', letterSpacing: -0.6 },
  settingsCard: { marginTop: 11, paddingHorizontal: 15, paddingVertical: 8, borderWidth: 1, borderRadius: 22 },
  settingsRow: { minHeight: 76, flexDirection: 'row', alignItems: 'center', gap: 12 },
  settingsIcon: { width: 44, height: 44, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: '#e8dff2' },
  settingsRowTitle: { fontSize: 13, fontWeight: '700' },
  settingsDescription: { marginTop: 4, fontSize: 11, lineHeight: 16 },
  settingsDivider: { height: StyleSheet.hairlineWidth, marginLeft: 56 },
  clearCacheButton: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 9, marginVertical: 6, paddingHorizontal: 10, borderRadius: 14, backgroundColor: 'rgba(156,61,70,0.08)' },
  clearCacheText: { fontSize: 12, fontWeight: '700' },
  clearCacheTextEnabled: { color: '#754f5b' },
  clearCacheTextDisabled: { color: '#726b78' },
  disabledButton: { opacity: 0.55 },
  settingsSectionLabel: { marginTop: 27 },
  settingsValue: { fontSize: 12, fontWeight: '700' },
  settingsFootnote: { marginTop: 24, fontSize: 11, lineHeight: 17 },
  mobileDock: { position: 'absolute', left: 12, right: 12, gap: 8, maxWidth: 704, alignSelf: 'center' },
  tabletPlayer: { position: 'absolute', left: 16, right: 16 },
  miniPlayer: { position: 'relative', height: 74, flexDirection: 'row', alignItems: 'center', gap: 2, paddingHorizontal: 8, borderWidth: 1, borderRadius: 23, overflow: 'hidden' },
  miniArtworkButton: { width: 54, height: 54, borderRadius: 16, overflow: 'hidden', backgroundColor: '#e1d8e7' },
  miniArtwork: { width: '100%', height: '100%' },
  miniArtworkPlaceholder: { flex: 1, backgroundColor: '#ded4e7' },
  playingOverlay: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(31,25,34,0.42)' },
  miniTrackMeta: { flex: 1, minWidth: 0, paddingHorizontal: 8 },
  miniTrackTitle: { fontSize: 12, fontWeight: '700' },
  miniTrackSubtitle: { marginTop: 3, fontSize: 9 },
  miniIconButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  miniPlayButton: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: '#312e35' },
  miniNextButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  miniProgressTrack: { position: 'absolute', right: 18, bottom: 0, left: 18, height: 2, backgroundColor: 'rgba(71,64,77,0.1)' },
  miniProgressFill: { height: '100%', backgroundColor: '#7c62a8' },
  miniProgressActive: { width: '48%' },
  miniProgressPaused: { width: 0 },
  bottomNav: { minHeight: 62, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around', paddingHorizontal: 4, borderWidth: 1, borderRadius: 24 },
  navButton: { flex: 1, minWidth: 56, minHeight: 54, flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, borderRadius: 20 },
  navButtonActive: { backgroundColor: '#332f39' },
  navLabel: { fontSize: 9, fontWeight: '700' },
  navLabelActive: { color: '#fff' },
  modalScrim: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(22,18,27,0.55)' },
  playerSheet: { minHeight: '75%', alignItems: 'center', paddingTop: 12, paddingHorizontal: 26, paddingBottom: 30, borderTopLeftRadius: 30, borderTopRightRadius: 30 },
  sheetHandle: { width: 36, height: 4, borderRadius: 2, backgroundColor: '#bdb5c4' },
  closeButton: { position: 'absolute', top: 22, right: 20, width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 24 },
  sheetArtwork: { width: 245, height: 245, marginTop: 28, borderRadius: 28, backgroundColor: '#ded4e7' },
  sheetTitle: { maxWidth: '100%', marginTop: 22, fontSize: 23, fontWeight: '700' },
  playerActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 30, marginTop: 21 },
  sheetControl: { width: 52, height: 52, alignItems: 'center', justifyContent: 'center' },
  sheetPlay: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center', borderRadius: 34, backgroundColor: '#312e35' },
  progressTrack: { width: '100%', height: 3, marginTop: 25, overflow: 'hidden', borderRadius: 2, backgroundColor: '#d5ceda' },
  progressFill: { height: '100%', borderRadius: 2, backgroundColor: '#7c62a8' },
  cacheNote: { marginTop: 22, fontSize: 11 },
  snackbar: { position: 'absolute', left: 16, right: 16, minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12, paddingHorizontal: 16, borderRadius: 16, backgroundColor: '#332f39' },
  snackbarText: { flex: 1, color: '#fff', fontSize: 12 },
  snackbarAction: { color: '#e3d8f3', fontSize: 10, fontWeight: '800' },
});

export default function App() {
  return (
    <SafeAreaProvider>
      <AppSurface />
    </SafeAreaProvider>
  );
}
