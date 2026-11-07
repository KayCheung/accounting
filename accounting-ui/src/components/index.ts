import type { App } from 'vue'

import AmountDisplay from './common/AmountDisplay/index.vue'
import AmountInput from './common/AmountInput/index.vue'
import StatusTag from './common/StatusTag/index.vue'
import DictSelect from './common/DictSelect/index.vue'
import BaseCheckboxGroup from './common/BaseCheckboxGroup/index.vue'
import BaseDialog from './common/BaseDialog/index.vue'
import ConfirmDialog from './common/ConfirmDialog/index.vue'
import ActionButton from './common/ActionButton/index.vue'
import BasePagination from './common/BasePagination/index.vue'
import BaseTable from './common/BaseTable/index.vue'

export {
  AmountDisplay,
  AmountInput,
  StatusTag,
  DictSelect,
  BaseCheckboxGroup,
  BaseDialog,
  ConfirmDialog,
  ActionButton,
  BasePagination,
  BaseTable
}

export default {
  install(app: App) {
    app.component('AmountDisplay', AmountDisplay)
    app.component('AmountInput', AmountInput)
    app.component('StatusTag', StatusTag)
    app.component('DictSelect', DictSelect)
    app.component('BaseCheckboxGroup', BaseCheckboxGroup)
    app.component('BaseDialog', BaseDialog)
    app.component('ConfirmDialog', ConfirmDialog)
    app.component('ActionButton', ActionButton)
    app.component('BasePagination', BasePagination)
    app.component('BaseTable', BaseTable)
  }
}
