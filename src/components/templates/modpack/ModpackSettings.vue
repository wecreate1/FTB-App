<template>
  <div class="pack-settings">
    <ftb-input
      label="Name"
      :value="localInstance.name"
      v-model="localInstance.name"
      @blur="saveSettings"
      class="mb-8"
    />

    <div class="flex flex-row mb-8">
      <ftb-input
        class="flex-col"
        label="Window Width"
        v-model="localInstance.width"
        :value="localInstance.width"
        @blur="saveSettings"
      />

      <font-awesome-icon class="mt-auto mb-6 mx-4 text-gray-600" icon="times" size="1x" />

      <ftb-input
        class="flex-col"
        label="Window Height"
        v-model="localInstance.height"
        :value="localInstance.height"
        @blur="saveSettings"
      />

      <div class="flex-col mt-auto mb-2 pl-4">
        <v-selectmenu
          :title="false"
          :query="false"
          :data="resolutionList"
          align="left"
          :value="resSelectedValue"
          @values="resChange"
        >
          <ftb-button color="primary" class="py-2 px-4 my-1">
            <font-awesome-icon icon="desktop" size="1x" class="cursor-pointer" />
            <span class="ml-4">Resolutions</span>
          </ftb-button>
        </v-selectmenu>
      </div>
    </div>
    <div class="flex items-center mb-8">
      <div class="w-1/2 mr-8 flex items-end justify-between">
        <section class="mr-4 flex-1">
          <label class="block uppercase tracking-wide text-white-700 text-xs font-bold mb-2"> Java Version </label>
          <select
            class="appearance-none block w-full bg-input text-gray-400 border border-input py-3 px-4 leading-tight focus:outline-none rounded w-full"
            v-model="jreSelection"
            @change="updateJrePath"
          >
            <option value="-1" v-if="jreSelection === '-1'" disabled>
              Custom selection ({{ localInstance.jrePath }})
            </option>
            <option
              v-for="index in Object.keys(javaVersions)"
              :value="javaVersions[index].path"
              :key="javaVersions[index].name"
            >
              {{ javaVersions[index].name }}
            </option>
          </select>
        </section>

        <ftb-button color="primary" class="py-2 px-4 mb-1" @click="browseForJava">
          <font-awesome-icon icon="folder" size="1x" class="cursor-pointer" />
          <span class="ml-4">Browse</span>
        </ftb-button>
      </div>

      <ftb-input
        label="Java runtime arguments"
        placeholder="-Xmx1G"
        v-model="localInstance.jvmArgs"
        @blur="saveSettings"
        class="flex-1"
      />
    </div>

    <ftb-slider
      label="Instance Memory"
      v-model="localInstance.memory"
      :currentValue="localInstance.memory"
      minValue="512"
      :maxValue="settingsState.hardware.totalMemory"
      @blur="saveSettings"
      @change="saveSettings"
      step="64"
      unit="MB"
      css-class="memory"
      :dark="true"
      class="mb-8"
      :raw-style="`background: linear-gradient(to right, #8e0c25 ${
        (this.localInstance.minMemory / settingsState.hardware.totalMemory) * 100 - 5
      }%, #a55805 ${(this.localInstance.minMemory / settingsState.hardware.totalMemory) * 100}%, #a55805 ${
        (this.localInstance.recMemory / settingsState.hardware.totalMemory) * 100 - 5
      }%, #005540 ${(this.localInstance.recMemory / settingsState.hardware.totalMemory) * 100}%);`"
    />

    <ftb-toggle
      label="Enable cloud save uploads"
      :disabled="canUseCloudSaves"
      onColor="bg-primary"
      :value="localInstance.cloudSaves"
      @change="toggleCloudSaves"
      small="You can only use Cloud Saves if you have an active paid plan on MineTogether."
      class="mb-4"
    />

    <ftb-input
      label="Shell"
      :value="localInstance.shellArgs"
      v-model="localInstance.shellArgs"
      @blur="saveSettings"
      class="mb-8"
    />

    <p class="text-lg font-bold mb-4">Actions</p>

    <div class="buttons flex flex-1">
      <ftb-button class="py-2 mr-4 px-4" color="warning" css-class="text-center text-l" @click="browseInstance()">
        <font-awesome-icon icon="folder" class="mr-2" size="1x" />
        Open Folder
      </ftb-button>

      <ftb-button
        :disabled="!getActiveMcProfile"
        :title="
          !getActiveMcProfile
            ? 'You need to be logged in to your Minecraft account to share packs'
            : 'Share your modpack with friends'
        "
        class="py-2 mr-4 px-4"
        color="info"
        css-class="text-center text-l"
        @click="shareConfirm = true"
      >
        <font-awesome-icon icon="upload" class="mr-2" size="1x" />
        Share modpack
      </ftb-button>

      <ftb-button class="py-2 px-4" color="danger" css-class="text-center text-l" @click="confirmDelete()">
        <font-awesome-icon icon="trash" class="mr-2" size="1x" />
        Delete
      </ftb-button>
    </div>

    <ftb-modal :visible="showMsgBox" @dismiss-modal="hideMsgBox">
      <message-modal
        :title="msgBox.title"
        :content="msgBox.content"
        :ok-action="msgBox.okAction"
        :cancel-action="msgBox.cancelAction"
        :type="msgBox.type"
        :loading="deleting"
      />
    </ftb-modal>

    <share-instance-modal :open="shareConfirm" @closed="shareConfirm = false" :uuid="instance.uuid" />
  </div>
