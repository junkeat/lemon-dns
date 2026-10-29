import React from 'react';
import { StatusBar, StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" backgroundColor="#0F172A" />
      <SafeAreaView style={styles.container}>
        <View style={styles.content}>
          <Text style={styles.title}>DNS Switcher</Text>
          <Text style={styles.subtitle}>Phase 1: UDP DNS VPN Prototype</Text>
          <Text style={styles.instructions}>
            Use the native control panel at the bottom of the screen to start or
            stop the split-tunnel DNS VPN (1.1.1.1).
          </Text>
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  content: {
    padding: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#38BDF8',
    marginBottom: 16,
  },
  instructions: {
    fontSize: 14,
    lineHeight: 20,
    color: '#94A3B8',
  },
});

export default App;
