import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Easing,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  ViewStyle,
  StyleProp,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import {
  DnsProfile,
  DnsSwitcher,
  useDnsSwitcherState,
} from './src/native/DnsSwitcher';

type Tab = 'resolvers' | 'settings';

/**
 * Palette: offwhite-bottle-yellow (design-taste-frontend / tasteskill 2.5.1)
 * 90-95% neutral off-white & stone surfaces with deep bottle-green ink (#0D2A1C)
 * and Signal Lemon Yellow (#F6E016) reserved strictly for the primary action,
 * active status signal, and focal highlights.
 */
const THEME = {
  bg: '#F7F7F5',
  surface: '#EBEBE8',
  card: '#FFFFFF',
  ink: '#0D2A1C',
  inkElevated: '#153A28',
  muted: '#5F6B64',
  mutedOnInk: '#A3B5AC',
  accent: '#F6E016',
  accentInk: '#0D2A1C',
  hairline: 'rgba(13, 42, 28, 0.11)',
  hairlineStrong: 'rgba(13, 42, 28, 0.22)',
  hairlineOnInk: 'rgba(247, 247, 245, 0.12)',
  danger: '#B91C1C',
  dangerBg: '#FEF2F2',
  dangerBorder: 'rgba(185, 28, 28, 0.22)',
} as const;

const RADIUS = {
  lg: 20,
  md: 12,
  sm: 8,
  btn: 12,
} as const;

interface ProviderMeta {
  code: string;
  color: string;
  bg: string;
  tag: string;
  summary: string;
}

function getProviderMeta(profile: DnsProfile): ProviderMeta {
  switch (profile.id) {
    case 'cloudflare':
      return {
        code: 'CF',
        color: '#D9650F',
        bg: '#FFF3E8',
        tag: 'Speed & privacy',
        summary: 'Fastest everyday browsing with privacy protection',
      };
    case 'google':
      return {
        code: 'G',
        color: '#1A73E8',
        bg: '#EDF4FF',
        tag: 'Reliable',
        summary: 'Dependable global connection for everyday apps',
      };
    case 'quad9':
      return {
        code: 'Q9',
        color: '#0D6E6E',
        bg: '#E8F6F6',
        tag: 'Security',
        summary: 'Blocks known malware, spyware, and phishing websites',
      };
    case 'adguard':
      return {
        code: 'AG',
        color: '#237A3B',
        bg: '#EAF7EE',
        tag: 'Ad blocker',
        summary: 'Blocks ads and trackers across browsers and apps',
      };
    case 'controld-ads':
      return {
        code: 'CD',
        color: '#2563EB',
        bg: '#EEF2FF',
        tag: 'Ads & malware',
        summary: 'Filters out intrusive ads, trackers, and harmful sites',
      };
    default: {
      const trimmed = profile.name.trim();
      const initials =
        trimmed.length >= 2
          ? trimmed.slice(0, 2).toUpperCase()
          : (trimmed[0] || 'DNS').toUpperCase();
      return {
        code: initials,
        color: THEME.ink,
        bg: THEME.surface,
        tag: 'Custom',
        summary: 'Custom DNS provider configured on your device',
      };
    }
  }
}

function cleanDisplayName(name: string): string {
  return name.replace(/\s*\(AdBlock\)\s*/i, '').trim();
}

interface ScalePressableProps {
  onPress: () => void;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  containerStyle?: StyleProp<ViewStyle>;
  children: React.ReactNode;
  accessibilityLabel?: string;
  accessibilityRole?: 'button' | 'tab';
}

function ScalePressable({
  onPress,
  disabled,
  style,
  containerStyle,
  children,
  accessibilityLabel,
  accessibilityRole = 'button',
}: ScalePressableProps) {
  const scale = useRef(new Animated.Value(1)).current;

  const handlePressIn = () => {
    if (disabled) {
      return;
    }
    Animated.spring(scale, {
      toValue: 0.984,
      useNativeDriver: true,
      speed: 50,
      bounciness: 0,
    }).start();
  };

  const handlePressOut = () => {
    Animated.spring(scale, {
      toValue: 1,
      useNativeDriver: true,
      speed: 40,
      bounciness: 4,
    }).start();
  };

  return (
    <Pressable
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      disabled={disabled}
      style={containerStyle}
      accessibilityRole={accessibilityRole}
      accessibilityLabel={accessibilityLabel}>
      <Animated.View style={[style, { transform: [{ scale }] }]}>
        {children}
      </Animated.View>
    </Pressable>
  );
}