</template>

<script lang="ts">
import { Instance } from '@/modules/modpacks/types';
import { Component, Prop, Vue, Watch } from 'vue-property-decorator';
import { Action, Getter, State } from 'vuex-class';
import { AuthState } from '@/modules/auth/types';
import { JavaVersion, SettingsState } from '@/modules/settings/types';
import FTBModal from '@/components/atoms/FTBModal.vue';
import FTBToggle from '@/components/atoms/input/FTBToggle.vue';
import FTBSlider from '@/components/atoms/input/FTBSlider.vue';
import MessageModal from '@/components/organisms/modals/MessageModal.vue';
import ShareInstanceModal from '@/components/organisms/modals/actions/ShareInstanceModal.vue';
import Platform from '@/utils/interface/electron-overwolf';

interface MsgBox {
  title: string;
  content: string;
  type: string;
  okAction: () => void;
  cancelAction: () => void;
}

@Component({
  components: {
    'ftb-modal': FTBModal,
    'ftb-toggle': FTBToggle,
    'ftb-slider': FTBSlider,
    MessageModal,
    ShareInstanceModal,
  },
})
export default class ModpackSettings extends Vue {
  // Vuex
  @State('auth') public auth!: AuthState;
  @State('settings') public settingsState!: SettingsState;

  @Getter('getActiveProfile', { namespace: 'core' }) public getActiveMcProfile!: any;

  @Action('storeInstalledPacks', { namespace: 'modpacks' }) public storePacks!: any;
  @Action('sendMessage') public sendMessage!: any;
  @Action('saveInstance', { namespace: 'modpacks' }) public saveInstance: any;
  @Action('showAlert') public showAlert: any;

  @Prop() instance!: Instance;

  localInstance: Instance = {} as Instance;
  resSelectedValue = '0';

  shareConfirm = false;

  jreSelection = '';
  javaVersions: JavaVersion[] = [];

  deleting = false;
  showMsgBox = false;
  msgBox: MsgBox = {
    title: '',
    content: '',
    type: '',
    okAction: Function,
    cancelAction: Function,
  };

  mounted() {
    this.localInstance = { ...this.instance }; // copy, don't reference
    if (this.localInstance.jrePath) {
      this.jreSelection = this.localInstance.jrePath;
    }

    this.sendMessage({
      payload: { type: 'getJavas' },
      callback: (data: { javas: JavaVersion[] }) => {
        this.javaVersions = data.javas;

        // If it's embedded we don't need to do anything special
        if (this.localInstance.embeddedJre) {
          return;
        }

        // Java version not in our list, thus it must be custom so flag it as custom
        if (!data.javas.find((e) => e.path === this.localInstance.jrePath)) {
          this.jreSelection = '-1';
        }
      },
    });
  }

