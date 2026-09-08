"""One-shot authoring receipt: no dependency resolution or build/test execution."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path('/Users/abdelrahman/Projects/parlor')
EVIDENCE = ROOT / 'remediation-runs/2026-09-07-local-readiness/reviews/dependency-native-notices-01'
OUTPUT = ROOT / 'composeApp/src/commonMain/composeResources/files/legal'
assert not OUTPUT.exists(), 'Never overwrite pre-existing resources'
assert not (ROOT / 'config/third-party-notices.json').exists()
base = json.loads((EVIDENCE / 'draft-notice-inputs-01/NOTICE_INPUTS.json').read_bytes())
extra = json.loads((EVIDENCE / 'draft-notice-inputs-supplement-02/NOTICE_INPUTS.json').read_bytes())
assert hashlib.sha256((EVIDENCE / 'draft-notice-inputs-01/NOTICE_INPUTS.json').read_bytes()).hexdigest() == '1a63fda721a571ee2db378b6a00372d0ec51b447b502ddeae0a3f6ccd4db2101'
assert hashlib.sha256((EVIDENCE / 'draft-notice-inputs-supplement-02/NOTICE_INPUTS.json').read_bytes()).hexdigest() == '147f3b9229dff8f06361f65f9df5fbb8bf2f6383cebed1fb4b59018ba496ebc0'

# Component labels describe inspected INPUTS, not a final dead-strip result.
details = {
 'APACHE-2.0.txt': (['Kotlin', 'Compose Multiplatform', 'Skiko', 'Wuffs 0.3.3', 'Piex', 'Apache-derived components'], ['android', 'ios', 'desktop-development'], 'SHARED_LICENSE_TEXT', 'Shared Apache License 2.0 text; component-specific notices and other licenses are retained separately. This is not a claim that every dependency is Apache-licensed.'),
 'Skiko-NOTICE.txt': (['Skiko 0.9.37.4'], ['ios'], 'NATIVE_INPUT_NOTICE', 'Exact upstream Skiko NOTICE acknowledges Android Open Source Project adaptations in the inspected published iOS input.'),
 'Skia-LICENSE.txt': (['Skia', 'skcms'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Skiko iOS inputs contain libskia.a and rendering components. This BSD notice also matches the inspected skcms source header; individual final-link survival is not asserted.'),
 'ICU-LICENSE.txt': (['ICU', 'SkUnicode ICU adapter'], ['ios'], 'NATIVE_INPUT_LICENSE', 'libicu.a and libskunicode_icu.a are compiled inputs. The complete upstream combined license includes build-script terms; those sections do not make ICU runtime or this application GPL-licensed.'),
 'HarfBuzz-COPYING.txt': (['HarfBuzz'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Text-shaping archives and objects are present in the inspected iOS inputs. The separate hb-ucd source and Unicode data notices supplement this root COPYING file.'),
 'Expat-COPYING.txt': (['Expat'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Expat objects are present both in a separate archive and within libskia.a; this is the exact source-revision MIT copyright and permission text.'),
 'WebP-COPYING.txt': (['WebP'], ['ios'], 'NATIVE_INPUT_LICENSE', 'The inspected Skiko iOS archives include WebP inputs; this preserves their BSD copyright and permission text.'),
 'WebP-PATENTS.txt': (['WebP'], ['ios'], 'NATIVE_INPUT_COMPANION_TERMS', 'Exact companion patent grant for the inspected input; retaining the text is not publisher patent or legal approval.'),
 'DNG-SDK-NOTICE.txt': (['Adobe DNG SDK', 'Google DNG SDK patches'], ['ios'], 'NATIVE_INPUT_CUSTOM_LICENSE', 'Compiled DNG objects are input to Skiko iOS. This is the custom Adobe license, technology notice and Google patch attribution, not Apache-2.0. Final object retention and publisher acceptance of custom terms require separate review.'),
 'DNG-SDK-PATENTS.txt': (['Adobe DNG SDK'], ['ios'], 'NATIVE_INPUT_COMPANION_TERMS', 'Exact DNG patent grant and technology-notice wording. Legal applicability and acceptance remain the publisher\'s responsibility.'),
 'libjpeg-turbo-LICENSE.md': (['libjpeg-turbo', 'Independent JPEG Group'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Inspected inputs include libjpeg API and SIMD objects. This upstream rollup distinguishes IJG and TurboJPEG API terms; no TurboJPEG API object was identified in the inspected arm64 inputs.'),
 'libjpeg-turbo-README.ijg.txt': (['Independent JPEG Group'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Complete upstream IJG README and terms; the separate INDEX also preserves the exact required acknowledgement sentence.'),
 'Wuffs-Skia-wrapper-LICENSE.txt': (['Skia Wuffs wrapper'], ['ios'], 'NATIVE_INPUT_LICENSE', 'The SkWuffsCodec wrapper is BSD-licensed. Generated Wuffs 0.3.3 input has Apache-2.0 terms covered by the shared Apache text; these are separate components.'),
 'Kotlin-ThreeTenBP-LICENSE.txt': (['ThreeTenBP portions of Kotlin time'], ['ios'], 'STDLIB_INPUT_LICENSE', 'Kotlin/Native 2.4.10 Instant derives from ThreeTenBP. Parlor uses kotlin.time.Instant for clocks and snapshot timestamps.'),
 'Kotlin-Harmony-NOTICE.txt': (['Apache Harmony portions of Kotlin/Native'], ['ios'], 'RUNTIME_INPUT_NOTICE', 'Native regex and native floating-point parsing/conversion derive from Apache Harmony; runtime inputs include dtoa. Apache-2.0 terms are in the shared license file.'),
 'Kotlin-MPMC-QUEUE-LICENSE.txt': (['Dmitry Vyukov bounded MPMC queue'], ['ios'], 'NATIVE_INPUT_LICENSE', 'The pinned default concurrent-mark-and-sweep runtime uses ConcurrentMark, ParallelProcessor and BoundedQueue. This preserves the exact BSD notice, without asserting every path executes.'),
 'libpng-LICENSE.txt': (['libpng'], ['ios'], 'OPTIONAL_BINARY_ACKNOWLEDGEMENT', 'Input is present. The PNG license says binary-product acknowledgement is appreciated, not required; this optional copy preserves the source terms without inventing a missing-notice obligation.'),
 'zlib-LICENSE.txt': (['zlib'], ['ios'], 'OPTIONAL_BINARY_ACKNOWLEDGEMENT', 'Input is present. The zlib license says acknowledgement is appreciated, not required; source notices must not be removed.'),
 'Kotlin-Boost-LICENSE.txt': (['Boost-derived Kotlin/Native code', 'UTFCPP'], ['ios'], 'OBJECT_CODE_NOTICE_EXCEPTION', 'Boost-derived runtime utilities and UTFCPP have a machine-executable object-code notice exception. Included as a helpful license copy, not an independently mandatory binary-only acknowledgement.'),
 'Kotlin-libbacktrace-LICENSE.txt': (['libbacktrace in Kotlin/Native runtime'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Kotlin 2.4.10 CompilerOutput collects libbacktrace runtime modules for Apple targets based on target support, independently of NOOP source-information dispatch. Default iOS input inclusion is established; final optimized code survival is not asserted.'),
 'Kotlin-Unicode-LICENSE.txt': (['Unicode portions of Kotlin/Native Apache-Harmony-derived regex'], ['ios'], 'STDLIB_INPUT_LICENSE', 'Four native regex range/set sources retain these legacy Unicode notices. The implementation contains upstream Kotlin/Native adaptations; Parlor has not authored modifications to Unicode data. This is separate from modern generated character tables.'),
 'HarfBuzz-hb-ucd-ISC-LICENSE.txt': (['HarfBuzz hb-ucd'], ['ios'], 'NATIVE_INPUT_LICENSE', 'Exact hb-ucd.cc lines 1-15 retain the Grigori Goronzy ISC notice. The compiled hb-ucd object occurs in inspected iOS archives; this notice is distinct from HarfBuzz root COPYING.'),
 'Unicode-V3-LICENSE.txt': (['Unicode 16.0.0 data in HarfBuzz', 'Unicode 13.0.0 data in Kotlin/Native'], ['ios'], 'GENERATED_DATA_LICENSE', 'The access-dated current Unicode License v3 applies to Public data under the official current terms unless otherwise indicated. This 1991-2026 grant is not misrepresented as an immutable historical 2024 grant; legacy regex terms remain separate.'),
 'BouncyCastle-1.85-LICENSE.md': (['Bouncy Castle bcprov-jdk18on 1.85'], ['android'], 'ANDROID_INPUT_LICENSE', 'Exact published Android input JAR member. Earlier AAB inspection already found this license text; this resource copy is a supplement, not evidence that Android previously shipped no licenses.'),
 'SLF4J-2.0.16-LICENSE.txt': (['SLF4J API 2.0.16'], ['android'], 'ANDROID_INPUT_LICENSE', 'Exact published Android input JAR member, including original CRLF bytes. Source-input presence alone does not establish earlier final-package retention.'),
}
index = '''PARLOR - THIRD-PARTY NOTICES
===========================

This software is based in part on the work of the Independent JPEG Group.
This product includes DNG technology under license by Adobe Systems Incorporated.

About this supplement
---------------------
This directory preserves notices for reviewed dependency inputs used by Parlor.
All files accompany the app as common Compose resources; platform labels below
identify the inspected inputs, not separate platform-exclusive delivery.
Input inclusion is not proof that every object survives optimization and linking.
This supplement is not an exhaustive per-file copyright inventory, an application
license, publisher legal approval, or a statement of Store readiness. Other
licenses may already accompany dependency resources elsewhere in the package.

The 25 upstream text files are copied verbatim (including their line endings).
INDEX.txt is a Parlor-authored guide, not a replacement for the full terms.
The repository's config/third-party-notices.json records exact byte sizes,
SHA-256 hashes, upstream sources, versions and applicability. It is not a
runtime dependency or part of a signing/credential store.

Shared license text
-------------------
APACHE-2.0.txt: Apache License 2.0 for Kotlin/Compose, Skiko, generated Wuffs
0.3.3, Piex and Apache-derived inputs. This is not a claim that every dependency
is Apache-licensed. Component notices below remain separate.

Skiko / Skia native inputs (iOS)
-------------------------------
Skiko 0.9.37.4 incorporates Skia package m138-80d088a-1.
- Skiko-NOTICE.txt: upstream Android Open Source Project adaptation notice.
- Skia-LICENSE.txt: Skia BSD terms; matching inspected skcms terms.
- ICU-LICENSE.txt: complete combined ICU terms. Its build-script sections do not
  make the ICU runtime or Parlor application GPL-licensed.
- HarfBuzz-COPYING.txt: HarfBuzz root copyright and permission text.
- HarfBuzz-hb-ucd-ISC-LICENSE.txt: separate Grigori Goronzy hb-ucd source notice.
- Expat-COPYING.txt: Expat copyright and MIT permission text.
- WebP-COPYING.txt and WebP-PATENTS.txt: WebP terms and companion patent grant.
- DNG-SDK-NOTICE.txt and DNG-SDK-PATENTS.txt: custom Adobe terms, Google patch
  attribution and patent grant. The DNG SDK is not Apache-licensed.
- libjpeg-turbo-LICENSE.md and libjpeg-turbo-README.ijg.txt: full upstream
  license rollup and IJG README. Inspected inputs include libjpeg and SIMD;
  TurboJPEG API object inclusion is not claimed.
- Wuffs-Skia-wrapper-LICENSE.txt: BSD terms for the separate Skia wrapper;
  generated Wuffs input uses the shared Apache License 2.0 text.
- libpng-LICENSE.txt and zlib-LICENSE.txt: optional acknowledgements. These
  licenses say binary acknowledgement is appreciated, not required.

Kotlin/Native 2.4.10 inputs (iOS)
-------------------------------
- Kotlin-ThreeTenBP-LICENSE.txt: ThreeTenBP portions of kotlin.time.Instant.
- Kotlin-Harmony-NOTICE.txt: Apache Harmony-derived regex and numeric conversion.
- Kotlin-MPMC-QUEUE-LICENSE.txt: default runtime bounded queue attribution.
- Kotlin-libbacktrace-LICENSE.txt: runtime modules collected for Apple targets
  based on target support, even when source-information dispatch selects NOOP.
  The default input is established; final dead-strip survival is not asserted.
- Kotlin-Unicode-LICENSE.txt: Unicode portions of Kotlin/Native's
  Apache-Harmony-derived regular-expression implementation. These are upstream
  Kotlin/Native adaptations, not Parlor-authored Unicode data changes.
- Kotlin-Boost-LICENSE.txt: Boost-derived runtime utilities and UTFCPP. The
  machine-executable object-code exception makes this a voluntary binary notice.

Unicode generated data (iOS)
---------------------------
Unicode-V3-LICENSE.txt: access-dated current Unicode License v3 (2026-09-07),
covering Public data under the official current terms unless otherwise indicated.
HarfBuzz's inspected generated table identifies Unicode 16.0.0; Kotlin/Native's
character table generator identifies Unicode 13.0.0. The current 1991-2026 grant
is not represented as an immutable historical grant. Legacy regex notices in
Kotlin-Unicode-LICENSE.txt remain distinct from these modern data tables.

Android input license copies
----------------------------
- BouncyCastle-1.85-LICENSE.md: exact bcprov-jdk18on 1.85 JAR license member.
- SLF4J-2.0.16-LICENSE.txt: exact slf4j-api 2.0.16 JAR license member.
Bouncy Castle and some AndroidX license texts were already observed elsewhere
in an earlier Android package. These copies do not claim their previous absence.

Review and remaining responsibility
----------------------------------
Package verification proves only that this complete reviewed resource set is
present with exact bytes in the inspected artifact. It does not establish
which native objects survive the linker, legal sufficiency, end-user UI access,
commercial/custom-license acceptance, trademarks, content rights, signing,
physical-device behavior or Store approval. Those remain separate review gates.
'''.encode('utf8')
OUTPUT.mkdir(parents=True)
entries=[]
for folder, source in [('draft-notice-inputs-01',base),('draft-notice-inputs-supplement-02',extra)]:
 for item in source['inputs']:
  name=item['draft_file']; raw=(EVIDENCE/folder/name).read_bytes()
  assert len(raw)==item['bytes'] and hashlib.sha256(raw).hexdigest()==item['sha256']
  components,platforms,classification,applicability=details[name]
  receipt=item['source_receipt']
  if receipt is None:
   receipt=next(r for r in json.loads((EVIDENCE/'fetch-receipts-01.json').read_bytes()) if Path(r.get('destination','')).name==item['source_evidence_file'])
  provenance={'method':'full-text','url':receipt.get('url',receipt.get('canonical_publication_url')),
              'accessed_on':receipt.get('accessed_at',receipt.get('observed_at'))[:10],
              'source_sha256':receipt['sha256'],'source_bytes':receipt['size']}
  if name=='HarfBuzz-hb-ucd-ISC-LICENSE.txt':
   provenance.update(method='source-lines',line_range=[1,15])
  if 'archive_sha256' in receipt:
   provenance.update(method='archive-member',archive_sha256=receipt['archive_sha256'],archive_bytes=receipt['archive_size'],member=receipt['member'])
  (OUTPUT/name).write_bytes(raw)
  entries.append({'name':name,'kind':'upstream-text','bytes':len(raw),'sha256':item['sha256'],
                  'components':components,'platforms':platforms,'classification':classification,
                  'applicability':applicability,'provenance':provenance})
(OUTPUT/'INDEX.txt').write_bytes(index)
entries.append({'name':'INDEX.txt','kind':'index','bytes':len(index),'sha256':hashlib.sha256(index).hexdigest(),
                'components':['Parlor dependency-input notice supplement'],'platforms':['android','ios','desktop-development'],
                'classification':'INDEX','applicability':'First-party guide to the exact upstream texts, input-level platform applicability, optional notices and remaining review limits.',
                'provenance':{'method':'authored-index'}})
catalog=(ROOT/'gradle/libs.versions.toml').read_bytes()
versions=catalog.decode().split('[versions]',1)[1].split('[libraries]',1)[0]
pins=dict(re.findall(r'^([a-z][a-z0-9-]*)\s*=\s*"([^"\n]+)"\s*$',versions,re.M))
upstream=dict(base['upstream_versions'])
upstream.update(icu_commit='364118a1d9da24bb5b770ac3d762ac144d6da5a4',harfbuzz_commit='08b52ae2e44931eef163dbad71697f911fadc323',expat_commit='8e49998f003d693213b538ef765814c7d21abada',webp_commit='845d5476a866141ba35ac133f856fa62f0b7445f',dng_sdk_commit='dbe0a676450d9b8c71bf00688bb306409b779e90',libjpeg_turbo_commit='e14cbfaa85529d47f9f55b0f104a579c1061f9ad',libpng_commit='ed217e3e601d8e462f7fd1e04bed43ac42212429',zlib_commit='646b7f569718921d7d4b5b8e22572ff6c76f2596',wuffs='0.3.3',harfbuzz_unicode_data='16.0.0',kotlin_unicode_data='13.0.0',unicode_license_accessed='2026-09-07',bouncycastle='1.85',slf4j='2.0.16')
manifest={'schema_version':1,'scope':'REVIEWED_INPUT_NOTICE_SUPPLEMENT_NOT_EXHAUSTIVE_OR_LEGAL_APPROVAL',
          'resource_directory':'composeApp/src/commonMain/composeResources/files/legal',
          'resource_namespace':'com.parlor.app.resources','resource_count':26,
          'catalog':{'path':'gradle/libs.versions.toml','sha256':hashlib.sha256(catalog).hexdigest(),'pins':pins},
          'upstream_versions':upstream,'files':sorted(entries,key=lambda x:x['name'])}
raw=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode()
assert b'/Users/' not in raw and b'.gradle/caches' not in raw
(ROOT/'config/third-party-notices.json').write_bytes(raw)
print(json.dumps({'status':'AUTHORING_ONLY_NOT_TEST_EXECUTION','upstream_text_count':25,'upstream_bytes':sum(e['bytes'] for e in entries if e['kind']=='upstream-text'),'total_resources':len(entries),'manifest_sha256':hashlib.sha256(raw).hexdigest(),'index_sha256':hashlib.sha256(index).hexdigest()}))
