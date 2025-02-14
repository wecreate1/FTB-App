<template>
  <article class="message" :class="{ [type]: true }">
    <div class="message-header" v-if="hasHeader">
      <font-awesome-icon class="icon" v-if="icon" :icon="icon"></font-awesome-icon>
      <div class="heading">
        <slot name="header" v-if="$slots.header" />
        <template v-else>{{ header }}</template>
      </div>
    </div>
    <div class="message-body">
      <slot />
    </div>
  </article>
</template>

<script lang="ts">
import Component from 'vue-class-component';
import Vue from 'vue';
import { Prop } from 'vue-property-decorator';
import { IconLookup, IconName } from '@fortawesome/fontawesome-common-types';

export enum MessageType {
  NORMAL = 'normal',
  SUCCESS = 'success',
  INFO = 'info',
  WARNING = 'warning',
  DANGER = 'danger',
}

@Component
export default class Message extends Vue {
  @Prop({ default: MessageType.NORMAL }) type!: MessageType;
  @Prop({ default: null }) icon!: null | IconName | IconLookup;
  @Prop({ default: '' }) header!: string;

  get hasHeader() {
    return this.$slots.header || this.header;
  }
}
</script>

<style lang="scss" scoped>
.message {
  background: #333333;
  border-radius: 5px;

  .message-header + .message-body {
    border-width: 0;
  }

  .message-header {
    display: flex;
    align-items: center;
    padding: 0.8rem 1rem;
    background-color: #454545;
    border-radius: 5px 5px 0 0;
    font-weight: bold;

    .icon {
      margin-right: 1rem;
    }
  }

  .message-body {
    padding: 1rem;
    border-left: 4px solid #454545;
    border-radius: 5px 0 0 5px;
  }

  &.success .message-body {
    border-color: var(--color-success-button);
  }

  &.info .message-body {
    border-color: var(--color-info-button);
  }

  &.warning .message-body {
    border-color: var(--color-warning-button);
  }

  &.danger .message-body {
    border-color: var(--color-danger-button);
  }

  &.success .message-header {
    background-color: var(--color-success-button);
  }

  &.info .message-header {
    background-color: var(--color-info-button);
  }

  &.warning .message-header {
    background-color: var(--color-warning-button);
  }

  &.danger .message-header {
    background-color: var(--color-danger-button);
  }
}
</style>
