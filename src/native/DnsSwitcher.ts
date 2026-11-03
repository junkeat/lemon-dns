import { useEffect, useState, useCallback } from 'react';
import { NativeEventEmitter, NativeModules } from 'react-native';

export interface DnsProfile {
  id: string;
  name: string;
  primaryDns: string;
  secondaryDns: string;
  primaryIpv6Dns: string;
  secondaryIpv6Dns: string;
  port: number;
  isBuiltIn: boolean;
}

export interface DnsSettings {
  startOnBoot: boolean;
}

export interface DnsSwitcherState {
  isRunning: boolean;
  activeProfileId: string;
  activeProfile: DnsProfile;
  profiles: DnsProfile[];
  settings: DnsSettings;
}

export interface SaveProfileInput {
  id?: string;
  name: string;
  primaryDns: string;
  secondaryDns?: string;
  primaryIpv6Dns?: string;
  secondaryIpv6Dns?: string;
  port?: number;
}

interface NativeDnsSwitcherModule {
  getState(): Promise<DnsSwitcherState>;
  startVpn(): Promise<DnsSwitcherState>;
  stopVpn(): Promise<DnsSwitcherState>;
  setActiveProfile(profileId: string): Promise<DnsSwitcherState>;
  saveProfile(profile: SaveProfileInput): Promise<DnsSwitcherState>;
  deleteProfile(profileId: string): Promise<DnsSwitcherState>;
  updateSettings(settings: Partial<DnsSettings>): Promise<DnsSwitcherState>;
  restoreDefaultProfiles(): Promise<DnsSwitcherState>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const NativeDnsSwitcher: NativeDnsSwitcherModule | undefined =
  NativeModules.DnsSwitcher;
const eventEmitter = NativeDnsSwitcher
  ? new NativeEventEmitter(NativeModules.DnsSwitcher)
  : null;

export const DnsSwitcher = {
  getState: (): Promise<DnsSwitcherState> => NativeDnsSwitcher!.getState(),
  startVpn: (): Promise<DnsSwitcherState> => NativeDnsSwitcher!.startVpn(),
  stopVpn: (): Promise<DnsSwitcherState> => NativeDnsSwitcher!.stopVpn(),
  setActiveProfile: (profileId: string): Promise<DnsSwitcherState> =>
    NativeDnsSwitcher!.setActiveProfile(profileId),
  saveProfile: (profile: SaveProfileInput): Promise<DnsSwitcherState> =>
    NativeDnsSwitcher!.saveProfile(profile),
  deleteProfile: (profileId: string): Promise<DnsSwitcherState> =>
    NativeDnsSwitcher!.deleteProfile(profileId),
  updateSettings: (settings: Partial<DnsSettings>): Promise<DnsSwitcherState> =>
    NativeDnsSwitcher!.updateSettings(settings),
  restoreDefaultProfiles: (): Promise<DnsSwitcherState> =>
    NativeDnsSwitcher!.restoreDefaultProfiles(),
};

export function useDnsSwitcherState() {
  const [state, setState] = useState<DnsSwitcherState | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!NativeDnsSwitcher) {
      return;
    }
    try {
      const next = await DnsSwitcher.getState();
      setState(next);
      setError(null);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load DNS Switcher state');
    }
  }, []);

  useEffect(() => {
    refresh();
    if (!eventEmitter) {
      return;
    }
    const subscription = eventEmitter.addListener(
      'DnsSwitcherStateChanged',
      (nextState: unknown) => {
        setState(nextState as DnsSwitcherState);
      },
    );
    return () => {
      subscription.remove();
    };
  }, [refresh]);

  return { state, setState, error, setError, refresh };
}
