<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {ElMessage} from 'element-plus'
import {useAuthStore} from '@/stores/auth'
import {changeMilestoneStatus,type Milestone} from '@/api/projects'
import {approveProjectMilestone} from '@/api/project-finance'
const props=defineProps<{milestone:Milestone;closed:boolean;billingMode:string}>()
const emit=defineEmits<{changed:[]}>(),auth=useAuthStore(),{t}=useI18n()
const pending=ref(false),visible=ref(false),approved=ref(true),reason=ref('')
let generation=0
const canManage=computed(()=>auth.user?.roles.some(r=>['ADMIN','PROJECT_MANAGER'].includes(r)))
const hasVersion=computed(()=>Number.isInteger(props.milestone.version) && props.milestone.version>=0)
const canReview=computed(()=>!props.closed && !pending.value && hasVersion.value && props.billingMode==='MILESTONE' && auth.hasPermission('workflow:approve') && props.milestone.status==='COMPLETED' && props.milestone.billable && ['DRAFT','REJECTED','NOT_REQUIRED'].includes(props.milestone.approvalStatus))
const next=computed<Milestone['status'] | null>(()=>props.milestone.status==='PLANNED'?'IN_PROGRESS':props.milestone.status==='IN_PROGRESS'?'COMPLETED':null)
async function advance(){
 if(props.closed || pending.value || !canManage.value || !hasVersion.value || !next.value)return
 const row=props.milestone,state=next.value,token=generation;pending.value=true
 try{await changeMilestoneStatus(row.id,state,row.version);if(token===generation){ElMessage.success(t('projects.saved'));emit('changed')}}
 catch{if(token===generation){ElMessage.error(t('projects.milestoneFlow.failed'));emit('changed')}}
 finally{if(token===generation)pending.value=false}
}
function review(value:boolean){if(!canReview.value)return;approved.value=value;reason.value='';visible.value=true}
function close(){if(!pending.value)visible.value=false}
async function saveReview(){
 if(!canReview.value || !reason.value.trim() || reason.value.length>500)return
 const row=props.milestone,token=generation,payload={approved:approved.value,reason:reason.value.trim(),version:row.version};pending.value=true
 try{await approveProjectMilestone(row.id,payload);if(token===generation){visible.value=false;ElMessage.success(t('projects.saved'));emit('changed')}}
 catch{if(token===generation){visible.value=false;ElMessage.error(t('projects.milestoneFlow.failed'));emit('changed')}}
 finally{if(token===generation)pending.value=false}
}
watch([()=>props.milestone.id,()=>props.milestone.projectId,()=>props.closed,()=>auth.user?.organizationId],()=>{generation++;pending.value=false;visible.value=false;reason.value=''})
onBeforeUnmount(()=>{generation++})
</script>
<template>
 <div class="milestone-actions">
  <el-button v-if="next && canManage" link type="primary" :disabled="closed || pending || !hasVersion" @click="advance">{{t(next==='IN_PROGRESS'?'projects.milestoneFlow.start':'projects.milestoneFlow.finish')}}</el-button>
  <el-button v-if="canReview" link type="primary" @click="review(true)">{{t('projects.milestoneFlow.approve')}}</el-button>
  <el-button v-if="canReview && milestone.approvalStatus!=='REJECTED'" link type="danger" @click="review(false)">{{t('projects.milestoneFlow.reject')}}</el-button>
 </div>
 <el-dialog :model-value="visible" :title="t(approved?'projects.milestoneFlow.approve':'projects.milestoneFlow.reject')" width="min(500px, calc(100vw - 32px))" :show-close="!pending" :close-on-click-modal="!pending" :close-on-press-escape="!pending" @update:model-value="close">
  <p>{{milestone.name}} · {{milestone.billingAmount}} {{milestone.currencyCode}}</p><p>{{t('projects.milestoneFlow.reviewHelp')}}</p>
  <el-input v-model="reason" type="textarea" :maxlength="500" :disabled="pending" :placeholder="t('projects.milestoneFlow.reason')" />
  <template #footer><el-button :disabled="pending" @click="close">{{t('projects.cancel')}}</el-button><el-button type="primary" :loading="pending" :disabled="pending || !canReview || !reason.trim()" @click="saveReview">{{t('projects.save')}}</el-button></template>
 </el-dialog>
</template>
<style scoped>.milestone-actions{display:flex;flex-wrap:wrap;gap:6px}</style>
