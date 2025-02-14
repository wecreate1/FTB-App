import { MutationTree } from 'vuex';
import { Resolution, Settings, SettingsState } from './types';

const defaultSettings: Settings = {
  width: 1720,
  height: 840,
  memory: 3072,
  keepLauncherOpen: true,
  enablePreview: false,
  jvmargs: '',
  exitOverwolf: false,
  enableAnalytics: true,
  enableChat: true,
  enableBeta: false,
  threadLimit: 2,
  speedLimit: 0,
  cacheLife: 5184000,
  packCardSize: 1,
  instanceLocation: '',
  listMode: false,
  verbose: false,
  cloudSaves: false,
  autoOpenChat: true,
  blockedUsers: [],
  mtConnect: false,
  automateMojang: true,
  showAdverts: true,
  proxyPort: '',
  proxyType: 'none',
  proxyHost: '',
  proxyPassword: '',
  proxyUser: '',
  shellArgs: '',
};

export const mutations: MutationTree<SettingsState> = {
  loadSettings(state, payload: Settings) {
    state.settings = { ...defaultSettings, ...payload };
  },
  saveSettings(state, payload: Settings) {
    state.settings = payload;
  },
  loadHardware(state, payload: any) {
    state.hardware = {
      totalMemory: payload.totalMemory,
      totalCores: payload.totalCores,
      availableMemory: payload.availableMemory,
      mainScreen: payload.mainScreen as Resolution,
      supportedResolutions:
        payload.supportedResolutions.length > 0
          ? (payload.supportedResolutions.sort((a: Resolution, b: Resolution) => {
              return b.width + b.height - (a.width + a.height);
            }) as Resolution[])
          : [],
    };
  },
  updateSetting() {},
};
