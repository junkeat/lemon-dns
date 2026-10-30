import React, { useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import {
  DnsProfile,
  DnsSwitcher,
  useDnsSwitcherState,
} from './src/native/DnsSwitcher';

type Screen = 'home' | 'editor' | 'settings';

function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" />
      <MainScreen />
    </SafeAreaProvider>
  );
}

function MainScreen() {
  const { state, setState, error, setError } = useDnsSwitcherState();
  const [screen, setScreen] = useState<Screen>('home');
  const [busy, setBusy] = useState(false);

  // Add/Edit Profile Form State
  const [editingProfileId, setEditingProfileId] = useState<string | undefined>(
    undefined,
  );
  const [nameInput, setNameInput] = useState('');
  const [primaryDnsInput, setPrimaryDnsInput] = useState('');
  const [secondaryDnsInput, setSecondaryDnsInput] = useState('');
  const [portInput, setPortInput] = useState('53');
  const [formError, setFormError] = useState<string | null>(null);

  if (!state) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#38BDF8" />
        <Text style={styles.loadingText}>Loading DNS Switcher...</Text>
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
      const next = state.isRunning
        ? await DnsSwitcher.stopVpn()
        : await DnsSwitcher.startVpn();
      setState(next);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to toggle DNS VPN');
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
      setError(e?.message ?? 'Failed to switch DNS profile');
    }
  };

  const openAddProfile = () => {
    setEditingProfileId(undefined);
    setNameInput('');
    setPrimaryDnsInput('');
    setSecondaryDnsInput('');
    setPortInput('53');
    setFormError(null);
    setScreen('editor');
  };

  const openEditProfile = (profile: DnsProfile) => {
    setEditingProfileId(profile.id);
    setNameInput(profile.name);
    setPrimaryDnsInput(profile.primaryDns);
    setSecondaryDnsInput(profile.secondaryDns || '');
    setPortInput(String(profile.port || 53));
    setFormError(null);
    setScreen('editor');
  };

  const handleSaveProfile = async () => {
    setFormError(null);
    const parsedPort = parseInt(portInput.trim() || '53', 10);
    if (isNaN(parsedPort) || parsedPort < 1 || parsedPort > 65535) {
      setFormError('Port must be a number between 1 and 65535.');
      return;
    }

    try {
      const next = await DnsSwitcher.saveProfile({
        id: editingProfileId,
        name: nameInput,
        primaryDns: primaryDnsInput,
        secondaryDns: secondaryDnsInput,
        port: parsedPort,
      });
      setState(next);
      setScreen('home');
    } catch (e: any) {
      setFormError(e?.message ?? 'Invalid profile configuration.');
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
      setScreen('home');
    } catch (e: any) {
      setFormError(e?.message ?? 'Cannot delete this profile.');
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
      setError(e?.message ?? 'Failed to restore default profiles');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Top Navigation Bar */}
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>DNS Switcher</Text>
          <Text style={styles.headerSubtitle}>
            Split-Tunnel • Instant Hot-Swap
          </Text>
        </View>
        <View style={styles.navRow}>
          {screen !== 'home' ? (
            <TouchableOpacity
              style={styles.navButton}
              onPress={() => setScreen('home')}>
              <Text style={styles.navButtonText}>Back</Text>
            </TouchableOpacity>
          ) : (
            <TouchableOpacity
              style={styles.navButton}
              onPress={() => setScreen('settings')}>
              <Text style={styles.navButtonText}>Settings</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {error ? (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>{error}</Text>
        </View>
      ) : null}

      {screen === 'home' && (
        <ScrollView
          style={styles.scrollArea}
          contentContainerStyle={styles.scrollContent}>
          {/* On/Off Control & Status Display Card */}
          <View
            style={[
              styles.statusCard,
              state.isRunning
                ? styles.statusCardActive
                : styles.statusCardInactive,
            ]}>
            <View style={styles.statusRow}>
              <View style={styles.statusIndicatorRow}>
                <View
                  style={[
                    styles.statusDot,
                    state.isRunning ? styles.dotActive : styles.dotInactive,
                  ]}
                />
                <Text style={styles.statusBadgeText}>
                  {state.isRunning ? 'CONNECTED • NO LEAKS' : 'DISCONNECTED'}
                </Text>
              </View>
              <Text style={styles.tunnelTag}>TUN 10.111.222.2/32</Text>
            </View>

            <Text style={styles.activeProfileTitle}>
              {state.activeProfile.name}
            </Text>
            <Text style={styles.activeProfileEndpoints}>
              Primary: {state.activeProfile.primaryDns}:
              {state.activeProfile.port}
              {state.activeProfile.secondaryDns
                ? `  •  Secondary: ${state.activeProfile.secondaryDns}`
                : ''}
            </Text>

            <TouchableOpacity
              style={[
                styles.powerButton,
                state.isRunning
                  ? styles.powerButtonStop
                  : styles.powerButtonStart,
              ]}
              onPress={handleToggleVpn}
              disabled={busy}>
              <Text style={styles.powerButtonText}>
                {state.isRunning ? 'Turn Off DNS VPN' : 'Turn On DNS VPN'}
              </Text>
            </TouchableOpacity>
          </View>

          {/* Profile List Header */}
          <View style={styles.sectionHeader}>
            <View>
              <Text style={styles.sectionTitle}>DNS Profiles</Text>
              <Text style={styles.sectionHint}>
                Tap any profile to hot-swap immediately with zero connection
                drops
              </Text>
            </View>
            <TouchableOpacity
              style={styles.addButton}
              onPress={openAddProfile}>
              <Text style={styles.addButtonText}>+ Add</Text>
            </TouchableOpacity>
          </View>

          {/* Profile Cards */}
          {state.profiles.map(profile => {
            const isSelected = profile.id === state.activeProfileId;
            return (
              <TouchableOpacity
                key={profile.id}
                style={[
                  styles.profileCard,
                  isSelected && styles.profileCardSelected,
                ]}
                onPress={() => handleSelectProfile(profile.id)}
                activeOpacity={0.8}>
                <View style={styles.profileCardMain}>
                  <View style={styles.profileTitleRow}>
                    <Text style={styles.profileName}>{profile.name}</Text>
                    {isSelected && (
                      <View
                        style={[
                          styles.activePill,
                          state.isRunning
                            ? styles.activePillLive
                            : styles.activePillSelected,
                        ]}>
                        <Text style={styles.activePillText}>
                          {state.isRunning ? 'ACTIVE' : 'SELECTED'}
                        </Text>
                      </View>
                    )}
                  </View>
                  <Text style={styles.profileIpText}>
                    {profile.primaryDns}
                    {profile.secondaryDns
                      ? `  •  ${profile.secondaryDns}`
                      : ''}
                    {profile.port !== 53 ? ` (Port ${profile.port})` : ''}
                  </Text>
                </View>

                <TouchableOpacity
                  style={styles.editButton}
                  onPress={() => openEditProfile(profile)}>
                  <Text style={styles.editButtonText}>Edit</Text>
                </TouchableOpacity>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      )}

      {screen === 'editor' && (
        <ScrollView
          style={styles.scrollArea}
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled">
          <View style={styles.formCard}>
            <Text style={styles.formTitle}>
              {editingProfileId ? 'Edit DNS Profile' : 'New DNS Profile'}
            </Text>

            {formError ? (
              <View style={styles.errorBanner}>
                <Text style={styles.errorBannerText}>{formError}</Text>
              </View>
            ) : null}

            <Text style={styles.inputLabel}>Profile Name</Text>
            <TextInput
              style={styles.textInput}
              placeholder="e.g. Cloudflare, Quad9, Custom"
              placeholderTextColor="#64748B"
              value={nameInput}
              onChangeText={setNameInput}
            />

            <Text style={styles.inputLabel}>Primary DNS (IPv4)</Text>
            <TextInput
              style={styles.textInput}
              placeholder="e.g. 1.1.1.1"
              placeholderTextColor="#64748B"
              keyboardType="numeric"
              autoCapitalize="none"
              value={primaryDnsInput}
              onChangeText={setPrimaryDnsInput}
            />

            <Text style={styles.inputLabel}>
              Secondary DNS (IPv4, Optional)
            </Text>
            <TextInput
              style={styles.textInput}
              placeholder="e.g. 1.0.0.1"
              placeholderTextColor="#64748B"
              keyboardType="numeric"
              autoCapitalize="none"
              value={secondaryDnsInput}
              onChangeText={setSecondaryDnsInput}
            />

            <Text style={styles.inputLabel}>UDP Port</Text>
            <TextInput
              style={styles.textInput}
              placeholder="53"
              placeholderTextColor="#64748B"
              keyboardType="number-pad"
              value={portInput}
              onChangeText={setPortInput}
            />

            <TouchableOpacity
              style={styles.saveButton}
              onPress={handleSaveProfile}>
              <Text style={styles.saveButtonText}>
                {editingProfileId && editingProfileId === state.activeProfileId && state.isRunning
                  ? 'Save & Hot-Swap Immediately'
                  : 'Save Profile'}
              </Text>
            </TouchableOpacity>

            {editingProfileId && state.profiles.length > 1 ? (
              <TouchableOpacity
                style={styles.deleteButton}
                onPress={handleDeleteProfile}>
                <Text style={styles.deleteButtonText}>Delete Profile</Text>
              </TouchableOpacity>
            ) : null}
          </View>
        </ScrollView>
      )}

      {screen === 'settings' && (
        <ScrollView
          style={styles.scrollArea}
          contentContainerStyle={styles.scrollContent}>
          <View style={styles.formCard}>
            <Text style={styles.formTitle}>Settings</Text>

            <View style={styles.settingRow}>
              <View style={styles.settingTextCol}>
                <Text style={styles.settingLabel}>Start on Device Boot</Text>
                <Text style={styles.settingDescription}>
                  Automatically start the DNS VPN with your active profile when
                  Android boots
                </Text>
              </View>
              <Switch
                value={state.settings.startOnBoot}
                onValueChange={handleToggleStartOnBoot}
                trackColor={{ false: '#334155', true: '#0284C7' }}
                thumbColor={state.settings.startOnBoot ? '#38BDF8' : '#94A3B8'}
              />
            </View>

            <View style={styles.divider} />

            <Text style={styles.settingLabel}>Default Presets</Text>
            <Text style={styles.settingDescription}>
              Restore built-in Cloudflare (1.1.1.1), Google (8.8.8.8), and Quad9
              (9.9.9.9) profiles while keeping your custom profiles.
            </Text>
            <TouchableOpacity
              style={styles.secondaryActionButton}
              onPress={handleRestoreDefaults}>
              <Text style={styles.secondaryActionText}>
                Restore Default Presets
              </Text>
            </TouchableOpacity>

            <View style={styles.divider} />

            <Text style={styles.settingLabel}>Architecture & Privacy</Text>
            <Text style={styles.settingDescription}>
              • Split-tunnel VPN routes only virtual DNS address 10.111.222.2/32
              into the tunnel. All other traffic bypasses the VPN directly.
              {'\n'}• Switching profiles hot-swaps the upstream inside the
              running service without closing the tunnel.{'\n'}• Zero DNS query
              logging, zero analytics, and native Kotlin storage.
            </Text>
          </View>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    backgroundColor: '#0F172A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    color: '#94A3B8',
    marginTop: 12,
    fontSize: 15,
  },
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#1E293B',
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#38BDF8',
    marginTop: 2,
  },
  navRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  navButton: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  navButtonText: {
    color: '#E2E8F0',
    fontSize: 13,
    fontWeight: '600',
  },
  errorBanner: {
    backgroundColor: '#7F1D1D',
    marginHorizontal: 20,
    marginTop: 12,
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#EF4444',
  },
  errorBannerText: {
    color: '#FECACA',
    fontSize: 13,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  statusCard: {
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    marginBottom: 24,
  },
  statusCardActive: {
    backgroundColor: '#0C2D48',
    borderColor: '#0284C7',
  },
  statusCardInactive: {
    backgroundColor: '#1E293B',
    borderColor: '#334155',
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  statusIndicatorRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: 8,
  },
  dotActive: {
    backgroundColor: '#22C55E',
  },
  dotInactive: {
    backgroundColor: '#64748B',
  },
  statusBadgeText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#E2E8F0',
    letterSpacing: 0.5,
  },
  tunnelTag: {
    fontSize: 11,
    color: '#94A3B8',
    fontFamily: 'monospace',
  },
  activeProfileTitle: {
    fontSize: 26,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 4,
  },
  activeProfileEndpoints: {
    fontSize: 13,
    color: '#BAE6FD',
    marginBottom: 20,
  },
  powerButton: {
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  powerButtonStart: {
    backgroundColor: '#0284C7',
  },
  powerButtonStop: {
    backgroundColor: '#DC2626',
  },
  powerButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  sectionHint: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 2,
    maxWidth: 260,
  },
  addButton: {
    backgroundColor: '#0284C7',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
  },
  addButtonText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
  },
  profileCard: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#334155',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  profileCardSelected: {
    borderColor: '#38BDF8',
    backgroundColor: '#172554',
  },
  profileCardMain: {
    flex: 1,
    paddingRight: 12,
  },
  profileTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  profileName: {
    fontSize: 16,
    fontWeight: '700',
    color: '#F8FAFC',
    marginRight: 8,
  },
  activePill: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  activePillLive: {
    backgroundColor: '#15803D',
  },
  activePillSelected: {
    backgroundColor: '#0369A1',
  },
  activePillText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '700',
  },
  profileIpText: {
    fontSize: 13,
    color: '#94A3B8',
    fontFamily: 'monospace',
  },
  editButton: {
    backgroundColor: '#334155',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  editButtonText: {
    color: '#E2E8F0',
    fontSize: 12,
    fontWeight: '600',
  },
  formCard: {
    backgroundColor: '#1E293B',
    borderRadius: 14,
    padding: 20,
    borderWidth: 1,
    borderColor: '#334155',
  },
  formTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#CBD5E1',
    marginBottom: 6,
    marginTop: 12,
  },
  textInput: {
    backgroundColor: '#0F172A',
    borderWidth: 1,
    borderColor: '#334155',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 11,
    color: '#F8FAFC',
    fontSize: 15,
  },
  saveButton: {
    backgroundColor: '#0284C7',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    marginTop: 24,
  },
  saveButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  deleteButton: {
    backgroundColor: '#450A0A',
    borderWidth: 1,
    borderColor: '#DC2626',
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: 'center',
    marginTop: 12,
  },
  deleteButtonText: {
    color: '#FCA5A5',
    fontSize: 14,
    fontWeight: '600',
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  settingTextCol: {
    flex: 1,
    paddingRight: 16,
  },
  settingLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#F8FAFC',
    marginBottom: 4,
  },
  settingDescription: {
    fontSize: 13,
    color: '#94A3B8',
    lineHeight: 19,
  },
  divider: {
    height: 1,
    backgroundColor: '#334155',
    marginVertical: 18,
  },
  secondaryActionButton: {
    backgroundColor: '#334155',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 12,
  },
  secondaryActionText: {
    color: '#F8FAFC',
    fontSize: 14,
    fontWeight: '600',
  },
});

export default App;