function RoutePulseDiagram({
  isRunning,
  providerName,
}: {
  isRunning: boolean;
  providerName: string;
}) {
  const pulseAnim = useRef(new Animated.Value(0)).current;
  const [trackWidth, setTrackWidth] = useState(180);

  useEffect(() => {
    if (!isRunning) {
      pulseAnim.stopAnimation();
      pulseAnim.setValue(0);
      return;
    }

    const loop = Animated.loop(
      Animated.timing(pulseAnim, {
        toValue: 1,
        duration: 2800,
        easing: Easing.inOut(Easing.quad),
        useNativeDriver: true,
      }),
    );
    loop.start();
    return () => loop.stop();
  }, [isRunning, pulseAnim]);

  const translateX = pulseAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [0, Math.max(trackWidth - 22, 0)],
  });

  const opacity = pulseAnim.interpolate({
    inputRange: [0, 0.15, 0.85, 1],
    outputRange: [0, 1, 1, 0],
  });

  return (
    <View
      style={[
        styles.diagramPanel,
        isRunning ? styles.diagramPanelActive : styles.diagramPanelIdle,
      ]}>
      <View style={styles.diagramNodesRow}>
        {/* Node 1: Phone */}
        <View style={styles.diagramNodeCol}>
          <View
            style={[
              styles.diagramNodeDot,
              isRunning ? styles.diagramNodeDotInk : styles.diagramNodeDotMuted,
            ]}
          />
          <Text
            style={[
              styles.diagramNodeValue,
              isRunning && styles.textOnInk,
            ]}>
            This phone
          </Text>
        </View>

        {/* Connector Track */}
        <View
          style={styles.diagramTrackContainer}
          onLayout={e => setTrackWidth(e.nativeEvent.layout.width)}>
          <View
            style={[
              styles.diagramTrackLine,
              isRunning ? styles.diagramTrackLineActive : styles.diagramTrackLineIdle,
            ]}
          />
          {isRunning && (
            <Animated.View
              style={[
                styles.diagramPulseBead,
                {
                  opacity,
                  transform: [{ translateX }],
                },
              ]}
            />
          )}
        </View>

        {/* Node 2: Active Provider */}
        <View style={styles.diagramNodeCol}>
          <Text
            style={[
              styles.diagramNodeValue,
              isRunning && styles.textAccentOnInk,
            ]}
            numberOfLines={1}>
            {providerName}
          </Text>
          <View
            style={[
              styles.diagramNodeDot,
              styles.diagramNodeDotRight,
              isRunning ? styles.diagramNodeDotYellow : styles.diagramNodeDotMuted,
            ]}
          />
        </View>
      </View>
    </View>
  );
}

function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" />
      <MainScreen />
    </SafeAreaProvider>
  );
}

