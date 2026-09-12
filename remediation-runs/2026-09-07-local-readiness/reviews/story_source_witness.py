"""Read-only, source-level witnesses for fictional bundled-content contradictions.

This is not Kotlin runtime or editorial/legal certification. Original JSON comes
from the committed repair baseline, never a checkout or user-file replacement.
"""
from pathlib import Path
import datetime
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'game-modes/whodunit/src/commonMain/composeResources/files/cases/'
HEAD = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
def body(case): return case['payload']
def char(case,id): return next(x for x in body(case)['characters'] if x['id']==id)
def clue(case,pool,character,id): return next(x['text'] for x in body(case)['cluePools'][pool][character] if x['id']==id)
def line(case,id,part): return next(x for x in char(case,id)['guiltyBrief']['timeline'] if part in x['action'])
CHECKS = [
 ('Daniel interval','last-dinner',lambda c:'9:10 — ten minutes later' in clue(c,'killerPointing','daniel-hargrove','kp-daniel-4')),
 ('Vivienne fireplace','last-dinner',lambda c:'library fireplace' in clue(c,'killerPointing','vivienne-cross','kp-vivienne-1')),
 ('Layla speech/detour','layla-halabi',lambda c:line(c,'amal-alhalabi','كلمته القاسية')['time']=='8:45 مساءً' and 'قبل أن يلقي كلمته' in char(c,'rana-naqshabandi')['guiltyBrief']['method']),
 ('Layla three 858 methods','layla-halabi',lambda c:all('الثامنة وثمانٍ وخمسين دقيقة' in char(c,p)['guiltyBrief']['method'] for p in ['ghassan-shoufan','souad-alhalabi','tarek-alhalabi'])),
 ('Amal kinship','layla-halabi',lambda c:'ملاحظة والدها' in body(c)['revealNarratives']['ghassan-shoufan']),
 ('Walid interval','jasmine-ring',lambda c:'بعد ثماني دقائق' in char(c,'walid-qasem')['guiltyBrief']['method']),
 ('Fadi following-morning metadata agreement','jasmine-ring',lambda c:'صباح الغد' in clue(c,'finalStrong','fadi-darwish','jr-fadi-fs-2')),
 ('Refaat note provenance','khan-el-khalili',lambda c:'الأسبوع الماضي' in body(c)['revealNarratives']['refaat-elsayed']),
 ('Zahra elder-sibling age','saidi-inheritance',lambda c:'62 عاماً' in body(c)['bedrockClues'][0] and '68 عاماً' in char(c,'hagga-zahra')['relationshipToVictim']),
 ('Magda elder-sibling age','iskenderia-corniche',lambda c:'75 عاماً' in char(c,'madame-magda')['relationshipToVictim'] and '70 عاماً' in body(c)['bedrockClues'][0]),
 ('Mokhtar two-year history','zamalek-ramadan',lambda c:'سنتان من اختلاسات' in body(c)['revealNarratives']['hagg-mokhtar']),
 ('Zamalek impossible minimum','zamalek-ramadan',lambda c:'قبل نحو ساعتين' in next(x['text'] for x in body(c)['cluePools']['publicUniversal'] if x['id']=='zr-bedrock-3')),
]
rows=[]
for name,case,check in CHECKS:
 rel=PREFIX+case+'.json';before=subprocess.check_output(['git','show',HEAD+':'+rel],cwd=ROOT);after=(ROOT/rel).read_bytes()
 old=json.loads(before);new=json.loads(after)
 row={'witness':name,'file':rel,'baseline_commit':HEAD,'baseline_sha256':hashlib.sha256(before).hexdigest(),'current_sha256':hashlib.sha256(after).hexdigest(),'baseline_satisfies_chosen_consistency_rule':check(old),'current_satisfies_chosen_consistency_rule':check(new)}
 assert not row['baseline_satisfies_chosen_consistency_rule'],row
 assert row['current_satisfies_chosen_consistency_rule'],row
 rows.append(row)
print(json.dumps({'recorded_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'method':'Read-only original/current JSON predicate witnesses; original/source-level failure, not red Kotlin execution','witnesses':rows,'result':'12 original failures independently witnessed;12 current predicates satisfied','limits':'Full cross-field rationale and false-alibi/cross-variant counter-evidence require accompanying independent source review. Ages are authorized editorial choices. Fadi is metadata mismatch, not currently rendered weekday proof.'},indent=2,ensure_ascii=False))
