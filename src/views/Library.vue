<template>
  <div class="mod-packs h-full" v-if="isLoaded">
    <!-- My Modpacks Stuff -->
    <div class="packs px-6 py-4" v-if="modpacks.installedPacks.length > 0">
      <div class="flex items-center mb-4">
        <FTBSearchBar v-model="searchTerm" placeholder="Search" class="mr-4 flex-1" />
        <ftb-button
          class="py-2 px-4 flex items-center border-2 border-blue-600 hover:border-blue-500"
          color="info"
          @click="showImport = true"
        >
          <font-awesome-icon icon="plus" class="mr-2" size="1x" />
          <span>Import</span>
        </ftb-button>
      </div>

      <div class="pack-card-list grid">
        <pack-card-wrapper
          v-for="modpack in packs"
          :list-mode="false"
          :key="modpack.uuid"
          :art="modpack.art"
          :installed="true"
          :minecraft="'1.7.10'"
          :version="modpack.version"
          :description="getModpack(modpack.id) !== null ? getModpack(modpack.id).synopsis : 'Unable to load synopsis'"
          :tags="getModpack(modpack.id) !== null ? getModpack(modpack.id).tags : []"
          :versions="modpack.versions"
          :name="modpack.name"
          :authors="modpack.authors"
          :instance="modpack"
          :instanceID="modpack.uuid"
          kind="instance"
        >
        </pack-card-wrapper>
      </div>
    </div>

    <div class="flex flex-1 flex-wrap justify-center flex-col items-center no-packs" v-else>
      <div class="message flex flex-1 flex-wrap items-center flex-col mt-32">
        <font-awesome-icon icon="heart-broken" size="6x" />
        <h1 class="text-5xl">Oh no!</h1>
        <span class="mb-4 w-3/4 text-center">
          Would you look at that! Looks like you've got no modpacks installed yet... If you know what you want, click
          Browse and search through our collection and all of CurseForge modpacks, otherwise, use Discover we've got
          some great recommended packs.</span
        >
        <div class="flex flex-row justify-between my-2">
          <router-link to="/browseModpacks">
            <ftb-button color="primary" class="py-2 px-10 mx-2">Browse</ftb-button>
          </router-link>
          <router-link to="/discover">
            <ftb-button color="primary" class="py-2 px-6 mx-2">Discover</ftb-button>
          </router-link>
        </div>
      </div>
    </div>

    <modal
      :open="showImport"
      :title="`${
        modalType === null
          ? 'Import a modpack'
          : modalType === 'share'
          ? 'Use a share code'
          : 'CurseForge Modpack import'
      }`"
      :subTitle="`${
        modalType === null
          ? 'You can import a modpack from a CurseForge zip file or use a share code'
          : modalType === 'share'
          ? 'A share code is a code you can use to install new packs'
          : 'Use a Curseforge Modpack zip file to import a modpack'
      }`"
      @closed="
        () => {
          showImport = false;
          modalType = null;
        }
      "
    >
      <div class="pt-4" v-if="modalType == null">
        <h4 class="text-lg font-bold mb-2"><font-awesome-icon icon="code" class="mr-2" size="1x" /> Share code</h4>
        <p class="mb-4">
          You can share modpacks using share codes from within the app. If you've been given one of these codes. You can
          import the modpack using the button below.
        </p>
        <ftb-button
          color="primary"
          class="py-2 px-6 mt-2 mb-6 text-center font-bold text-md"
          @click="modalType = 'share'"
        >
          <font-awesome-icon icon="code" class="mr-2" size="1x" />
          Use a share code
        </ftb-button>

        <hr class="border-white opacity-25 mb-4" />

        <h4 class="text-lg font-bold mb-2">
          <font-awesome-icon icon="download" class="mr-2" size="1x" /> CurseForge imports
        </h4>
        <p class="mb-4">You can import CurseForge Modpacks using the button below</p>
        <ftb-button color="primary" class="py-2 px-6 mt-2 text-center font-bold text-md" @click="modalType = 'curse'">
          <font-awesome-icon icon="download" class="mr-2" size="1x" />
          Import a CurseForge modpack zip
        </ftb-button>
      </div>
      <template v-if="modalType === 'curse'">
        <message type="danger" v-if="fileError" class="mb-4">
          {{ fileError }}
        </message>

        <div
          class="drop-area"
          @click.self="$refs.fileInputRef.click()"
          @dragenter.prevent
          @dragleave.prevent
          @dragover.prevent
          @drop.prevent="fileAttach($event)"
        >
          <font-awesome-icon icon="upload" class="mr-2" size="2x" />
          <p>Drag & Drop a file or select a file</p>
          <hr />
          <ftb-button color="primary" class="py-2 px-6 mt-2 font-bold" @click="$refs.fileInputRef.click()">
            <font-awesome-icon icon="download" class="mr-2" size="1x" />
            Select a file
          </ftb-button>
          <input type="file" @change="fileAttach($event)" accept="application/zip" hidden ref="fileInputRef" />
        </div>

        <p v-if="activeFile" class="font-bold mt-4 text-base mb-2">Selected file</p>
        <div class="file flex items-center p-4" v-if="activeFile">
          <font-awesome-icon icon="file-zipper" size="2x" class="mr-4" />
          <div class="text">
            <div class="name font-bold">{{ activeFile.name }}</div>
            <div class="size">
              {{ PrettyBytes(activeFile.size) }}
            </div>
          </div>
        </div>

        <ftb-button
          color="primary"
          :disabled="!activeFile"
          class="py-2 px-6 mt-6 w-full block text-center font-bold"
          @click="installZip"
        >
          <font-awesome-icon icon="download" class="mr-2" size="1x" />
          Install {{ activeFile ? activeFile.name : '' }}
        </ftb-button>
      </template>
      <template v-if="modalType === 'share'">
        <message type="danger" v-if="shareCodeError" class="mb-4">
          {{ shareCodeError }}
        </message>

        <ftb-input
          placeholder="share code"
          label="Share code"
          v-model="shareCode"
          class="mb-4 text-base"
          :copyable="true"
        />
        <div class="flex justify-end">
          <ftb-button color="primary" class="py-2 px-6 mt-2 inline-block" @click="checkAndInstall">
            <font-awesome-icon icon="download" class="mr-2" size="1x" />
            Install
          </ftb-button>
        </div>
      </template>
    </modal>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator';
import PackCardWrapper from '@/components/organisms/packs/PackCardWrapper.vue';
import FTBSearchBar from '@/components/atoms/input/FTBSearchBar.vue';
import { Instance, ModPack, ModpackState } from '@/modules/modpacks/types';
import { Action, Getter, State } from 'vuex-class';
import { SettingsState } from '@/modules/settings/types';
import { prettyByteFormat, wsTimeoutWrapper, wsTimeoutWrapperTyped } from '@/utils';
import { InstallerState } from '@/modules/app/appStore.types';

@Component({
  components: {
    PackCardWrapper,
    FTBSearchBar,
  },
})
export default class Library extends Vue {
  @State('settings') public settings!: SettingsState;
  @State('modpacks') public modpacks!: ModpackState;
  @Action('saveSettings', { namespace: 'settings' }) public saveSettings: any;
  @Getter('packsCache', { namespace: 'modpacks' }) public packsCache!: ModPack[];
  @Action('fetchModpack', { namespace: 'modpacks' }) public fetchModpack!: (id: number) => Promise<ModPack>;
  @Action('sendMessage') public sendMessage!: any;
  @Action('installModpack', { namespace: 'app' }) public installModpack!: (data: InstallerState) => void;

  private searchTerm: string = '';
  private isLoaded: boolean = false;
  isGrid: boolean = false;

  showImport = false;
  modalType = null;
  fileError = '';
  activeFile: any = null;
  shareCode: string = '';
  shareCodeError = '';

  PrettyBytes = prettyByteFormat;

  @Watch('modpacks', { deep: true })
  public async onModpacksChange(newVal: ModpackState, oldVal: ModpackState) {
    if (JSON.stringify(newVal.installedPacks) !== JSON.stringify(oldVal.installedPacks)) {
      this.isLoaded = false;
      try {
        await Promise.all(
          this.modpacks.installedPacks.map(async (instance) => {
            const pack = await this.fetchModpack(instance.id);
            return pack;
          }),
        );
        this.isLoaded = true;
      } catch (err) {
        this.isLoaded = true;
      }
    }
  }

  public async mounted() {
    if (this.modpacks) {
      this.isLoaded = false;
      try {
        await Promise.all(this.modpacks.installedPacks.map(async (instance) => await this.fetchModpack(instance.id)));
        this.isLoaded = true;
      } catch (err) {
        this.isLoaded = true;
      }
    }
  }

  async checkAndInstall() {
    if (this.shareCode === '') {
      return;
    }

    const checkCode = await wsTimeoutWrapperTyped<any, { success: boolean }>({
      type: 'checkShareCode',
      shareCode: this.shareCode,
    });

    if (!checkCode.success) {
      this.shareCodeError = `Unable to find a valid pack with the code of ${this.shareCode} `;
      return;
    }

    this.showImport = false;
    this.modalType = null;

    this.installModpack({
      pack: {
        shareCode: this.shareCode,
      },
      meta: {
        name: 'Shared pack',
        version: this.shareCode,
      },
    });

    this.shareCode = '';
  }

  fileAttach(event: any) {
    const file = event.dataTransfer?.files[0] ?? event.target?.files[0] ?? null;
    if (file == null || !file.name.endsWith('.zip')) {
      return;
    }

    this.activeFile = {
      name: file.name,
      size: file.size,
      path: file.path,
    };
  }

  async installZip() {
    this.fileError = '';
    if (!this.activeFile) {
      return;
    }

    const res = await wsTimeoutWrapper({
      type: 'checkCurseZip',
      path: this.activeFile.path ?? 'invalid-path-name-to-break-the-java-size-by-default',
    });

    if (!res?.success) {
      this.activeFile = null;
      this.fileError = res.message ?? "We're unable to detect a CurseForge pack in this zip file.";
    } else {
      this.modalType = null;
      this.showImport = false;

      this.installModpack({
        pack: {
          importFrom: this.activeFile.path ?? 'invalid-path-name-to-break-the-java-size-by-default',
        },
        meta: {
          name: 'Curse imported modpack',
          version: this.activeFile.name,
        },
      });
      this.activeFile = null;
    }
  }

  get packs(): Instance[] {
    return this.modpacks == null
      ? []
      : this.searchTerm.length > 0
      ? this.modpacks.installedPacks.filter((pack) => {
          return pack.name.search(new RegExp(this.searchTerm, 'gi')) !== -1;
        })
      : this.modpacks.installedPacks.sort((a, b) => {
          if (!a.lastPlayed || !b.lastPlayed) {
            return a.name.localeCompare(b.name);
          }

          return b.lastPlayed - a.lastPlayed;
        });
  }

  public getModpack(id: number): ModPack | null {
    return this.packsCache[id] ? this.packsCache[id] : null;
  }
}
</script>

<style lang="scss" scoped>
.pack-card-list {
  &.grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, 148px);
    gap: 1rem;
  }
}

.drop-area {
  margin-top: 1rem;
  padding: 2.5rem 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed rgba(white, 0.2);
  border-radius: 5px;

  hr {
    margin: 1rem 0;
  }

  > svg {
    margin-bottom: 1rem;
  }
}

.file {
  background-color: rgba(white, 0.1);
  border-radius: 5px;

  .text {
    .name {
      word-wrap: break-word;
    }
  }
}

.no-packs {
  position: relative;
  height: 100%;

  &::before {
    content: '';
    top: 0;
    left: 0;
    position: absolute;
    width: 100%;
    height: 100%;

    background: url('../assets/images/no-pack-bg.webp') center center no-repeat;
    background-size: auto 100%;
    z-index: -1;
    opacity: 0.3;
  }
}
</style>
