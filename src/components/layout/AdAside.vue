<template>
  <div class="ad-aside" :class="{ 'is-dev': isDev }">
    <div class="heading">
      <nav>
        <p class="font-bold text-lg mb-4">Helpful links</p>
        <div class="flex">
          <div
            class="item up"
            aria-label="FTB Discord"
            data-balloon-pos="down-left"
            @click="() => platform.get.utils.openUrl('https://go.ftb.team/discord')"
          >
            <font-awesome-icon :icon="['fab', 'discord']" />
          </div>

          <div
            class="item up"
            aria-label="FTB Twitter"
            data-balloon-pos="down-left"
            @click="() => platform.get.utils.openUrl('https://go.ftb.team/twitter')"
          >
            <font-awesome-icon :icon="['fab', 'twitter']" />
          </div>

          <div
            class="item up"
            aria-label="FTB Twitch"
            data-balloon-pos="down-left"
            @click="() => platform.get.utils.openUrl('https://go.ftb.team/twitch')"
          >
            <font-awesome-icon :icon="['fab', 'twitch']" />
          </div>
        </div>

        <p class="font-bold text-lg mt-6 mb-2">Support</p>
        <div class="item" @click="() => platform.get.utils.openUrl('https://go.ftb.team/creeperhost')">
          Order a server from CreeperHost
        </div>
        <div class="item" @click="() => platform.get.utils.openUrl('https://go.ftb.team/support')">
          Need help using the app?
        </div>
        <div class="item" @click="() => platform.get.utils.openUrl('https://go.ftb.team/app-feedback')">
          Report an app bug
        </div>
        <div class="item" @click="() => platform.get.utils.openUrl('https://go.ftb.team/server-setup')">
          Server setup guide
        </div>
        <div class="item" @click="() => platform.get.utils.openUrl('https://go.ftb.team/app-support')">App Guides</div>
      </nav>
    </div>

    <div class="ad-space">
      <div class="heart text-center mb-4" v-if="!isElectron">
        <div class="heart-hearts">
          <font-awesome-icon icon="heart" size="2x" class="mb-2" />
          <font-awesome-icon icon="heart" size="2x" class="less-than-three heart-2 mb-2" />
          <font-awesome-icon icon="heart" size="2x" class="less-than-three heart-3 mb-2" />
        </div>
        <p class="font-bold">Supports FTB & Curseforge Authors</p>
      </div>
      <div class="ad-box" :class="{ overwolf: !isElectron }">
        <div class="flex flex-col w-full mt-auto mb-auto" v-show="advertsEnabled">
          <div
            v-if="!showPlaceholder && !isElectron"
            id="ow-ad"
            ref="adRef"
            style="max-width: 400px; max-height: 300px; display: flex; margin: 0 auto"
          />
          <video
            @click="platform.get.utils.openUrl('https://go.ftb.team/creeperhost')"
            class="cursor-pointer"
            width="400"
            height="300"
            autoplay
            muted
            loop
            style="margin: 0 auto"
            v-if="isElectron && !isDevEnv"
          >
            <source src="https://dist.modpacks.ch/windows_desktop_src_assets_CH_AD.mp4" type="video/mp4" />
          </video>
        </div>
      </div>
      <!--      <small-->
      <!--        class="text-xs text-center block cursor-pointer mt-2 text-muted hover:text-white hover:shadow"-->
      <!--        @click="reportAdvert"-->
      <!--        v-if="!isElectron"-->
      <!--        >Report advert</small-->
      <!--      >-->
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import platform from '@/utils/interface/electron-overwolf';
import Component from 'vue-class-component';
import { Action, State } from 'vuex-class';
import { SettingsState } from '@/modules/settings/types';
import { AuthState } from '@/modules/auth/types';
import { Prop } from 'vue-property-decorator';
import { getLogger } from '@/utils';

/**
 * TODO: clean all of this up, it's a mess and a copy of a different component
 */
@Component
export default class AdAside extends Vue {
  @State('settings') public settings!: SettingsState;
  @State('auth') public auth!: AuthState;
  @Action('reportAdvert') public reportAd!: any;
  @Prop({ default: false }) public isDev!: boolean;

  private logger = getLogger('ad-aside-vue');

  private ad: any;
  platform = platform;
  showPlaceholder = false;

  get isElectron() {
    return platform.isElectron();
  }

  async mounted() {
    this.logger.info('Loaded ad sidebar widget');
    // Kinda dirty hack for this file
    if (!this.platform.isOverwolf()) {
      return;
    }

    setTimeout(() => {
      this.loadAds();
    }, 1500);
  }

