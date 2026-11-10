/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Text, TextInput } from 'react-native';
import App from '../App';
import { DnsSwitcher, DnsSwitcherState } from '../src/native/DnsSwitcher';

jest.mock('react-native-safe-area-context', () => {
  const ReactActual = require('react');
  return {
    SafeAreaProvider: ({ children }: { children: React.ReactNode }) =>
      ReactActual.createElement(ReactActual.Fragment, null, children),
    SafeAreaView: ({ children }: { children: React.ReactNode }) =>
      ReactActual.createElement(ReactActual.Fragment, null, children),
  };
});

const mockInitialState: DnsSwitcherState = {
  isRunning: false,
  activeProfileId: 'cloudflare',
  activeProfile: {
    id: 'cloudflare',
    name: 'Cloudflare',
    primaryDns: '1.1.1.1',
    secondaryDns: '1.0.0.1',
    primaryIpv6Dns: '2606:4700:4700::1111',
    secondaryIpv6Dns: '2606:4700:4700::1001',
    port: 53,
    isBuiltIn: true,
  },
  profiles: [
    {
      id: 'cloudflare',
      name: 'Cloudflare',
      primaryDns: '1.1.1.1',
      secondaryDns: '1.0.0.1',
      primaryIpv6Dns: '2606:4700:4700::1111',
      secondaryIpv6Dns: '2606:4700:4700::1001',
      port: 53,
      isBuiltIn: true,
    },
    {
      id: 'custom-1',
      name: 'Home Wi-Fi',
      primaryDns: '192.168.1.2',
      secondaryDns: '',
      primaryIpv6Dns: '',
      secondaryIpv6Dns: '',
      port: 5353,
      isBuiltIn: false,
    },
  ],
  settings: {
    startOnBoot: true,
  },
};

jest.mock('../src/native/DnsSwitcher', () => {
  const ReactActual = require('react');
  const mockFns = {
    getState: jest.fn(),
    startVpn: jest.fn(),
    stopVpn: jest.fn(),
    setActiveProfile: jest.fn(),
    saveProfile: jest.fn(),
    deleteProfile: jest.fn(),
    updateSettings: jest.fn(),
    restoreDefaultProfiles: jest.fn(),
  };
  return {
    DnsSwitcher: mockFns,
    useDnsSwitcherState: () => {
      const [state, setState] = ReactActual.useState(mockInitialState);
      const [error, setError] = ReactActual.useState(null);
      return { state, setState, error, setError, refresh: jest.fn() };
    },
  };
});

describe('App UI', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders providers tab without raw IPs, switches to settings, and validates port input', async () => {
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<App />);
    });

    const getAllText = () =>
      renderer.root
        .findAllByType(Text)
        .map(node => node.props.children)
        .flat()
        .join(' ');

    const findButtonByLabel = (label: string) =>
      renderer.root.findAll(
        node =>
          typeof node.props?.onPress === 'function' &&
          node
            .findAllByType(Text)
            .some(t => String(t.props.children).includes(label)),
      )[0];

    expect(getAllText()).toContain('Cloudflare');
    expect(getAllText()).toContain('Home Wi-Fi');
    expect(getAllText()).toContain('Turn on protection');
    expect(getAllText()).not.toContain('10.111.222.2');

    // Open "+ Add custom" modal and test port validation for partial numbers like "53abc"
    const addBtn = findButtonByLabel('+ Add custom');
    expect(addBtn).toBeDefined();

    await ReactTestRenderer.act(() => {
      addBtn!.props.onPress();
    });

    const portInput = renderer.root
      .findAllByType(TextInput)
      .find(input => input.props.placeholder === '53');
    expect(portInput).toBeDefined();

    await ReactTestRenderer.act(() => {
      portInput!.props.onChangeText('53abc');
    });

    const saveBtn = findButtonByLabel('Save provider');
    expect(saveBtn).toBeDefined();

    await ReactTestRenderer.act(async () => {
      await saveBtn!.props.onPress();
    });

    expect(getAllText()).toContain(
      'Port must be a number between 1 and 65535.',
    );
    expect(DnsSwitcher.saveProfile).not.toHaveBeenCalled();

    // Switch to Settings tab and verify clean layman settings without architecture table
    const settingsTab = findButtonByLabel('Settings');
    expect(settingsTab).toBeDefined();

    await ReactTestRenderer.act(() => {
      settingsTab!.props.onPress();
    });

    expect(getAllText()).toContain('Start when phone turns on');
    expect(getAllText()).not.toContain('Split-tunnel architecture');
  });
});
