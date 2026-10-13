import { QUEST_DATA } from './OSRS-Fate-Locked/data/questData';
import { DIARY_DATA } from './OSRS-Fate-Locked/data/diaryData';
import { ALL_CA_TASKS } from './OSRS-Fate-Locked/data/caTasks';
const diff = (d: string) => (d.match(/\((.*)\)/) || [,''])[1];
const quests = Object.values(QUEST_DATA).map((q: any) => ({
  name: q.name, kind: q.kind, points: q.points, series: q.series || null,
  difficulty: diff(q.difficulty), skills: q.skills, prereqs: q.prereqs,
  combatLevel: q.combatLevel || 0,
}));
const diaries = Object.values(DIARY_DATA).map((d: any) => ({ id: d.id, area: d.id.replace(/ (Easy|Medium|Hard|Elite)$/, ''), tier: d.tier, skills: d.skills, quests: d.quests }));
const caTiers: Record<string, number> = {};
for (const t of ALL_CA_TASKS as any[]) caTiers[t.tierId] = (caTiers[t.tierId] || 0) + 1;
console.log(JSON.stringify({ source: 'Nubles/OSRS-Fate-Locked data/ (questData, diaryData, caTasks)', quests, diaries, caTiers }));