  loadAds() {
    this.logger.info('Loading advert system');
    //@ts-ignore
    if (typeof OwAd === 'undefined' || !OwAd) {
      this.showPlaceholder = true;
      this.logger.info('No advert loaded');
    } else {
      //@ts-ignore
      if (window.ad) {
        this.logger.info('Is window advert');
        //@ts-ignore
        this.ad = window.ad;
        this.ad.refreshAd();
      } else {
        this.logger.info('Created advert');
        //@ts-ignore
        this.ad = new OwAd(document.getElementById('ow-ad'));
        this.logger.info(this.ad, document.getElementById('ow-ad'));

        //@ts-ignore
        window.ad = this.ad;
      }

      this.ad.addEventListener('error', (error: any) => {
        this.showPlaceholder = true;
        this.logger.info('Failed to load ad');
        this.logger.info(error);
      });
      this.ad.addEventListener('player_loaded', () => {
        this.logger.info('player loaded for overwolf');
      });
      this.ad.addEventListener('display_ad_loaded', () => {
        this.logger.info('display_ad_loaded overwolf');
      });
      this.ad.addEventListener('play', () => {
        this.logger.info('play overwolf');
      });
      this.ad.addEventListener('complete', () => {
        this.logger.info('complete ads for overwolf');
      });
      this.ad.addEventListener('impression', () => {
        fetch(`${process.env.VUE_APP_MODPACK_API}/public/analytics/ads/video`);
      });
      this.ad.addEventListener('display_ad_loaded', () => {
        fetch(`${process.env.VUE_APP_MODPACK_API}/public/analytics/ads/static`);
      });
    }
  }

  get advertsEnabled(): boolean {
    if (this.auth?.token?.activePlan === null) {
      return true;
    }

    // If this fails, show the ads
    return (this.settings?.settings?.showAdverts === true || this.settings?.settings?.showAdverts === 'true') ?? true;
  }

  get isDevEnv() {
    return process.env.NODE_ENV === 'development';
  }

  public reportAdvert() {
    // const el = document.getElementById('banner300x250');
    // if (!el) {
    //   this.showPlaceholder = true;
    //   return;
    // }
    // // @ts-ignore
    // const adHTML = el.children[0].contentDocument.body.innerHTML;
    // // @ts-ignore
    // this.starAPI(api => {
    //   api.game.setTarget(null);
    // });
    // el.innerHTML = '';
    // this.ad = null;
    // // @ts-ignore
    // window.ad = null;
    // this.showPlaceholder = true;
    // this.reportAd({ object: '', html: adHTML });
  }
}
</script>

<style lang="scss" scoped>
.ad-aside {
  padding: 1.5rem;
  background: rgba(black, 0.5);

  display: flex;
  flex-direction: column;

  justify-content: space-between;
  transition: background-color 0.3s ease-in-out;

  &.is-dev {
    background-color: #171c1f;
  }

  .heading {
    nav {
      .item {
        padding: 0.25rem 0;
        opacity: 0.75;

        cursor: pointer;
        transition: opacity 0.25s ease-in-out, transform 0.25s ease-in-out;

        &.up:hover {
          transform: translateY(-3px);
        }

        &:hover {
          opacity: 1;
          transform: translateX(5px);
        }

        svg {
          margin-right: 1rem;
        }
      }
    }
  }

  .ad-space {
    .heart {
      position: relative;
      svg {
        color: #ff4040;
      }

      .less-than-three {
        position: absolute;
        left: 50%;
        transform: translateX(-50%) scale(1.5);
        opacity: 0;

        animation: scale 2s ease-in-out infinite;

        &.heart-3 {
          animation: scale2 2s ease-in-out infinite;
          animation-delay: 0.4s;
        }

        @keyframes scale {
          0%,
          100% {
            transform: translateX(-50%) scale(1);
            opacity: 0;
          }
          50% {
            transform: translateX(-50%) scale(1.5);
            opacity: 0.6;
          }
        }

        @keyframes scale2 {
          0%,
          100% {
            transform: translateX(-50%) scale(1);
            opacity: 0;
          }
          50% {
            transform: translateX(-50%) scale(1.8);
            opacity: 0.2;
          }
        }
      }
    }

    .ad-box {
      background: black;
      width: 300px;
      height: 250px;
      overflow: hidden;
      display: flex;
      align-items: center;

      border-radius: 5px;

      &.overwolf {
        width: 400px;
        height: 300px;
      }
    }
  }
}
</style>