function MainScreen() {
  const { state, setState, error, setError } = useDnsSwitcherState();
  const [activeTab, setActiveTab] = useState<Tab>('resolvers');
  const [editorVisible, setEditorVisible] = useState(false);
  const [busy, setBusy] = useState(false);

  // Add/Edit Resolver Form State
  const [editingProfileId, setEditingProfileId] = useState<string | undefined>(
    undefined,
  );
  const [nameInput, setNameInput] = useState('');
  const [primaryDnsInput, setPrimaryDnsInput] = useState('');
  const [secondaryDnsInput, setSecondaryDnsInput] = useState('');
  const [primaryIpv6DnsInput, setPrimaryIpv6DnsInput] = useState('');
  const [secondaryIpv6DnsInput, setSecondaryIpv6DnsInput] = useState('');
  const [portInput, setPortInput] = useState('53');
  const [formError, setFormError] = useState<string | null>(null);

  if (!state) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <View style={styles.loadingMark}>
          <View style={styles.loadingMarkDot} />
        </View>
        <ActivityIndicator size="small" color={THEME.ink} />
        <Text style={styles.loadingText}>Loading LemonDNS</Text>
      </SafeAreaView>
    );
  }

  const handleToggleVpn = async () => {
    if (busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (state.isRunning) {
        await DnsSwitcher.stopVpn();
      } else {
        await DnsSwitcher.startVpn();
      }
    } catch (e: any) {
      setError(e?.message ?? 'Failed to toggle DNS protection');
    } finally {
      setBusy(false);
    }
  };

  const handleSelectProfile = async (profileId: string) => {
    setError(null);
    try {
      const next = await DnsSwitcher.setActiveProfile(profileId);
      setState(next);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to switch DNS provider');
    }
  };

  const openAddProfile = () => {
    setEditingProfileId(undefined);
    setNameInput('');
    setPrimaryDnsInput('');
    setSecondaryDnsInput('');
    setPrimaryIpv6DnsInput('');
    setSecondaryIpv6DnsInput('');
    setPortInput('53');
    setFormError(null);
    setEditorVisible(true);
  };

  const openEditProfile = (profile: DnsProfile) => {
    setEditingProfileId(profile.id);
    setNameInput(profile.name);
    setPrimaryDnsInput(profile.primaryDns);
    setSecondaryDnsInput(profile.secondaryDns || '');
    setPrimaryIpv6DnsInput(profile.primaryIpv6Dns || '');
    setSecondaryIpv6DnsInput(profile.secondaryIpv6Dns || '');
    setPortInput(String(profile.port || 53));
    setFormError(null);
    setEditorVisible(true);
  };

  const handleSaveProfile = async () => {
    setFormError(null);
    const trimmedPort = portInput.trim() || '53';
    const parsedPort = /^\d+$/.test(trimmedPort) ? Number(trimmedPort) : NaN;
    if (!Number.isInteger(parsedPort) || parsedPort < 1 || parsedPort > 65535) {
      setFormError('Port must be a number between 1 and 65535.');
      return;
    }

    try {
      const next = await DnsSwitcher.saveProfile({
        id: editingProfileId,
        name: nameInput,
        primaryDns: primaryDnsInput,
        secondaryDns: secondaryDnsInput,
        primaryIpv6Dns: primaryIpv6DnsInput,
        secondaryIpv6Dns: secondaryIpv6DnsInput,
        port: parsedPort,
      });
      setState(next);
      setEditorVisible(false);
    } catch (e: any) {
      setFormError(e?.message ?? 'Invalid DNS configuration.');
    }
  };

  const handleDeleteProfile = async () => {
    if (!editingProfileId) {
      return;
    }
    setFormError(null);
    try {
      const next = await DnsSwitcher.deleteProfile(editingProfileId);
      setState(next);
      setEditorVisible(false);
    } catch (e: any) {
      setFormError(e?.message ?? 'Cannot delete this provider.');
    }
  };

  const handleToggleStartOnBoot = async (enabled: boolean) => {
    setError(null);
    try {
      const next = await DnsSwitcher.updateSettings({ startOnBoot: enabled });
      setState(next);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to update settings');
    }
  };

  const handleRestoreDefaults = async () => {
    setError(null);
    try {
      const next = await DnsSwitcher.restoreDefaultProfiles();
      setState(next);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to restore default providers');
    }
  };

  const { isRunning, activeProfile } = state;
  const activeMeta = getProviderMeta(activeProfile);
  const activeDisplayName = cleanDisplayName(activeProfile.name);

  return (
    <SafeAreaView style={styles.container}>
      {/* Fixed Header */}
      <View style={styles.header}>
        <View style={styles.brandRow}>
          <View style={styles.brandMark}>
            <View style={styles.brandMarkInner} />
          </View>
          <Text style={styles.brandName}>LemonDNS</Text>
        </View>

        <View
          style={[
            styles.headerStatusChip,
            isRunning ? styles.headerStatusChipLive : styles.headerStatusChipIdle,
          ]}>
          <View
            style={[
              styles.headerStatusDot,
              isRunning ? styles.headerStatusDotLive : styles.headerStatusDotIdle,
            ]}
          />
          <Text
            style={[
              styles.headerStatusText,
              isRunning ? styles.headerStatusTextLive : styles.headerStatusTextIdle,
            ]}>
            {isRunning ? 'Protected' : 'Off'}
          </Text>
        </View>
      </View>

      {error ? (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>{error}</Text>
        </View>
      ) : null}

      {/* Main Content Area */}
      {activeTab === 'resolvers' ? (
        <ScrollView
          style={styles.scrollArea}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}>
          {/* Hero Console: State-Driven Bottle-Ink Inversion when Connected */}
          <View
            style={[
              styles.heroConsole,
              isRunning ? styles.heroConsoleActive : styles.heroConsoleIdle,
            ]}>
            <View style={styles.heroTopRow}>
              <View style={styles.heroStatusLine}>
                <View
                  style={[
                    styles.heroPulseDot,
                    isRunning ? styles.heroPulseDotActive : styles.heroPulseDotIdle,
                  ]}
                />
                <Text
                  style={[
                    styles.heroStatusLabel,
                    isRunning ? styles.textOnInkMuted : styles.textMuted,
                  ]}>
                  {isRunning ? 'Connected' : 'Not connected'}
                </Text>
              </View>

              <Text
                style={[
                  styles.heroBenefitTag,
                  isRunning ? styles.textOnInkMuted : styles.textMuted,
                ]}>
                {activeMeta.tag}
              </Text>
            </View>

            {/* Active Provider Headline & Plain-English Summary */}
            <Text
              style={[
                styles.heroTitle,
                isRunning ? styles.textOnInk : styles.textInk,
              ]}>
              {activeDisplayName}
            </Text>
            <Text
              style={[
                styles.heroSummary,
                isRunning ? styles.textOnInkMuted : styles.textMuted,
              ]}>
              {activeMeta.summary}
            </Text>

            {/* Simple Visual Connection Line */}
            <RoutePulseDiagram
              isRunning={isRunning}
              providerName={activeDisplayName}
            />

            {/* Single Primary Action Button */}
            <ScalePressable
              onPress={handleToggleVpn}
              disabled={busy}
              style={[
                styles.primaryActionBtn,
                isRunning
                  ? styles.primaryActionBtnDisconnect
                  : styles.primaryActionBtnConnect,
              ]}>
              <Text style={styles.primaryActionBtnText}>
                {busy
                  ? 'Updating…'
                  : isRunning
                  ? 'Turn off protection'
                  : 'Turn on protection'}
              </Text>
              <View
                style={[
                  styles.primaryActionArrowBadge,
                  isRunning
                    ? styles.primaryActionArrowDisconnect
                    : styles.primaryActionArrowConnect,
                ]}>
                <Text
                  style={[
                    styles.primaryActionArrowText,
                    isRunning ? styles.textOnInk : styles.textInk,
                  ]}>
                  {isRunning ? '■' : '→'}
                </Text>
              </View>
            </ScalePressable>
          </View>

          {/* Section Header: DNS Providers */}
          <View style={styles.sectionHeader}>
            <View style={styles.sectionHeaderLeft}>
              <Text style={styles.sectionTitle}>DNS providers</Text>
              <Text style={styles.sectionSubtitle}>
                Tap any provider to switch instantly.
              </Text>
            </View>

            <ScalePressable
              onPress={openAddProfile}
              style={styles.newResolverBtn}>
              <Text style={styles.newResolverBtnText}>+ Add custom</Text>
            </ScalePressable>
          </View>

          {/* Clean, Single-Level Provider Cards (No Raw IP Clutter) */}
          <View style={styles.resolverList}>
            {state.profiles.map(profile => {
              const isSelected = profile.id === state.activeProfileId;
              const meta = getProviderMeta(profile);
              const displayName = cleanDisplayName(profile.name);

              return (
                <ScalePressable
                  key={profile.id}
                  onPress={() => handleSelectProfile(profile.id)}
                  style={[
                    styles.resolverCard,
                    isSelected && styles.resolverCardSelected,
                  ]}>
                  <View style={styles.resolverRow}>
                    <View
                      style={[
                        styles.providerTile,
                        { backgroundColor: meta.bg },
                      ]}>
                      <Text
                        style={[
                          styles.providerTileText,
                          { color: meta.color },
                        ]}>
                        {meta.code}
                      </Text>
                    </View>

                    <View style={styles.resolverIdentityCol}>
                      <View style={styles.resolverNameRow}>
                        <Text style={styles.resolverName} numberOfLines={1}>
                          {displayName}
                        </Text>
                        {isSelected ? (
                          <View
                            style={[
                              styles.stateChip,
                              isRunning
                                ? styles.stateChipActive
                                : styles.stateChipSelected,
                            ]}>
                            <Text style={styles.stateChipText}>
                              {isRunning ? 'Active' : 'Selected'}
                            </Text>
                          </View>
                        ) : null}
                      </View>
                      <Text style={styles.resolverSummaryText}>
                        {meta.summary}
                      </Text>
                    </View>

                    {!profile.isBuiltIn ? (
                      <ScalePressable
                        onPress={() => openEditProfile(profile)}
                        style={styles.editResolverBtn}>
                        <Text style={styles.editResolverBtnText}>Edit</Text>
                      </ScalePressable>
                    ) : (
                      <View
                        style={[
                          styles.radioOuter,
                          isSelected && styles.radioOuterSelected,
                        ]}>
                        {isSelected ? <View style={styles.radioInner} /> : null}
                      </View>
                    )}
                  </View>
                </ScalePressable>
              );
            })}
          </View>
        </ScrollView>
      ) : (
        /* Tab 2: Settings (Clean, Layman-Friendly) */
        <ScrollView
          style={styles.scrollArea}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}>
          <View style={styles.settingsPageHeader}>
            <Text style={styles.settingsPageTitle}>Settings</Text>
            <Text style={styles.settingsPageSubtitle}>
              Automatic startup and built-in providers.
            </Text>
          </View>

          <View style={styles.settingsCard}>
            <View style={styles.settingRow}>
              <View style={styles.settingCopyCol}>
                <Text style={styles.settingTitle}>Start when phone turns on</Text>
                <Text style={styles.settingDescription}>
                  Automatically turn on DNS protection with your selected
                  provider whenever you restart your phone.
                </Text>
              </View>
              <Switch
                value={state.settings.startOnBoot}
                onValueChange={handleToggleStartOnBoot}
                trackColor={{
                  false: THEME.surface,
                  true: THEME.accent,
                }}
                thumbColor={THEME.ink}
              />
            </View>

            <View style={styles.hairlineDivider} />

            <View style={styles.settingBlock}>
              <Text style={styles.settingTitle}>Default providers</Text>
              <Text style={styles.settingDescription}>
                Restore the built-in Cloudflare, Google, Quad9, AdGuard, and
                Control D list while keeping any custom providers you added.
              </Text>
              <ScalePressable
                onPress={handleRestoreDefaults}
                style={styles.secondaryBtn}>
                <Text style={styles.secondaryBtnText}>
                  Restore default providers
                </Text>
              </ScalePressable>
            </View>
          </View>
        </ScrollView>
      )}

      {/* Fixed 2-Tab Bar (Providers | Settings) */}
      <View style={styles.tabBar}>
        <ScalePressable
          onPress={() => setActiveTab('resolvers')}
          accessibilityRole="tab"
          containerStyle={styles.tabPressable}
          style={[
            styles.tabItem,
            activeTab === 'resolvers' && styles.tabItemActive,
          ]}>
          <View
            style={[
              styles.tabDot,
              activeTab === 'resolvers' ? styles.tabDotActive : styles.tabDotIdle,
            ]}
          />
          <Text
            style={[
              styles.tabLabel,
              activeTab === 'resolvers'
                ? styles.tabLabelActive
                : styles.tabLabelIdle,
            ]}>
            Providers
          </Text>
          <View
            style={[
              styles.tabCountBadge,
              activeTab === 'resolvers' && styles.tabCountBadgeActive,
            ]}>
            <Text
              style={[
                styles.tabCountText,
                activeTab === 'resolvers' && styles.tabCountTextActive,
              ]}>
              {state.profiles.length}
            </Text>
          </View>
        </ScalePressable>

        <ScalePressable
          onPress={() => setActiveTab('settings')}
          accessibilityRole="tab"
          containerStyle={styles.tabPressable}
          style={[
            styles.tabItem,
            activeTab === 'settings' && styles.tabItemActive,
          ]}>
          <View
            style={[
              styles.tabDot,
              activeTab === 'settings' ? styles.tabDotActive : styles.tabDotIdle,
            ]}
          />
          <Text
            style={[
              styles.tabLabel,
              activeTab === 'settings'
                ? styles.tabLabelActive
                : styles.tabLabelIdle,
            ]}>
            Settings
          </Text>
        </ScalePressable>
      </View>

      {/* Slide-Up Modal Sheet for Add / Edit Custom Provider */}
      <Modal
        visible={editorVisible}
        animationType="slide"
        transparent
        onRequestClose={() => setEditorVisible(false)}>
        <KeyboardAvoidingView
          style={styles.modalBackdrop}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <Pressable
            style={styles.modalDismissArea}
            onPress={() => setEditorVisible(false)}
          />
          <View style={styles.modalSheet}>
            <View style={styles.modalHandle} />

            <View style={styles.modalHeaderRow}>
              <Text style={styles.modalTitle}>
                {editingProfileId ? 'Edit custom DNS' : 'Add custom DNS'}
              </Text>
              <ScalePressable
                onPress={() => setEditorVisible(false)}
                style={styles.modalCloseBtn}>
                <Text style={styles.modalCloseBtnText}>Cancel</Text>
              </ScalePressable>
            </View>

            <ScrollView
              style={styles.modalScroll}
              contentContainerStyle={styles.modalScrollContent}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}>
              {formError ? (
                <View style={styles.formErrorBox}>
                  <Text style={styles.formErrorText}>{formError}</Text>
                </View>
              ) : null}

              <Text style={styles.fieldLabel}>Name</Text>
              <TextInput
                style={styles.textInput}
                placeholder="e.g. NextDNS, Mullvad, Home Wi-Fi"
                placeholderTextColor={THEME.muted}
                value={nameInput}
                onChangeText={setNameInput}
              />

              <View style={styles.fieldRowTwo}>
                <View style={styles.fieldColMain}>
                  <Text style={styles.fieldLabel}>Primary DNS address</Text>
                  <TextInput
                    style={[styles.textInput, styles.textInputMono]}
                    placeholder="1.1.1.1"
                    placeholderTextColor={THEME.muted}
                    keyboardType="numeric"
                    autoCapitalize="none"
                    value={primaryDnsInput}
                    onChangeText={setPrimaryDnsInput}
                  />
                </View>

                <View style={styles.fieldColPort}>
                  <Text style={styles.fieldLabel}>Port</Text>
                  <TextInput
                    style={[styles.textInput, styles.textInputMono]}
                    placeholder="53"
                    placeholderTextColor={THEME.muted}
                    keyboardType="number-pad"
                    value={portInput}
                    onChangeText={setPortInput}
                  />
                </View>
              </View>

              <Text style={styles.fieldLabel}>Secondary address (optional)</Text>
              <TextInput
                style={[styles.textInput, styles.textInputMono]}
                placeholder="1.0.0.1"
                placeholderTextColor={THEME.muted}
                keyboardType="numeric"
                autoCapitalize="none"
                value={secondaryDnsInput}
                onChangeText={setSecondaryDnsInput}
              />

              <Text style={styles.fieldLabel}>IPv6 address (optional)</Text>
              <TextInput
                style={[styles.textInput, styles.textInputMono]}
                placeholder="2606:4700:4700::1111"
                placeholderTextColor={THEME.muted}
                autoCapitalize="none"
                value={primaryIpv6DnsInput}
                onChangeText={setPrimaryIpv6DnsInput}
              />

              <Text style={styles.fieldLabel}>
                Secondary IPv6 address (optional)
              </Text>
              <TextInput
                style={[styles.textInput, styles.textInputMono]}
                placeholder="2606:4700:4700::1001"
                placeholderTextColor={THEME.muted}
                autoCapitalize="none"
                value={secondaryIpv6DnsInput}
                onChangeText={setSecondaryIpv6DnsInput}
              />

              <View style={styles.modalActionsBlock}>
                <ScalePressable
                  onPress={handleSaveProfile}
                  style={styles.saveResolverBtn}>
                  <Text style={styles.saveResolverBtnText}>Save provider</Text>
                </ScalePressable>

                {editingProfileId && state.profiles.length > 1 ? (
                  <ScalePressable
                    onPress={handleDeleteProfile}
                    style={styles.deleteResolverBtn}>
                    <Text style={styles.deleteResolverBtnText}>
                      Delete provider
                    </Text>
                  </ScalePressable>
                ) : null}
              </View>
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const MONO_FONT = Platform.select({
  ios: 'Menlo',
  android: 'monospace',
  default: 'monospace',
});

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    backgroundColor: THEME.bg,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingMark: {
    width: 36,
    height: 36,
    borderRadius: RADIUS.sm,
    backgroundColor: THEME.ink,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  loadingMarkDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: THEME.accent,
  },
  loadingText: {
    color: THEME.ink,
    fontSize: 15,
    fontWeight: '500',
    letterSpacing: -0.2,
  },
  container: {
    flex: 1,
    backgroundColor: THEME.bg,
  },

  /* Header */
  header: {
    height: 60,
    paddingHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: THEME.bg,
    borderBottomWidth: 1,
    borderBottomColor: THEME.hairline,
  },
  brandRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  brandMark: {
    width: 24,
    height: 24,
    borderRadius: 7,
    backgroundColor: THEME.ink,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  brandMarkInner: {
    width: 9,
    height: 9,
    borderRadius: 4.5,
    backgroundColor: THEME.accent,
  },
  brandName: {
    fontSize: 19,
    fontWeight: '700',
    color: THEME.ink,
    letterSpacing: -0.5,
  },
  headerStatusChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 11,
    paddingVertical: 5,
    borderRadius: RADIUS.sm,
    borderWidth: 1,
  },
  headerStatusChipLive: {
    backgroundColor: THEME.ink,
    borderColor: THEME.ink,
  },
  headerStatusChipIdle: {
    backgroundColor: THEME.surface,
    borderColor: THEME.hairline,
  },
  headerStatusDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    marginRight: 6,
  },
  headerStatusDotLive: {
    backgroundColor: THEME.accent,
  },
  headerStatusDotIdle: {
    backgroundColor: THEME.muted,
  },
  headerStatusText: {
    fontSize: 13,
    fontWeight: '600',
  },
  headerStatusTextLive: {
    color: THEME.bg,
  },
  headerStatusTextIdle: {
    color: THEME.muted,
  },

  /* Error Banner */
  errorBanner: {
    backgroundColor: THEME.dangerBg,
    marginHorizontal: 20,
    marginTop: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    borderColor: THEME.dangerBorder,
  },
  errorBannerText: {
    color: THEME.danger,
    fontSize: 14,
    fontWeight: '500',
    lineHeight: 20,
  },

  /* Scroll Layout */
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 32,
  },

  /* Hero Console */
  heroConsole: {
    borderRadius: RADIUS.lg,
    padding: 24,
    borderWidth: 1,
    marginBottom: 28,
  },
  heroConsoleActive: {
    backgroundColor: THEME.ink,
    borderColor: THEME.ink,
  },
  heroConsoleIdle: {
    backgroundColor: THEME.surface,
    borderColor: THEME.hairlineStrong,
  },
  heroTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  heroStatusLine: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  heroPulseDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  heroPulseDotActive: {
    backgroundColor: THEME.accent,
  },
  heroPulseDotIdle: {
    backgroundColor: THEME.muted,
  },
  heroStatusLabel: {
    fontSize: 13,
    fontWeight: '500',
  },
  heroBenefitTag: {
    fontSize: 13,
    fontWeight: '500',
  },
  heroTitle: {
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.9,
    lineHeight: 36,
    marginBottom: 6,
  },
  heroSummary: {
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 18,
  },

  /* Simple Connection Diagram inside Hero */
  diagramPanel: {
    borderRadius: RADIUS.md,
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderWidth: 1,
    marginBottom: 18,
  },
  diagramPanelActive: {
    backgroundColor: THEME.inkElevated,
    borderColor: THEME.hairlineOnInk,
  },
  diagramPanelIdle: {
    backgroundColor: THEME.bg,
    borderColor: THEME.hairline,
  },
  diagramNodesRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  diagramNodeCol: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 0,
  },
  diagramNodeDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 7,
  },
  diagramNodeDotRight: {
    marginRight: 0,
    marginLeft: 7,
  },
  diagramNodeDotInk: {
    backgroundColor: THEME.bg,
  },
  diagramNodeDotYellow: {
    backgroundColor: THEME.accent,
  },
  diagramNodeDotMuted: {
    backgroundColor: THEME.muted,
  },
  diagramNodeValue: {
    fontSize: 13,
    fontWeight: '600',
    color: THEME.ink,
  },
  diagramTrackContainer: {
    flex: 1,
    height: 16,
    marginHorizontal: 12,
    justifyContent: 'center',
    overflow: 'hidden',
  },
  diagramTrackLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1.5,
  },
  diagramTrackLineActive: {
    backgroundColor: 'rgba(246, 224, 22, 0.35)',
  },
  diagramTrackLineIdle: {
    backgroundColor: THEME.hairlineStrong,
  },
  diagramPulseBead: {
    position: 'absolute',
    left: 0,
    width: 24,
    height: 3,
    borderRadius: 2,
    backgroundColor: THEME.accent,
  },

  /* Hero Primary Button */
  primaryActionBtn: {
    minHeight: 48,
    borderRadius: RADIUS.btn,
    paddingHorizontal: 20,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: THEME.accent,
    borderWidth: 1,
  },
  primaryActionBtnConnect: {
    borderColor: THEME.ink,
  },
  primaryActionBtnDisconnect: {
    borderColor: THEME.accent,
  },
  primaryActionBtnText: {
    fontSize: 16,
    fontWeight: '600',
    letterSpacing: -0.2,
    color: THEME.accentInk,
  },
  primaryActionArrowBadge: {
    width: 28,
    height: 28,
    borderRadius: RADIUS.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryActionArrowConnect: {
    backgroundColor: 'rgba(13, 42, 28, 0.1)',
  },
  primaryActionArrowDisconnect: {
    backgroundColor: THEME.ink,
  },
  primaryActionArrowText: {
    fontSize: 14,
    fontWeight: '700',
  },

  /* Section Header */
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 14,
    gap: 12,
  },
  sectionHeaderLeft: {
    flex: 1,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: THEME.ink,
    letterSpacing: -0.5,
    lineHeight: 24,
  },
  sectionSubtitle: {
    fontSize: 14,
    fontWeight: '400',
    color: THEME.muted,
    marginTop: 2,
  },
  newResolverBtn: {
    minHeight: 42,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: RADIUS.btn,
    backgroundColor: THEME.card,
    borderWidth: 1,
    borderColor: THEME.hairlineStrong,
    justifyContent: 'center',
    alignItems: 'center',
  },
  newResolverBtnText: {
    fontSize: 14,
    fontWeight: '600',
    color: THEME.ink,
  },

  /* Provider Cards List */
  resolverList: {
    gap: 10,
  },
  resolverCard: {
    backgroundColor: THEME.card,
    borderRadius: RADIUS.lg,
    paddingHorizontal: 18,
    paddingVertical: 16,
    borderWidth: 1,
    borderColor: THEME.hairline,
  },
  resolverCardSelected: {
    borderColor: THEME.ink,
  },
  resolverRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  providerTile: {
    width: 42,
    height: 42,
    borderRadius: RADIUS.sm,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: THEME.hairline,
    marginRight: 14,
  },
  providerTileText: {
    fontSize: 14,
    fontWeight: '700',
  },
  resolverIdentityCol: {
    flex: 1,
    paddingRight: 10,
  },
  resolverNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 3,
  },
  resolverName: {
    fontSize: 17,
    fontWeight: '600',
    color: THEME.ink,
    letterSpacing: -0.3,
  },
  stateChip: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  stateChipActive: {
    backgroundColor: THEME.accent,
  },
  stateChipSelected: {
    backgroundColor: THEME.surface,
  },
  stateChipText: {
    fontSize: 12,
    fontWeight: '600',
    color: THEME.ink,
  },
  resolverSummaryText: {
    fontSize: 14,
    color: THEME.muted,
    lineHeight: 19,
  },
  editResolverBtn: {
    minHeight: 40,
    paddingHorizontal: 12,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: RADIUS.sm,
    backgroundColor: THEME.surface,
    borderWidth: 1,
    borderColor: THEME.hairline,
  },
  editResolverBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: THEME.ink,
  },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 1.5,
    borderColor: THEME.hairlineStrong,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOuterSelected: {
    borderColor: THEME.ink,
    backgroundColor: THEME.ink,
  },
  radioInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: THEME.accent,
  },

  /* Settings Tab */
  settingsPageHeader: {
    marginBottom: 18,
  },
  settingsPageTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: THEME.ink,
    letterSpacing: -0.6,
    lineHeight: 30,
  },
  settingsPageSubtitle: {
    fontSize: 15,
    color: THEME.muted,
    marginTop: 2,
  },
  settingsCard: {
    backgroundColor: THEME.card,
    borderRadius: RADIUS.lg,
    padding: 22,
    borderWidth: 1,
    borderColor: THEME.hairline,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  settingCopyCol: {
    flex: 1,
    paddingRight: 16,
  },
  settingBlock: {
    gap: 8,
  },
  settingTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: THEME.ink,
    marginBottom: 4,
    letterSpacing: -0.2,
  },
  settingDescription: {
    fontSize: 15,
    color: THEME.muted,
    lineHeight: 22,
  },
  hairlineDivider: {
    height: 1,
    backgroundColor: THEME.hairline,
    marginVertical: 20,
  },
  secondaryBtn: {
    minHeight: 46,
    marginTop: 8,
    borderRadius: RADIUS.btn,
    backgroundColor: THEME.surface,
    borderWidth: 1,
    borderColor: THEME.hairlineStrong,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 18,
  },
  secondaryBtnText: {
    fontSize: 15,
    fontWeight: '600',
    color: THEME.ink,
  },

  /* 2-Tab Bottom Bar */
  tabBar: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    paddingVertical: 10,
    backgroundColor: THEME.bg,
    borderTopWidth: 1,
    borderTopColor: THEME.hairline,
    gap: 12,
  },
  tabPressable: {
    flex: 1,
  },
  tabItem: {
    minHeight: 46,
    borderRadius: RADIUS.btn,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
    backgroundColor: THEME.surface,
    borderWidth: 1,
    borderColor: 'transparent',
    gap: 8,
  },
  tabItemActive: {
    backgroundColor: THEME.ink,
    borderColor: THEME.ink,
  },
  tabDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
  },
  tabDotActive: {
    backgroundColor: THEME.accent,
  },
  tabDotIdle: {
    backgroundColor: THEME.muted,
  },
  tabLabel: {
    fontSize: 15,
    fontWeight: '600',
  },
  tabLabelActive: {
    color: THEME.bg,
  },
  tabLabelIdle: {
    color: THEME.ink,
  },
  tabCountBadge: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 6,
    backgroundColor: 'rgba(13, 42, 28, 0.08)',
  },
  tabCountBadgeActive: {
    backgroundColor: 'rgba(247, 247, 245, 0.16)',
  },
  tabCountText: {
    fontSize: 13,
    fontWeight: '600',
    color: THEME.muted,
  },
  tabCountTextActive: {
    color: THEME.bg,
  },

  /* Slide-Up Modal Sheet */
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(13, 42, 28, 0.45)',
    justifyContent: 'flex-end',
  },
  modalDismissArea: {
    flex: 1,
  },
  modalSheet: {
    backgroundColor: THEME.bg,
    borderTopLeftRadius: RADIUS.lg,
    borderTopRightRadius: RADIUS.lg,
    paddingTop: 12,
    maxHeight: '88%',
    borderTopWidth: 1,
    borderColor: THEME.hairlineStrong,
  },
  modalHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: THEME.hairlineStrong,
    alignSelf: 'center',
    marginBottom: 12,
  },
  modalHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 14,
    borderBottomWidth: 1,
    borderBottomColor: THEME.hairline,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: THEME.ink,
    letterSpacing: -0.4,
  },
  modalCloseBtn: {
    minHeight: 44,
    paddingHorizontal: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalCloseBtnText: {
    fontSize: 15,
    fontWeight: '500',
    color: THEME.muted,
  },
  modalScroll: {
    paddingHorizontal: 20,
  },
  modalScrollContent: {
    paddingTop: 16,
    paddingBottom: 36,
  },
  formErrorBox: {
    backgroundColor: THEME.dangerBg,
    borderColor: THEME.dangerBorder,
    borderWidth: 1,
    borderRadius: RADIUS.md,
    padding: 12,
    marginBottom: 12,
  },
  formErrorText: {
    color: THEME.danger,
    fontSize: 14,
    fontWeight: '500',
  },
  fieldLabel: {
    fontSize: 13,
    fontWeight: '500',
    color: THEME.muted,
    marginBottom: 6,
    marginTop: 12,
  },
  fieldRowTwo: {
    flexDirection: 'row',
    gap: 12,
  },
  fieldColMain: {
    flex: 1,
  },
  fieldColPort: {
    width: 104,
  },
  textInput: {
    minHeight: 46,
    backgroundColor: THEME.card,
    borderWidth: 1,
    borderColor: THEME.hairlineStrong,
    borderRadius: RADIUS.md,
    paddingHorizontal: 14,
    paddingVertical: 11,
    color: THEME.ink,
    fontSize: 16,
  },
  textInputMono: {
    fontFamily: MONO_FONT,
    fontSize: 15,
  },
  modalActionsBlock: {
    marginTop: 24,
    gap: 12,
  },
  saveResolverBtn: {
    minHeight: 48,
    backgroundColor: THEME.accent,
    borderRadius: RADIUS.btn,
    borderWidth: 1,
    borderColor: THEME.ink,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 13,
  },
  saveResolverBtnText: {
    fontSize: 16,
    fontWeight: '600',
    color: THEME.accentInk,
  },
  deleteResolverBtn: {
    minHeight: 46,
    backgroundColor: THEME.dangerBg,
    borderRadius: RADIUS.btn,
    borderWidth: 1,
    borderColor: THEME.dangerBorder,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
  },
  deleteResolverBtnText: {
    fontSize: 15,
    fontWeight: '600',
    color: THEME.danger,
  },

  /* Shared Typography Helpers */
  textInk: {
    color: THEME.ink,
  },
  textMuted: {
    color: THEME.muted,
  },
  textOnInk: {
    color: THEME.bg,
  },
  textOnInkMuted: {
    color: THEME.mutedOnInk,
  },
  textAccentOnInk: {
    color: THEME.accent,
  },
});

export default App;
