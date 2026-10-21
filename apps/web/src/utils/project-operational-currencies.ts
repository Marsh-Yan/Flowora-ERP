import type { OperationalCurrencyAmount, Project } from '@/api/projects'

export function aggregateProjectAmounts(projects: Project[]): OperationalCurrencyAmount[] | null {
 const grouped=new Map<string,OperationalCurrencyAmount>()
 for(const project of projects) {
  if(!Array.isArray(project.operationalAmounts))return null
  for(const row of project.operationalAmounts) {
   if(!/^[A-Z]{3}$/.test(row.currencyCode) || !Number.isFinite(row.actualCost) || !Number.isFinite(row.billableAmount))return null
   const old=grouped.get(row.currencyCode)??{currencyCode:row.currencyCode,actualCost:0,billableAmount:0}
   grouped.set(row.currencyCode,{currencyCode:row.currencyCode,actualCost:Number((old.actualCost+row.actualCost).toFixed(4)),billableAmount:Number((old.billableAmount+row.billableAmount).toFixed(4))})
  }
 }
 return [...grouped.values()].sort((a,b)=>a.currencyCode.localeCompare(b.currencyCode))
}

export function operationalMoney(value:number,currency:string,locale:string) {
 if(!/^[A-Z]{3}$/.test(currency) || !Number.isFinite(value))return '—'
 return `${new Intl.NumberFormat(locale,{style:'currency',currency,minimumFractionDigits:2,maximumFractionDigits:4}).format(value)} ${currency}`
}

// Each monetary row has one currency; project hours appear only on its first row.
export function projectOperationalCsvRows(projects:Project[]): Array<Array<string|number>> {
 return projects.flatMap(project=>{
  const source=aggregateProjectAmounts([project])
  const rows=Array.isArray(source)?source.length?source:[{currencyCode:project.currencyCode,actualCost:0,billableAmount:0}]:null
  const prefix=[project.number,project.name,project.status,project.targetDate,project.progressPercent,project.currencyCode]
  if(!rows)return [[...prefix,'Unavailable','','',project.actualHours]]
  return [...rows].sort((a,b)=>a.currencyCode.localeCompare(b.currencyCode)).map((row,index)=>[...prefix,row.currencyCode,Number.isFinite(row.actualCost)?row.actualCost.toFixed(4):'',Number.isFinite(row.billableAmount)?row.billableAmount.toFixed(4):'',index===0?project.actualHours:''])
 })
}