  /**
   * When we save our instance, it's possible the prop will also be mutated so we
   * keep our local instance in sync by checking if anything changed. Yes, this
   * really is one of the best way of checking nested objects.
   */
  @Watch('instance')
  onInstancePropChange(newVal: Instance, oldVal: Instance) {
    if (JSON.stringify(newVal) !== JSON.stringify(oldVal)) {
      this.localInstance = { ...newVal }; // copy, don't reference
    }
  }

  // I'm not sure this works, at best, it's VueX state mutation which is bad hmm kay...
  public toggleCloudSaves() {
    this.localInstance.cloudSaves = !this.localInstance.cloudSaves;
  }

  public resChange(data: any) {
    if (data && data.length) {
      if (this.resSelectedValue === data[0].value) {
        return;
      }
      this.resSelectedValue = data[0].value;
      this.localInstance.width = this.settingsState.hardware.supportedResolutions[data[0].id].width;
      this.localInstance.height = this.settingsState.hardware.supportedResolutions[data[0].id].height;

      this.saveSettings();
      return;
    }
  }

  browseForJava() {
    Platform.get.io.selectFileDialog((path) => {
      if (typeof path !== 'undefined' && path == null) {
        this.showAlert({
          title: 'Error',
          message: 'Unable to set Java location as the path was not found',
          type: 'danger',
        });

        return;
      } else if (!path) {
        return;
      }

      const javaVersion = this.javaVersions.find((e) => e.path === path);
      this.jreSelection = !javaVersion ? '-1' : javaVersion.path;

      this.localInstance.jrePath = path;
      this.saveSettings();
    });
  }

  updateJrePath(value: any) {
    this.localInstance.jrePath = value.target.value;
    this.saveSettings();
  }

  public async saveSettings() {
    if (JSON.stringify(this.localInstance) === JSON.stringify(this.instance)) {
      return;
    }

    const response = await this.saveInstance(this.localInstance);
    if (response.status === 'error') {
      this.showAlert({
        title: 'Error',
        message: response.errorMessage,
        type: 'danger',
      });
    } else {
      this.showAlert({
        title: 'Saved!',
        message: 'The settings for this instance have been saved',
        type: 'primary',
      });
    }
  }

  public browseInstance(): void {
    this.sendMessage({
      payload: { type: 'instanceBrowse', uuid: this.instance?.uuid },
      callback: (data: any) => {},
    });
  }

  public confirmDelete() {
    this.openMessageBox({
      type: 'okCancel',
      title: 'Are you sure?',
      okAction: this.deleteInstace,
      cancelAction: this.hideMsgBox,
      content: `Are you sure you want to delete ${this.instance?.name}?`,
    });
  }

  public hideMsgBox(): void {
    this.showMsgBox = false;
  }

  private openMessageBox(payload: MsgBox) {
    this.msgBox = { ...this.msgBox, ...payload };
    this.showMsgBox = true;
  }

  public deleteInstace(): void {
    this.deleting = true;
    this.sendMessage({
      payload: { type: 'uninstallInstance', uuid: this.instance?.uuid },
      callback: (data: any) => {
        this.sendMessage({
          payload: { type: 'installedInstances', refresh: true },
          callback: (data: any) => {
            this.storePacks(data);
            this.$router.push({ name: 'modpacks' });
          },
        });
      },
    });
  }

  get resolutionList() {
    return Object.entries(this.settingsState.hardware.supportedResolutions).map(([key, res]) => ({
      id: key,
      name: res.width + 'x' + res.height,
      value: key,
    }));
  }

  get canUseCloudSaves() {
    return (
      this.auth.token?.activePlan !== null &&
      !this.settingsState.settings.cloudSaves &&
      (this.settingsState.settings.cloudSaves as boolean | 'true' | 'false') !== 'true'
    );
  }
}
</script>

<style scoped lang="scss"></style>
