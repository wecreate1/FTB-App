<template>
  <div class="ms-auth text-center">
    <template v-if="!loading">
      <h3 class="text-3xl mt-6 mb-4 font-bold">Waiting for response...</h3>
      <div class="logo my-12">
        <img class="mx-auto" src="@/assets/images/branding/microsoft.svg" alt="Microsoft logo" />
      </div>
      <p class="mb-6">
        A window should have just popped up. Follow the instructions on screen, once you've finished, you'll see that
        you've been signed in here.
      </p>
    </template>
    <template v-else>
      <h3 class="text-3xl mt-6 mb-4 font-bold">Loading</h3>
      <Loading class="my-4" size="8" />
      <p class="mb-6">We're just checking the credentials, one moment please.</p>
    </template>
  </div>
</template>

<script lang="ts">
import Component from 'vue-class-component';
import Vue from 'vue';
import YggdrasilAuthForm from '@/components/templates/authentication/YggdrasilAuthForm.vue';
import Loading from '@/components/atoms/Loading.vue';
import platform from '@/utils/interface/electron-overwolf';
import { Action } from 'vuex-class';

@Component({
  components: { YggdrasilAuthForm, Loading },
})
export default class MicrosoftAuth extends Vue {
  @Action('sendMessage') public sendMessage: any;
  @Action('loadProfiles', { namespace: 'core' }) public loadProfiles!: () => Promise<void>;

  loading = false;

  mounted() {
    this.openMsAuth();
  }

  async openMsAuth() {
    try {
      const res = await platform.get.actions.openMsAuth();

      this.loading = true;
      if (!res || !res.key || !res.iv || !res.password) {
        this.$emit('error', 'Unable to authenticate. Please try again.');
        return;
      }

      const responseRaw: any = await fetch('https://msauth.feed-the-beast.com/v1/retrieve', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          key: res.key,
          iv: res.iv,
          password: res.password,
        }),
      });

      const response: any = (await responseRaw.json()).data;

      if (!response || !response.liveAccessToken || !response.liveRefreshToken || !response.liveExpires) {
        this.$emit('error', 'Failed to retrieve essential information, please try again.');
        return;
      }

      this.sendMessage({
        payload: {
          type: 'profiles.ms.authenticate',
          ...response,
        },
        callback: async (data: any) => {
          if (!data || !data.success) {
            this.$emit('error', data.response || 'An unknown error occurred.', 'final');
            return;
          }

          // No error
          await this.loadProfiles();
          this.$emit('authenticated');
        },
      });
    } catch (e) {
      console.log(e);
      this.$emit('error', 'Fatal error whilst trying to retrieve your account details, please try again.');
    } finally {
      this.loading = false;
    }
  }
}
</script>

<style lang="scss" scoped></style>
