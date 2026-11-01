#!/usr/bin/env python3
"""Static security contract for Parlor's GitHub Actions release workflows."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
EXPECTED = {
    "production-verification.yml",
    "github-distribution.yml",
    "github-test-release.yml",
    "testing-candidate.yml",
    "testing-external-promotion.yml",
    "production-promotion.yml",
    "ios-input-investigation.yml",
}
# Exact reviewed evidence-only workflow. Adding it never widens the generic
# security contract: changed privileges, runners, triggers or uploads fail closed.
INPUT_INVESTIGATION_SHA256 = "c5b316d9ad127e36ef430dacd4c93e5811d52ed6529087f687b8ef34b69c97cc"
# Optional, source-bound decoder of one existing failed result. This profile is
# not a release gate and grants no execution, cache, signing or write privilege.
# Any change to its workflow, immutable intake, or export controls needs review.
PUBLIC_TEXT_REVIEW = "ios-public-text-review.yml"
PUBLIC_TEXT_REVIEW_SHA256 = "311d0cf79767d929dfac625dac3cf6f1f929c6bb7e87f157f9dd176f917b59a0"
PUBLIC_TEXT_REVIEW_COMPANIONS = {
    "review_public_text_commit.py": "5c40db8fba05f4a80f22acb9b84620d0130bd870a7ed4ee25fbbd17fc2175808",
    "test_public_text_commit_review.py": "57ae0242d2050f6c2db84a613df568d1cba51bb97a5dfab6692ccb65c8a89279",
}
PUBLIC_BACK_REVIEW = "ios-public-back-review.yml"
PUBLIC_BACK_REVIEW_SHA256 = "755493e19d26f5c63bde2d002345491718bbb72612dd731aaeea81118260b9ef"
PUBLIC_BACK_REVIEW_COMPANIONS = {
    'review_public_back_commit.py': 'c4413b77424fea299302495ab40b9dc2886c8343056ad56297e6286a3b6cfe45',
    'test_public_back_commit_review.py': 'facb78dfb2ec3979e514eefe0c48cdc894ea95ed0404ea06ae35c023837236b1',
}
# Separate exact decoder for the PR300 catalog failure. Not a production gate.
PUBLIC_CATALOG_REVIEW = "ios-public-catalog-review.yml"
PUBLIC_CATALOG_REVIEW_SHA256 = "cd4642f114adc20cf5b9280b5ec085e774f65905b99fb8e039040249fcab6747"
# Optional exact decoder of the retained PIN19 earlier failure. No app execution.
SETTLED_FAILURE_REVIEW = 'ios-settled-failure-review.yml'
SETTLED_FAILURE_REVIEW_SHA256 = '481b078a2402b17f7b51bc66d0a5d36aa78b1da45f73b8ec070b57052e59f56d'
SETTLED_FAILURE_REVIEW_COMPANIONS = {'review_pin19_failure.py': '7d6b88f67e0ecf5324e907797bbc9c349163e0d6328dc0583c2a82a81c74f8b8', 'test_pin19_failure_review.py': 'f0ff42408e27649b6a040f886679a843ca6811672fc328d607cb3c732415148b', 'public_attachments.py': 'ad93b59659be452750d8b7cc5aa02c42547048c364557d0251f913a936729ec8'}
PUBLIC_CATALOG_REVIEW_COMPANIONS = {
    "review_public_catalog_commit.py": "97c3d176a3adf366156c6aa5bb12f246e6956e4def075e94e0c2f45aa627fe0e",
    "test_public_catalog_commit_review.py": "912943d70f5c0ecd9adec8f2257754ef17ac12ffe1f389f5283223dbad984d4a",
}
STORE_WORKFLOWS = {"testing-candidate.yml", "testing-external-promotion.yml", "production-promotion.yml"}
PROMOTION_WORKFLOWS = {"testing-external-promotion.yml", "production-promotion.yml"}
FREE_APPLE_RELEASE_RUNNER = "${{ inputs.apple_release_runner == 'intel' && 'macos-15-intel' || 'macos-15' }}"
FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
# Separate frozen decoder of PR296's two original public setup failures.
PUBLIC_SETUP_PAIR_REVIEW = "ios-public-setup-pair-review.yml"
PUBLIC_SETUP_PAIR_REVIEW_SHA256 = "c949768c410c07d91c249f1e6044165224e275106314fff736454d1f81961688"
PUBLIC_SETUP_PAIR_REVIEW_COMPANIONS = {
    'review_public_setup_pair_commit.py': '4c39679c5a5fb0e8e4a65e1e614cb666b0d743b543e711c1a35a81dcaf6393af',
    'test_public_setup_pair_commit_review.py': '01e4d28f1f8cbf6638cff29fbbdeb5c00e97071065a851c50a5954252757643d',
}
# Separate copy-only native investigation. It never qualifies a production lane
# or changes the existing generic workflow; only this exact frozen profile runs.
PUBLIC_SETUP_BOUNDARIES = "ios-public-setup-boundaries.yml"
PUBLIC_SETUP_BOUNDARIES_SHA256 = "c11f8f0f8041ebcf91ed8081d76c8665bff4a64f30eced70c10bbc128dd4a163"
PUBLIC_SETUP_BOUNDARIES_COMPANIONS = {'pin06_public_setup_pair.py': '6a3bc3c5e638262919269d0dd803bf977e0f2157e351e4fcfcc8c4be6d870a4a', 'test_pin06_public_setup_pair.py': '2edb46c6ca6cc27276aca8a2f5a5cb1e195305ce001a980dd414574fa92159d7', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'pin01_copy.py': 'acf6e21c400287459740469c624ae589a791be39e3381898f150647caa07f385', 'pin01_receipts.py': '2d778db5298bd2ac6e594bd5d4c389dc5f812c32e24ae77fb3467c36b7a98848', 'pin02_capture.py': '4ed9b4257263e88abc640cf904243adfd6bce00787e93a9e468bc5648ba15f0f', 'pin03_catalog_copy.py': '0092a943ca2103f4ecf829cb574b976d1d767f2fa340687335738b710cfce6c7', 'frozen_sources.py': '6402b9fc49298d143d1893dd6b609079b96fbeeb9672432816cd9357ff310962'}

# Explicit synthetic failed-export control, separate from all application gates.
SYNTHETIC_FAILURE_EXPORT = "ios-synthetic-failure-export.yml"
SYNTHETIC_FAILURE_EXPORT_SHA256 = "454f33e2e4de97d67f6a08599beb0986bafcb949fd0ed8bf45436491d2906313"
SYNTHETIC_FAILURE_EXPORT_COMPANIONS = {'run_pin07.py': '5ff286084167b000cbb7de9b1d92d2b838f6565210cb89af9da79913730d5eb3', 'pin07_native_adapter.py': '5421a07246d5be5a40fad88c09879f431934f29299c9c06812ad69fc702a5e43', 'test_pin07_native_adapter.py': '9330fcbbfc916c54d205c9e21a9b4402ff2ba4d30ee33d41bef77310430acfee', 'pin07_failure_control.py': '0a6b300c7aaa00571d876d4a91d5a16cb1921494a2142ecded0cec6a8e20c0f7', 'test_pin07_failure_control.py': '684f64cdaec02feee4bbd27be785844a209dd008a3410edd595af1209cd03094', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2'}

# Exact current failed-source observations, not an application gate or retry.
CURRENT_INPUT_BOUNDARIES = "ios-current-input-boundaries.yml"
CURRENT_INPUT_BOUNDARIES_SHA256 = "90f9bf297eda4d7677ffd29d32efc841581cb9feb506719c9c661111fdf3a50b"
CURRENT_INPUT_BOUNDARIES_COMPANIONS = {'current_boundary_adapter.py': '36e1346a0c6d3ad2039d57989e35c160a17482c692cb58f9abcf2002dd36d466', 'run_current_boundary.py': '7525f744980d79cad4636087682c5ff371e7942bb09d9364caaf47a2e9655d7e', 'test_current_boundary_adapter.py': '8a3c7b3553b823bad02b63c7c069e23f4a3ec3668ae8075c3cef390e4a890f08', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'pin06_public_setup_pair.py': '6a3bc3c5e638262919269d0dd803bf977e0f2157e351e4fcfcc8c4be6d870a4a', 'pin07_native_adapter.py': '5421a07246d5be5a40fad88c09879f431934f29299c9c06812ad69fc702a5e43'}


# Optional exact native getter capability control; never a shipping check.
NATIVE_ACCESSIBILITY_CAPABILITY = 'ios-native-accessibility-capability.yml'
NATIVE_ACCESSIBILITY_CAPABILITY_SHA256 = '621c5b641cbefa519af9a6e8c3df5f4f9c7c3717522b98a341b00eb0d797c92a'
NATIVE_ACCESSIBILITY_CAPABILITY_COMPANIONS = {'run_pin08.py': '818e576147ec69419b80074b18ecff5330755402b1920b7b582374eb30a02611', 'pin08_accessibility.py': 'e517b5b575a6b35d9acfb80fbc1d400db12e6d56101651094dcc06c0b488a9be', 'test_pin08_accessibility.py': '23d128dbd7ab5e68f5de83c6ae5105657031f76171abc90f0db1b81317f24a9e', 'PIN08AccessibilityObserver.swift.in': 'ecf2df64d3a7b6adc3ddf1e90adaa04c80c1c7b4cdecf428474df5a4123eb931', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'pin07_failure_control.py': '0a6b300c7aaa00571d876d4a91d5a16cb1921494a2142ecded0cec6a8e20c0f7', 'pin07_native_adapter.py': '5421a07246d5be5a40fad88c09879f431934f29299c9c06812ad69fc702a5e43', 'pin06_public_setup_pair.py': '6a3bc3c5e638262919269d0dd803bf977e0f2157e351e4fcfcc8c4be6d870a4a'}

NATIVE_BOUNDARY_OBSERVATIONS = 'ios-native-boundary-observations.yml'
NATIVE_BOUNDARY_OBSERVATIONS_SHA256 = '5de6bcfb9d09ceaa440c5b2d8f1536cd9d8b62364d467cd323838b329f83c366'
NATIVE_BOUNDARY_OBSERVATIONS_COMPANIONS = {'run_pin09.py': 'a57ceecf789f23c419ac9d83fd7a3d2f4b450cb36a17a7f477b67db574dc937e', 'pin09_native_boundary.py': '70ac606fdb6be8dee1e795dd2f536f749071a4cf79ca101523d09b1816419cce', 'test_pin09_native_boundary.py': 'b877925f4b2ab6c028ca93978c57c009197edaaa2cfed82a51137a3cde9e3b70', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'current_boundary_adapter.py': '36e1346a0c6d3ad2039d57989e35c160a17482c692cb58f9abcf2002dd36d466', 'pin08_accessibility.py': 'e517b5b575a6b35d9acfb80fbc1d400db12e6d56101651094dcc06c0b488a9be', 'PIN08AccessibilityObserver.swift.in': 'ecf2df64d3a7b6adc3ddf1e90adaa04c80c1c7b4cdecf428474df5a4123eb931', 'pin06_public_setup_pair.py': '6a3bc3c5e638262919269d0dd803bf977e0f2157e351e4fcfcc8c4be6d870a4a'}

# Exact additional public-root observations; never a qualification workaround.
NATIVE_ROOT_CAPABILITY = 'ios-native-root-capability.yml'
NATIVE_ROOT_CAPABILITY_SHA256 = '017332aa70a90b7c216c2f7773d158789d64b838810e105e6063da775e4ed802'
NATIVE_ROOT_CAPABILITY_COMPANIONS = {'run_pin10.py': 'f87916cc6e196bd923cb3bda9e6608f715f7aa48fd0cc9146b4d9fc8a00ff961', 'pin10_root_boundary.py': '49e0c3cc365ef299ee7435f291def95a9bb45a907880b60c2455cd2f6b382d2d', 'test_pin10_root_boundary.py': '72b0c8233826978ea899859bd7d587a2ee6f4afe1196ab65a82fa3204ed73101', 'PIN10RootObserver.swift.in': '1dc4ad1fb63788be0f7550ebb4c8dde82677ec74600343139d50729f8f682c32', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'pin08_accessibility.py': 'e517b5b575a6b35d9acfb80fbc1d400db12e6d56101651094dcc06c0b488a9be', 'PIN08AccessibilityObserver.swift.in': 'ecf2df64d3a7b6adc3ddf1e90adaa04c80c1c7b4cdecf428474df5a4123eb931', 'pin09_native_boundary.py': '70ac606fdb6be8dee1e795dd2f536f749071a4cf79ca101523d09b1816419cce', 'pin06_public_setup_pair.py': '6a3bc3c5e638262919269d0dd803bf977e0f2157e351e4fcfcc8c4be6d870a4a', 'current_boundary_adapter.py': '36e1346a0c6d3ad2039d57989e35c160a17482c692cb58f9abcf2002dd36d466'}

NATIVE_ROOT_OBSERVATIONS = 'ios-native-root-observations.yml'
NATIVE_ROOT_OBSERVATIONS_SHA256 = 'c5404093a2349589c1fe8380cf7f8aff0c6ee483f1c00336acfa5ddd36922279'
NATIVE_ROOT_OBSERVATIONS_COMPANIONS = {'run_pin11.py': 'fd44d919abec2435d0af34e85cd2c8ce17c1c305efb1105bfd9927b2e2abfb61', 'pin11_root_observations.py': '90b7f3e68b0c466fb2d69165d730caa4f7d02ee367de61be57127f1e02d37529', 'test_pin11_root_observations.py': '26cbc62fff5a2afaa6ade8ea45d6075fb6bf93ea399e612d40574e0c1fb0e3e2', 'pin10_root_boundary.py': '49e0c3cc365ef299ee7435f291def95a9bb45a907880b60c2455cd2f6b382d2d', 'test_pin10_root_boundary.py': '72b0c8233826978ea899859bd7d587a2ee6f4afe1196ab65a82fa3204ed73101', 'PIN10RootObserver.swift.in': '1dc4ad1fb63788be0f7550ebb4c8dde82677ec74600343139d50729f8f682c32', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'pin08_accessibility.py': 'e517b5b575a6b35d9acfb80fbc1d400db12e6d56101651094dcc06c0b488a9be', 'PIN08AccessibilityObserver.swift.in': 'ecf2df64d3a7b6adc3ddf1e90adaa04c80c1c7b4cdecf428474df5a4123eb931', 'pin09_native_boundary.py': '70ac606fdb6be8dee1e795dd2f536f749071a4cf79ca101523d09b1816419cce', 'pin06_public_setup_pair.py': '6a3bc3c5e638262919269d0dd803bf977e0f2157e351e4fcfcc8c4be6d870a4a', 'current_boundary_adapter.py': '36e1346a0c6d3ad2039d57989e35c160a17482c692cb58f9abcf2002dd36d466', 'run_pin12.py': '6b2637be786e2707eafbacab48d2992618792f3581f210819fe9000960f64d3a', 'pin12_public_boundaries.py': 'f5491f5d8b0e119172ce4eab2548d2a1303d05f0b5267d3d70896a46580d6a53', 'test_pin12_public_boundaries.py': '36ca5b2c4205a81252f3f399586b754ab35bb27773242d96eb936ef3d3006714', 'pin11_public_failure_coverage.py': '5f64cd0d1379ea6d907f301239c730b3fb0131db58ba4019695eecdeb6ab881f', 'test_pin11_public_failure_coverage.py': 'e9754b8d297eb8aeab636912168f7efebe4222316155aa546146fa3f2c3aee61', 'pin13_original_cohort.py': '2bdfd26ea484290f1c1a1767e07ec241cd75ed22b3e1c6458a8545a0ecbd428f', 'test_pin13_original_cohort.py': '108e21037ee4ee33f5f7e56be5af01095ddaa7b57723f7c3515d577d8a312fbe', 'run_pin13.py': '6dba242bccdf535c022eb0ccdb6eec3b72fb28ed21fe25b034930e1bc9fd301f', 'pin14_original_resources.py': '4bbb50af25b733fc877b0839e54e7c0c3437fe62cf29d0ba7342d54dfd6da1bc', 'test_pin14_original_resources.py': 'fe87494518cb4202b077df85f76f59cb0afd4688ffb2351159a43910d17c710e', 'run_pin14.py': '7a27c75d6dfcaff1fe086f50de0a4e6b33361ac0a91f42277613ffbac2e1b1ce', 'run_pin15.py': '0383e50b4e11300211d7b4ba9e9a903a5bcbe97d779e8fb88931c0f57b253170', 'pin15_combined_xcode.py': '1898b5c244fa166d6dc95200c86b9c9967e336f0778fe86cd29a1b61392785e6', 'test_pin15_combined_xcode.py': 'b237700838e43fc7b968393d9f1106b3ba4f3278b59d3f5b4b17d6152c5ad288', 'pin16_aggregate_context.py': '6b925774de8f792e3bf870ac6e9db7a35605201e2849ddeac81a7603a3fe3e76', 'test_pin16_aggregate_context.py': 'e702b86dd084a874d7852d483e8f58c90f2d3363aa8a696a163540f9dafaeb0d', 'run_pin16.py': '85ca7a3c24a41b36b502df593168ff79127b079dab8836849c8549fa03650e56', 'PIN16NativeCases.json': '4cc25fd8e96df235b440d64d5554b836e20c9cfce617fabd67ef273e837d711f', 'pin17_observer_comparison.py': 'e4c44c568f07505051e55ab3d729befde1ad84113e40962361f92cba300be315', 'test_pin17_observer_comparison.py': 'ae74556f6b5f8f1f3c9a9e6305ca442d1f6f6b053208833f0043fef44531dac2', 'run_pin17.py': '34e94361b6ff2e073b6cd53a25530e98f4bd4194c06c7e11a260b04d822ed1c9', 'run_pin18.py': '24299fa2f27da655a76747cb4b46c78a826fb89d2746dfaf8308af647f317ceb', 'pin18_root_idle_stimulus.py': 'd5181b8a3ae62b485befce99fb3e7a4e9ad37b127998f9b35d17180e9d2e4e55', 'test_pin18_root_idle_stimulus.py': 'b4dda8c3d2faaa8da631c3b12ee45a5e0c54020525560054cd18f8c02c69bd82', 'run_pin19.py': '590d60129f3a3413d1e5d7dcd3946291409d105a00baa8ee04cc954c0966ae3e', 'pin19_settled_catalog_idle.py': 'c771d565767dae0c53a241ab11bf8ab0ed024a28f39ec7d0d5564a959fcbc163', 'test_pin19_settled_catalog_idle.py': '441244d81e056ab32f6b100b7ccc99dd4773c92439b9ae40a421485b52acbcd5', 'run_pin20.py': '6871904d7b269f25b8060c1cba342ed6a899524c6264176505d60423232153c4', 'pin20_catalog_motion.py': '4f9a90e0c70ec44c167824612410ef6c2fedd6a6710ceb56dd82061142793864', 'test_pin20_catalog_motion.py': '1737b08156da08da0cfde954e5d51443d130069d62dae5eb197eca6ad402dca5', 'PIN20CatalogMotion.kt.in': '31db821fa6f9a8a17261ecc17f96187422c415921eef54f17d5bb82edee7f2c7', 'PIN20CatalogMotion.swift.in': 'a413db43931aa80cce8ad6c6f3bbacbceb25978f757e5e69cde9b99c5c3f65a7'}
NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'pin21_motion_export_control.py': '450f47140a9ec0e951fab44a8f44f933856e229b2c1840890c5577eb6a0e31cd', 'run_pin21.py': 'd2e350f971280ec0b47a097eeb055d2c361e9205f9da1f9cf9cf86f76edad2d9', 'test_pin21_motion_export_control.py': '15fceb6466667a54422510789614896c89fff567ab1a9017132070bf0a323d4e'}
NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'PIN22NativeFrame.swift.in': 'd9313b50e1079e96fd6f8328b3f9d85382ad17ff8f963a891512e92dfac4ac82', 'pin22_native_frame_boundary.py': 'cadf443eb0f7a215c8ee4c2a8cadf5af911769f95983534098db8ea8e155e1fb', 'run_pin22.py': '4a257c37c946a26fda54eea73fec82d8cc8ed8530f0fbb2024d8827f57342d3e', 'test_pin22_native_frame_boundary.py': '69ec51072d052632d46ada4f432a99af3a7ea3284eaa32a7aa5961cc0e40c57c'}
NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'PIN05TextObservation.kt.in': '88ceae03dc07cba145d32c8c6c8de58de1b976f3d3183ca8ab6174461a3a6efe', 'PIN05TextInputInterceptor.kt.in': '282e8795c19940e4013f0173f694b0fbaebd44408aa5674c75e3984575aa8f48', 'PIN05TextInputControlTest.kt.in': '77bcea0365b83978baac69a8d85657eda3ba2b53714209ddd5f96c97791e5a38', 'PIN23TextInput.swift.in': '7e00616eb94b04afdd890142bb7e8c9e5aaec898c9fe798d19ee876af0c56612', 'pin23_text_session_boundary.py': 'f716a663e9530fd52ce682b1c03cf0171c2cac16491946bd8471b9c9f2d61bfd', 'run_pin23.py': '7924da8f2c73377bc4c7b34339eeb9cdf0b8a1ad7ef30009e77fb6c57c3c4e46', 'test_pin23_text_session_boundary.py': 'fabe71e334518cfa96c0e9a92d30000fbd745ea885473de214e7f9f73ca80b61', 'fixtures/PIN23NameInputScreen.kt.source': '4730d58c86ec11cb64a8a8540c523b9ce84c259d7250178bc1aa249bafa01686'}
NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'pin24_text_export_control.py': '3a6456e03a089c5b3d11bc848dedb251451d3e103dee1a35de9715080c941180', 'run_pin24.py': 'd4baf46a814570c972344ba84e0d45b97a9c797b10be6f9e54284be706dc68e1', 'test_pin24_text_export_control.py': '2a97270aa49395008e7779ad557239cf253d0f6e265310836603311f12488ff5'}

NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'PIN25NativeInputDelivery.swift.in': 'cdc65db6af08d324bbbf47940a030c67998dce56745bd38d9409f9d690464a5a', 'pin25_native_input_delivery.py': '235bc5247ca39b1cbaa268d72390e29c09152d76a8283889b1f8def550d24a25', 'run_pin25.py': '60e0ed57968c18b62e5fd9ea596bdd2a797dc1658e9343486399fe7756914e8a', 'test_pin25_native_input_delivery.py': '6df28de502822f6db7624b85e23aa51405c8c939017c30e6e6aab45388bce853'}

NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'pin26_delivery_export_control.py': '882feeb79d376eeb2073635ad567b5962c3981945e745f344cd33cfb48e25366', 'run_pin26.py': 'bf4f6eb8c6db9183f18a133d0e0b1a2c0960d617ff6c38a9a69c3095652ec742', 'test_pin26_delivery_export_control.py': 'b60ae07223bf2ab64a7acda22daa76813d45b0fe8c39818c77ac62c0f135fa6b'}

NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'PIN27InputContainer.kt.in': '44d539d1aaf57615e97714c073063d4033fa4de52b11066a9fe9e415ea5390e2', 'PIN27InputAttachment.swift.in': '4892fff80f78157fc4e75a79bbf00aa562a26d8da620afcf58aae05fb0a30a1c', 'pin27_attached_input_delivery.py': '1283ec02138d0a95feabe616c43e8885a7aead774f268fcd4fa262392733e2da', 'run_pin27.py': '620b9ce3d7090d91b25546b5092bee75ff9b6950b446a64af8f9e00cba14a476', 'test_pin27_attached_input_delivery.py': 'd08e2f8252a68b5cebfb85fb510d928f1060409dbdb02ad0ceab4d75061086d9'}

NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'PIN28Lifecycle.swift.in': '3390f3792e96530b715446e0079cecca3700d593b5b9143dc7bd75d10c529e53', 'pin28_original_lifecycle_boundaries.py': 'f288c339a9732230a7137aa447fe10e52727745e32aa8481f525ffe0511d7b59', 'run_pin28.py': '7b7ad5ee7810b60119c4b9cf4f915b4e96c3271c834bb4d2bf6a5579d8c98a93', 'test_pin28_original_lifecycle_boundaries.py': '6c0fac75094074448bbb57bba6b636c89b9d7a2d36c50028450e65f81615a297'}
NATIVE_ROOT_OBSERVATIONS_COMPANIONS |= {'PIN29EquivalentQuery.swift.in': '559273425980e231f7bf17a5faf1b9283f979abf727b2abf47c4187784b24ddf', 'pin29_equivalent_query_boundary.py': '634ae5500d4032f7cb12f79b713a5ea7b4762fed41861ef21cfbe7ef91fc2188', 'run_pin29.py': '0d9d1554419ebf3ed9208c055fc703fa3cada7ae61e8e9858a256a7ce7a874e6', 'test_pin29_equivalent_query_boundary.py': '98949b8eb7f3af4b25e696a641f5063933b068ad0d7584285840b3a7db2f1a3e'}

PUBLIC_SESSION_STRESS = 'ios-public-session-stress.yml'
PUBLIC_SESSION_STRESS_SHA256 = 'fc3547cf3a9e6a627dd4307a86e8894d51bbdcdf1c53b1822053aadbb900fda4'
PUBLIC_SESSION_STRESS_COMPANIONS = {'pin30_public_session_stress.py': '46a85c59736dbfab7d8bcd247105f151b7da2871ec80eae6cf05ff75c245f96d', 'run_pin30.py': 'b326e6d97801e1b24ba581b86981bc48f13cbc580bad2d4fc218747574d12447', 'test_pin30_public_session_stress.py': 'e7240134d1e2f64a594a5760efe8ceb139cdea6a156fb96b24b82b7ee85fe616', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'pin28_original_lifecycle_boundaries.py': 'f288c339a9732230a7137aa447fe10e52727745e32aa8481f525ffe0511d7b59', 'pin29_equivalent_query_boundary.py': '634ae5500d4032f7cb12f79b713a5ea7b4762fed41861ef21cfbe7ef91fc2188'}

CATALOG_INPUT_BOUNDARY = 'ios-catalog-input-boundary.yml'
CATALOG_INPUT_BOUNDARY_SHA256 = '5efea62ed22c5601a88fac57bbede01d4d61c3f60ae044e3e3d770b7429d55c7'
CATALOG_INPUT_BOUNDARY_COMPANIONS = {'pin31_catalog_input_boundary.py': '349c4ceafbd1c5003b3221e13994d3ae8385c616629f53cb7945bf46915ff15d', 'run_pin31.py': '4107324c8dad009720749e548516f683cf8d1074bc063982b4712fa03c963f35', 'test_pin31_catalog_input_boundary.py': '611e73492f56d69dc48f252698cd07e9943d704cacbf4e14fd812ea31b79e90a'}

OVERSCROLL_GETTER_BOUNDARY = 'ios-overscroll-getter-boundary.yml'
OVERSCROLL_GETTER_BOUNDARY_SHA256 = '58c388e9032236f1fc2199c43dbcb87c4d1707f006ba9389b427cfff9daec5f3'
OVERSCROLL_GETTER_BOUNDARY_COMPANIONS = {'PIN32CatalogOverscroll.kt.in': 'c5fc5549f695f22660f0156c1e1bd8d90737483872c964b3caa0294fdb42d090', 'PIN32CatalogOverscroll.swift.in': 'efa38e8cbce7ced4fd2f079f347fee226c2922561fd365b31f4e009e54f2aab7', 'pin32_catalog_overscroll_boundary.py': '076da9e2c6fc2aab3dfbd0da37b8b0e872248889e0475a67831fcf0b875d9a7f', 'test_pin32_catalog_overscroll_boundary.py': 'eed532f46b68cd03daa4f00efe9be9b56eba3b84f80652c1d5b1984a286654b8', 'run_pin32.py': 'f84885a91bb921e8203781a7ecdc2a038222f135d2eb71a561be4dcfff45a5e0'}

ORIGINAL_TOUCH_DELIVERY = 'ios-original-touch-delivery.yml'
ORIGINAL_TOUCH_DELIVERY_SHA256 = '06bec4ec7b2a6c81a640e7921d243bed9dd1ea3d0c1800d45cc50fb7ab111249'
ORIGINAL_TOUCH_DELIVERY_COMPANIONS = {'PIN33OriginalTouchDelivery.swift.in': 'f776602c71b96f75e5c695f769406de700e81cfbf64a83c45c0b94b0137a436f', 'PIN33OriginalTouchABIControl.swift.in': 'c261e89054c7f084258ea03f362ac9ccc0dfa0b4cd419865e5dfb665848205e3', 'pin33_original_touch_delivery.py': 'e0efbc5033a568eca2759f78b52f0ac27e6c64d6431f097758857ded0d5c261f', 'pin33_touch_decoder.py': 'd649900ee3cbde011a7704f6afeffdc813c5bd1477c152db28d6f4cb1150349b', 'test_pin33_original_touch_delivery.py': 'd2bb8b789f0e920e6ffe19ef0cf759a4b8edf5863f300f69f4b51415fe8934f1', 'run_pin33.py': '14aa18b9a7b95de99b5cbe0a8fb633f978cf4df0629a1bf912f47718e2e0142e'}

ORIGINAL_TOUCH_EXPORT_CONTROL = 'ios-original-touch-export-control.yml'
ORIGINAL_TOUCH_EXPORT_CONTROL_SHA256 = '2e243301f37241adf32c3e830c838ebdc2f4588277535bd8b6890ec803efdfc3'
ORIGINAL_TOUCH_EXPORT_CONTROL_COMPANIONS = {'pin34_touch_export_control.py': '4e03307ae7952da46bb7c20b8786dd3a87f8cecc33231017f540678bbf98f16a', 'test_pin34_touch_export_control.py': '81907ca50e0aa88b55a07b9564c02b77128cda4213f0af1b43e86bbb1d6ba933', 'run_pin34.py': '41c529f9e3cd2ee8c334021af71269215ffb33a1821bb712a51b4b86652367bc'}

ORIGINAL_TOUCH_COHORT = 'ios-touch-original-cohort.yml'
ORIGINAL_TOUCH_COHORT_SHA256 = 'da5a8bf17f403f49a4a0371ae769083ef2414a837042f53ed2cb1d11053c3e5a'
ORIGINAL_TOUCH_COHORT_COMPANIONS = {'pin35_original_touch_cohort.py': '032d8b93447fc1d2c87b4f36549f6543d3d41d6cdbd3429cd192bbaeed0d5fc8', 'test_pin35_original_touch_cohort.py': '53e4adb1ec62c3881d18dc614918c401b0c8c2effe4756e6ff70071def748d4e', 'run_pin35.py': 'd6f40329017e4b068ccbf1c53501df81d4750d5123a1728a499166f63204d80d'}


NATIVE_ONLY_EXPORT = 'ios-native-only-export.yml'
NATIVE_ONLY_EXPORT_SHA256 = 'bd3874e36c22d10ebd7dd53a92198e11cf617db987f24a45e3b282c4d01aa178'
NATIVE_ONLY_EXPORT_COMPANIONS = {'PIN39NativeOnly.swift.in': '5091e6b8e823d114e8eb3d1c637608fd875cda92b400ca193cba136ba7f93c78', 'pin39_native_only_export.py': '54b3f8eb6bf4cd09f80dc25211add5b295e4e70a1cdf43626d19151ca93228b9', 'run_pin39.py': '193b1f7600af159bf964413eae0af0e38e3c8d5af598030e60b31c53ba308c8e', 'test_pin39_native_only_export.py': '371633455dd993677baac9b34e1434fa33725976777793a368096e0da732b2a4', 'run_pin03.py': '1414db87d1264e8cf4b9d0b91a528996ecd8d22b492249a80b38c2b4e5bef7b2', 'PIN02Capture.swift.in': '4a3814846d15f854f2e7ad2510fa04795a220ed55f0d65793ee185e8d5716433', 'PIN02FailureRequest.swift.in': '07a613781ea793d323f7a54aede4dba8c94281bbc0ff94b18b437e1d71391740', 'PIN08AccessibilityObserver.swift.in': 'ecf2df64d3a7b6adc3ddf1e90adaa04c80c1c7b4cdecf428474df5a4123eb931', 'PIN10RootObserver.swift.in': '1dc4ad1fb63788be0f7550ebb4c8dde82677ec74600343139d50729f8f682c32', 'PIN22NativeFrame.swift.in': 'd9313b50e1079e96fd6f8328b3f9d85382ad17ff8f963a891512e92dfac4ac82', 'PIN33OriginalTouchDelivery.swift.in': 'f776602c71b96f75e5c695f769406de700e81cfbf64a83c45c0b94b0137a436f', 'pin22_native_frame_boundary.py': 'cadf443eb0f7a215c8ee4c2a8cadf5af911769f95983534098db8ea8e155e1fb', 'pin33_touch_decoder.py': 'd649900ee3cbde011a7704f6afeffdc813c5bd1477c152db28d6f4cb1150349b'}

# Finite native-only original samples retain the separately verified sentinel contract.
NATIVE_ONLY_SAMPLES = 'ios-native-only-samples.yml'
NATIVE_ONLY_SAMPLES_SHA256 = '3b3094e2fca4cf59fe30401b40d0103bf3feb5485c53567d9b7d1c16bb827ba9'
NATIVE_ONLY_SAMPLES_COMPANIONS = {'pin40_native_only_samples.py': '5e69b7a12d6456c12e9863f818b183ba9b76193a72910a95926117990f0f04b2', 'test_pin40_native_only_samples.py': '6eb8c47eaa40348ffc974000aa889c8a4af2f639db2a0fee31ebf81f16c95c8f', 'run_pin40.py': '14ef62a2ef249acad8e1a3b5f609db8d7c0e0820c4e1bb5d4c82441034a86f14', 'pin30_public_session_stress.py': '46a85c59736dbfab7d8bcd247105f151b7da2871ec80eae6cf05ff75c245f96d', 'pin11_public_failure_coverage.py': '5f64cd0d1379ea6d907f301239c730b3fb0131db58ba4019695eecdeb6ab881f'}

OWNED_LEAF_PAIR = 'ios-owned-leaf-pair.yml'
OWNED_LEAF_PAIR_SHA256 = '9160a1b2e2bd253f9a939928423a37ed2104e839967310e7d3766fba17436195'
OWNED_LEAF_PAIR_COMPANIONS = {'pin43_owned_leaf_pair.py': 'd444a5e0d4e40e299c6dabfaa6546878bd72db2a2aefe4c7f52fe6edc29c2570', 'run_pin43.py': '7f4cfe402c6132b5a25368148efea8dd1c58b7c0ae2d588946d5f5f611e59524', 'test_pin43_owned_leaf_pair.py': '7d60ba563b8194eeda1658a53b4cc1d9608acdeef8c6340d33e51c4011dc5cf7'}

OWNED_LEAF_EXPORT = 'ios-owned-leaf-export.yml'
OWNED_LEAF_EXPORT_SHA256 = 'a0f0619f366f52a25dfb7d7702ded9c600817faceef950db580b82b4fb56ee60'
OWNED_LEAF_EXPORT_COMPANIONS = {'PIN42OwnedLeafTouch.swift.in': 'f7c1657bcae823b61623ae1556fbb00567869d244ccdebd3a62160c9dc037049', 'PIN42OwnedLeafOnly.swift.in': '8c2390dad12d9829f9bbee924e42e7fff6fd341057c1c4e775e6455b645d27e5', 'PIN42OwnedLeafABIControl.swift.in': 'cc432eaa9554f064fb5494916c4b3103bd756a73b1c4df4f8b7a5a21ff6d27d3', 'pin42_owned_leaf_export.py': 'a9c19c4f08a64dbd4d6cbc40d7d031dd8557f497e37948eb666deb044a3921fc', 'test_pin42_owned_leaf_export.py': '09be7d672e5e5b3f111b448a5ad23f7c6af47a4bf52c4f326cb757a2a1549952', 'run_pin42.py': 'e2d3963d8854a87db0f11be7399db693cca9660fbf0c478c8fca490326602013'}

ORIGINAL_INPUT_ONLY = 'ios-original-input-only.yml'
ORIGINAL_INPUT_ONLY_SHA256 = '2b745c5391ff3126f25c505f27b2871984e28932dab5b7da0f4068d8890e396c'
ORIGINAL_INPUT_ONLY_COMPANIONS = {'PIN41OriginalInputOnly.swift.in': 'a602d1d8d90f65c7f829401b892ca1f9e9b7c87ddfcd4bd517b40a37c9fc621d', 'pin41_original_input_only.py': '827414bf3f42377faa3f59eb2978a23def464a4c31406f59364d8d5dbc6f7bd6', 'test_pin41_original_input_only.py': '864979f469f62ab2c16071bb6038f768ad743592bf60e5a6ec48854f98931ee2', 'run_pin41.py': '582ad08c31feac90fc0da60218218bdbb89849345b32913a9f50c18edee83df9'}

PUBLIC_SCROLL_SAMPLES = 'ios-public-scroll-samples.yml'
PUBLIC_SCROLL_SAMPLES_SHA256 = '84d422a8313255f65821eb0284555179af2b2f351983704b2095625820b8ea28'
PUBLIC_SCROLL_SAMPLES_COMPANIONS = {'pin38_public_scroll_samples.py': '66065bb6efb6942547e9d7f223b5424706933d60bba338066b62b7703b148527', 'test_pin38_public_scroll_samples.py': 'f4b72eea87332e23b980fd5486d3cea6b281be7f4cb72b67258eaf670b1c6bd6', 'run_pin38.py': '07e6091e1a3288bf3454aeea29d5864e4d9302bb72e2206eae701de4253043c4'}

PUBLIC_SCROLL_ORDER_CORRECTION = 'ios-public-scroll-order-correction.yml'
PUBLIC_SCROLL_ORDER_CORRECTION_SHA256 = 'f7a5adbf4e3ff125c1d6e216fc750384730c67c8f7e620ff70ef71880922716a'
PUBLIC_SCROLL_ORDER_CORRECTION_COMPANIONS = {'read_pin37_order_correction.py': '23b51140a4d20c517493525953ecdb088e0179cbfd5ff63ef44faf2c13217ffe', 'test_pin37_order_correction.py': '2485e11ee6550147fe2f0f7d54957f6ff96a89e82711b73de4c1ade6734180a9'}

PUBLIC_SCROLL_NATURAL = 'ios-public-scroll-natural.yml'
PUBLIC_SCROLL_NATURAL_SHA256 = '725f5f4b749acea459357665178d72c4856e74976fe7c05c0f12f5c92e95aeb5'
PUBLIC_SCROLL_NATURAL_COMPANIONS = {'pin37_public_scroll_natural.py': '4afa00f3618e6d214c62a586d213e9360bf69bdc8b2519ff9c6eaf2973cbf821', 'test_pin37_public_scroll_natural.py': '365f39fa9293c336a6b8b22a94689a4989e8beb8259bb402b0386da1ff4ef75e', 'run_pin37.py': 'a5d3002c15d22bbb93b2aa7fe7a643688e5c1cd49bb0905966c6afbe0f42ef10'}

PUBLIC_SCROLL_EXPORT = 'ios-public-scroll-export.yml'
PUBLIC_SCROLL_EXPORT_SHA256 = 'b45efb79aae661aaadedf43c4075aba0c038584868c34aba2d12cadadec2aa8a'
PUBLIC_SCROLL_EXPORT_COMPANIONS = {'pin36_public_scroll_boundary.py': '990121c236b133a621783b6fe006e640af62ef3aa96b1a34cfa476610bea3cb4', 'test_pin36_public_scroll_boundary.py': 'eea185caafeaa506a278077a92327ec761be54730a9e68e905aafbbf05754230', 'run_pin36.py': '750d599ad1dd0f11b8da7c157550adbba2d87934beea75b317545d5f94e1d272', 'PIN36PublicScroll.kt.in': 'c186541ee658acd6f24c424b4a334723759734dfd840905d9078a04e62f7b0a9', 'PIN36PublicScroll.swift.in': 'df40b9d44aa1004f68b15efe13a15926d93304e206d2d060db45e323a66caf38', 'PIN36PublicScrollControlTest.kt.in': 'aa6c2f902dec0646ff3d74fbee409f8db3cb920a107006787d9377f4918295eb', 'PIN36PublicScrollAdmissionTest.kt.in': '23e07b6b22414356dadb2da0884fe85546e1fd4cdf5c9ce34fbbb4b38e82eaa0', 'fixtures/PIN36PermissionScreen.kt.source': 'a6f6a21eccb2a7c7798ea84c0aba8d84c7bb825f84e77001992a03fdc4633552'}

PUBLIC_FAILURE_COVERAGE_CONTROL = 'ios-public-failure-coverage-control.yml'
PUBLIC_FAILURE_COVERAGE_CONTROL_SHA256 = 'b5a1e83f46e691d6967f45eb27ae13ac24cee19bd6184178db5974bb64251ab4'
PUBLIC_FAILURE_COVERAGE_CONTROL_COMPANIONS = {'pin11_public_failure_coverage.py': '5f64cd0d1379ea6d907f301239c730b3fb0131db58ba4019695eecdeb6ab881f', 'test_pin11_public_failure_coverage.py': 'e9754b8d297eb8aeab636912168f7efebe4222316155aa546146fa3f2c3aee61'}

ALLOWED_ACTIONS = {
    "actions/checkout",
    "actions/setup-java",
    "actions/upload-artifact",
    "actions/attest-build-provenance",
    "actions/cache/restore",
    "actions/cache/save",
}
DISABLED_STORE_JOB_CONDITION = "if: ${{ always() && false }}"
FULL_VERIFICATION_SCOPE = "github.event_name != 'workflow_dispatch' || inputs.verification_scope == 'full'"
FULL_VERIFICATION_FINALIZER = "always() && (" + FULL_VERIFICATION_SCOPE + ")"
WINDOWS_VERIFICATION_SCOPE = FULL_VERIFICATION_SCOPE + " || inputs.verification_scope == 'windows-only'"
LINUX_VERIFICATION_SCOPE = (FULL_VERIFICATION_SCOPE + " || inputs.verification_scope == 'linux-process-probe'"
                            " || inputs.verification_scope == 'android-cleanup-only'")
LINUX_PROBE_SCOPE = "github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'linux-process-probe'"
ANDROID_CLEANUP_SCOPE = "github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'android-cleanup-only'"
ANDROID_RUNTIME_SCOPE = FULL_VERIFICATION_SCOPE + " || inputs.verification_scope == 'android-cleanup-only'"
ANDROID_RUNTIME_FINALIZER = "always() && (" + ANDROID_RUNTIME_SCOPE + ")"
LINUX_PREREQUISITE_SCOPE = ("github.event_name == 'workflow_dispatch' && "
                            "(inputs.verification_scope == 'linux-process-probe' || inputs.verification_scope == 'android-cleanup-only')")
IOS_VERIFICATION_SCOPE = ("github.event_name != 'workflow_dispatch' || "
                          "(inputs.verification_scope != 'windows-only' && inputs.verification_scope != 'linux-process-probe'"
                          " && inputs.verification_scope != 'android-cleanup-only'"
                          " && inputs.verification_scope != 'ios-protection-probe'"
                          " && inputs.verification_scope != 'darwin-host-capability')")
NATIVE_VERIFICATION_SCOPE = ("github.event_name == 'workflow_dispatch' && "
                             "(inputs.verification_scope == 'native-preflight' || inputs.verification_scope == 'native-evidence')")
PROCESS_PROBE_SCOPE = "github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'native-process-probe'"
PROTECTION_PROBE_SCOPE = "github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'ios-protection-probe'"
VERIFICATION_JOBS = {
    "desktop-android": "Common, desktop, and Android release",
    "desktop-linux-arm64": "Desktop strict verification (Linux arm64)",
    "desktop-macos-x64": "Desktop and Kotlin Native strict verification (macOS x64)",
    "desktop-windows-x64": "Desktop, Kotlin Native, and Android resources strict verification (Windows x64)",
    "ios": "iOS simulator runtime and Swift host",
    "ios-release-simulator": "iOS Release simulator and Swift wrapper",
    "ios-release-device": "iOS Release device arm64",
    "ios-release-x64": "iOS Release simulator x64",
    "ios-release": "iOS release frameworks and Swift wrapper",
    "ios-protection-probe": "iOS strict protection diagnostic",
    "darwin-host-capability": "Darwin native host capability (${{ matrix.architecture }})",
}


def fail(message: str) -> None:
    raise RuntimeError(message)


def load_files() -> dict[str, str]:
    actual = {path.name for path in WORKFLOWS.glob("*.yml")} | {path.name for path in WORKFLOWS.glob("*.yaml")}
    missing = EXPECTED - actual
    if missing:
        fail(f"Missing required release workflows: {', '.join(sorted(missing))}")
    unexpected = actual - EXPECTED - {PUBLIC_TEXT_REVIEW, PUBLIC_BACK_REVIEW, PUBLIC_CATALOG_REVIEW,
                                     SETTLED_FAILURE_REVIEW, PUBLIC_SETUP_PAIR_REVIEW, PUBLIC_SETUP_BOUNDARIES, SYNTHETIC_FAILURE_EXPORT, CURRENT_INPUT_BOUNDARIES, NATIVE_ACCESSIBILITY_CAPABILITY, NATIVE_BOUNDARY_OBSERVATIONS, NATIVE_ROOT_CAPABILITY, NATIVE_ROOT_OBSERVATIONS, PUBLIC_FAILURE_COVERAGE_CONTROL, PUBLIC_SESSION_STRESS, CATALOG_INPUT_BOUNDARY, OVERSCROLL_GETTER_BOUNDARY, ORIGINAL_TOUCH_DELIVERY, ORIGINAL_TOUCH_EXPORT_CONTROL, ORIGINAL_TOUCH_COHORT, PUBLIC_SCROLL_EXPORT, PUBLIC_SCROLL_NATURAL, PUBLIC_SCROLL_ORDER_CORRECTION, PUBLIC_SCROLL_SAMPLES, NATIVE_ONLY_EXPORT, NATIVE_ONLY_SAMPLES, ORIGINAL_INPUT_ONLY, OWNED_LEAF_EXPORT, OWNED_LEAF_PAIR}
    if unexpected:
        fail(f"Unreviewed workflow files are forbidden: {', '.join(sorted(unexpected))}")
    return {name: (WORKFLOWS / name).read_bytes().decode("utf-8") for name in sorted(actual)}


def verify_action_pins(name: str, text: str) -> None:
    for line_number, line in enumerate(text.splitlines(), 1):
        match = re.search(r"\buses:\s*([^\s#]+)", line)
        if not match:
            continue
        reference = match.group(1).strip("'\"")
        if reference.startswith("./"):
            continue
        if "@" not in reference:
            fail(f"{name}:{line_number}: Action has no immutable reference")
        action, revision = reference.rsplit("@", 1)
        if not action or not FULL_SHA.fullmatch(revision):
            fail(f"{name}:{line_number}: third-party Action is not pinned to a full commit SHA")
        if action not in ALLOWED_ACTIONS:
            fail(f"{name}:{line_number}: Action {action!r} is outside the reviewed allowlist")


def verify_common(name: str, text: str) -> None:
    verify_action_pins(name, text)
    forbidden = ("pull_request_target:", "continue-on-error: true", "persist-credentials: true")
    for token in forbidden:
        if token in text:
            fail(f"{name}: forbidden workflow construct: {token}")
    if re.search(r"echo[^\n]*\$\{\{\s*secrets\.", text, re.IGNORECASE):
        fail(f"{name}: possible direct secret echo")
    if "timeout-minutes:" not in text:
        fail(f"{name}: every workflow must bound job duration")
    if "permissions:" not in text or "contents: read" not in text:
        fail(f"{name}: missing least-privilege baseline permissions")


def verify_input_investigation(text: str) -> None:
    verify_common("ios-input-investigation.yml", text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != INPUT_INVESTIGATION_SHA256:
        fail("Input investigation must retain its exact reviewed read-only, free-runner, non-production workflow")


def verify_public_text_review(text: str) -> None:
    verify_common(PUBLIC_TEXT_REVIEW, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_TEXT_REVIEW_SHA256:
        fail("Public text review must retain its exact reviewed read-only, free-runner, non-production workflow")
    for name, digest in PUBLIC_TEXT_REVIEW_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public text review companion must retain its exact reviewed immutable intake/export controls: {name}")


def verify_public_back_review(text: str) -> None:
    verify_common(PUBLIC_BACK_REVIEW, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_BACK_REVIEW_SHA256:
        fail("Public Back review must retain its exact reviewed read-only, free-runner, non-production workflow")
    for name, digest in PUBLIC_BACK_REVIEW_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public Back review companion must retain its exact reviewed immutable intake/export controls: {name}")


def verify_settled_failure_review(text: str) -> None:
    verify_common(SETTLED_FAILURE_REVIEW, text)
    if hashlib.sha256(text.encode()).hexdigest() != SETTLED_FAILURE_REVIEW_SHA256:
        fail("PIN19 retained-failure workflow differs from exact free readonly review")
    for name, digest in SETTLED_FAILURE_REVIEW_COMPANIONS.items():
        path = ROOT / "scripts" / "verification" / "ios-input-boundaries" / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail("PIN19 retained-failure reader differs from reviewed companion")


def verify_public_catalog_review(text: str) -> None:
    verify_common(PUBLIC_CATALOG_REVIEW, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_CATALOG_REVIEW_SHA256:
        fail("Public catalog review must retain its exact reviewed read-only, free-runner, non-production workflow")
    for name, digest in PUBLIC_CATALOG_REVIEW_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public catalog review companion must retain its exact reviewed immutable intake/export controls: {name}")


def verify_public_setup_pair_review(text: str) -> None:
    verify_common(PUBLIC_SETUP_PAIR_REVIEW, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SETUP_PAIR_REVIEW_SHA256:
        fail("Public setup-pair review must retain its exact reviewed read-only, free-runner, non-production workflow")
    for name, digest in PUBLIC_SETUP_PAIR_REVIEW_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public setup-pair review companion must retain its exact reviewed immutable intake/export controls: {name}")


def verify_public_setup_boundaries(text: str) -> None:
    verify_common(PUBLIC_SETUP_BOUNDARIES, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SETUP_BOUNDARIES_SHA256:
        fail("Public setup boundaries must retain the exact frozen-source, free-runner, copy-only workflow")
    for name, digest in PUBLIC_SETUP_BOUNDARIES_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public setup boundaries companion differs from reviewed source-copy controls: {name}")


def verify_synthetic_failure_export(text: str) -> None:
    verify_common(SYNTHETIC_FAILURE_EXPORT, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != SYNTHETIC_FAILURE_EXPORT_SHA256:
        fail("Synthetic export must retain the exact single-control, free-runner, copy-only workflow")
    for name, digest in SYNTHETIC_FAILURE_EXPORT_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Synthetic export companion differs from reviewed failed-only controls: {name}")


def verify_current_input_boundaries(text: str) -> None:
    verify_common(CURRENT_INPUT_BOUNDARIES, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != CURRENT_INPUT_BOUNDARIES_SHA256:
        fail("Current boundaries must retain the exact failed-source, free-runner, observed-only workflow")
    for name, digest in CURRENT_INPUT_BOUNDARIES_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Current boundaries companion differs from reviewed failed-only controls: {name}")


def verify_native_accessibility_capability(text: str) -> None:
    verify_common(NATIVE_ACCESSIBILITY_CAPABILITY, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != NATIVE_ACCESSIBILITY_CAPABILITY_SHA256:
        fail("Native accessibility capability must keep its exact copied control and free runner")
    for name, digest in NATIVE_ACCESSIBILITY_CAPABILITY_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Native accessibility companion differs from reviewed source: {name}")


def verify_native_boundary_observations(text: str) -> None:
    verify_common(NATIVE_BOUNDARY_OBSERVATIONS, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != NATIVE_BOUNDARY_OBSERVATIONS_SHA256:
        fail("Native boundary observation must keep its exact copied control and free runner")
    for name, digest in NATIVE_BOUNDARY_OBSERVATIONS_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Native boundary companion differs from reviewed source: {name}")


def verify_native_root_capability(text: str) -> None:
    verify_common(NATIVE_ROOT_CAPABILITY, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != NATIVE_ROOT_CAPABILITY_SHA256:
        fail("Native root diagnostic must keep its exact copied source and free runner")
    for name, digest in NATIVE_ROOT_CAPABILITY_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Native root companion differs from reviewed source: {name}")


def verify_native_root_observations(text: str) -> None:
    verify_common(NATIVE_ROOT_OBSERVATIONS, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != NATIVE_ROOT_OBSERVATIONS_SHA256:
        fail("Native root diagnostic must keep its exact copied source and free runner")
    for name, digest in NATIVE_ROOT_OBSERVATIONS_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Native root companion differs from reviewed source: {name}")


def verify_public_session_stress(text: str) -> None:
    verify_common(PUBLIC_SESSION_STRESS, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SESSION_STRESS_SHA256:
        fail("Stress must retain exact fixed samples, two free runners, all failures and read-only custody")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public-session stress companion differs from reviewed source: {name}")


def verify_catalog_input_boundary(text: str) -> None:
    verify_common(CATALOG_INPUT_BOUNDARY, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != CATALOG_INPUT_BOUNDARY_SHA256:
        fail("Catalog boundary must keep fixed samples, one free runner and every failed outcome")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS | CATALOG_INPUT_BOUNDARY_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Catalog input companion differs from reviewed source: {name}")



def verify_overscroll_getter_boundary(text: str) -> None:
    verify_common(OVERSCROLL_GETTER_BOUNDARY, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != OVERSCROLL_GETTER_BOUNDARY_SHA256:
        fail("Overscroll getter boundary must preserve the original effect, fixed samples and free runner")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS |
                         CATALOG_INPUT_BOUNDARY_COMPANIONS | OVERSCROLL_GETTER_BOUNDARY_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Overscroll getter companion differs from reviewed source: {name}")


def verify_original_touch_delivery(text: str) -> None:
    verify_common(ORIGINAL_TOUCH_DELIVERY, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != ORIGINAL_TOUCH_DELIVERY_SHA256:
        fail("Original touch observation must preserve original input, fixed samples and free runner")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS |
                         CATALOG_INPUT_BOUNDARY_COMPANIONS | OVERSCROLL_GETTER_BOUNDARY_COMPANIONS |
                         ORIGINAL_TOUCH_DELIVERY_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Original touch companion differs from reviewed source: {name}")


def verify_original_touch_export_control(text: str) -> None:
    verify_common(ORIGINAL_TOUCH_EXPORT_CONTROL, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != ORIGINAL_TOUCH_EXPORT_CONTROL_SHA256:
        fail("Isolated touch export must keep the existing sentinel only on a free runner")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS |
                         CATALOG_INPUT_BOUNDARY_COMPANIONS | OVERSCROLL_GETTER_BOUNDARY_COMPANIONS |
                         ORIGINAL_TOUCH_DELIVERY_COMPANIONS | ORIGINAL_TOUCH_EXPORT_CONTROL_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Isolated touch-export companion differs from reviewed source: {name}")


def verify_owned_leaf_pair(text: str) -> None:
    verify_common(OWNED_LEAF_PAIR, text)
    verify_owned_leaf_export((WORKFLOWS / OWNED_LEAF_EXPORT).read_text())
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != OWNED_LEAF_PAIR_SHA256:
        fail("Owned-leaf pair must retain ordered original/observed arms, all failures and one standard free runner")
    for name, digest in OWNED_LEAF_PAIR_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Owned-leaf pair companion differs from reviewed source: {name}")


def verify_owned_leaf_export(text: str) -> None:
    verify_common(OWNED_LEAF_EXPORT, text)
    # Required shared runner/privacy/ownership controls remain bound by existing contracts.
    verify_original_input_only((WORKFLOWS / ORIGINAL_INPUT_ONLY).read_text())
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != OWNED_LEAF_EXPORT_SHA256:
        fail("Owned-leaf export must retain one isolated unexpected failure and a standard free runner")
    for name, digest in OWNED_LEAF_EXPORT_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Owned-leaf companion differs from reviewed source: {name}")


def verify_original_input_only(text: str) -> None:
    verify_common(ORIGINAL_INPUT_ONLY, text)
    verify_native_only_samples((WORKFLOWS / NATIVE_ONLY_SAMPLES).read_text())
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != ORIGINAL_INPUT_ONLY_SHA256:
        fail("Original input-only samples must retain unchanged queries/actions/deadlines, failure-only custody and one free runner")
    for name, digest in ORIGINAL_INPUT_ONLY_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Original input-only companion differs from reviewed source: {name}")


def verify_native_only_samples(text: str) -> None:
    verify_common(NATIVE_ONLY_SAMPLES, text)
    verify_native_only_export((WORKFLOWS / NATIVE_ONLY_EXPORT).read_text())
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != NATIVE_ONLY_SAMPLES_SHA256:
        fail("Native-only samples require original finite AR/EN order, failure-only custody and one free runner")
    for name, digest in NATIVE_ONLY_SAMPLES_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Native-only sample companion differs from reviewed source: {name}")


def verify_native_only_export(text: str) -> None:
    verify_common(NATIVE_ONLY_EXPORT, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != NATIVE_ONLY_EXPORT_SHA256:
        fail("Native-only export must retain one isolated sentinel on a standard free runner")
    for name, digest in NATIVE_ONLY_EXPORT_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Native-only export companion differs from reviewed source: {name}")


def verify_public_scroll_samples(text: str) -> None:
    verify_common(PUBLIC_SCROLL_SAMPLES, text)
    verify_public_scroll_natural((WORKFLOWS / PUBLIC_SCROLL_NATURAL).read_text())
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SCROLL_SAMPLES_SHA256:
        fail("Public scroll samples must retain exact failed sample context, all custody and one free runner")
    for name, digest in PUBLIC_SCROLL_SAMPLES_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Fixed public scroll sample companion differs from reviewed source: {name}")


def verify_public_scroll_order_correction(text: str) -> None:
    verify_common(PUBLIC_SCROLL_ORDER_CORRECTION, text)
    verify_public_scroll_natural((WORKFLOWS / PUBLIC_SCROLL_NATURAL).read_text())
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SCROLL_ORDER_CORRECTION_SHA256:
        fail("Retained order correction must preserve exact evidence, free read-only runner and no native replay")
    for name, digest in PUBLIC_SCROLL_ORDER_CORRECTION_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Retained order correction companion differs from reviewed source: {name}")


def verify_public_scroll_natural(text: str) -> None:
    verify_common(PUBLIC_SCROLL_NATURAL, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SCROLL_NATURAL_SHA256:
        fail("Original public scroll must preserve original EN/AR once, failure-only custody and free runner")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS |
                         CATALOG_INPUT_BOUNDARY_COMPANIONS | OVERSCROLL_GETTER_BOUNDARY_COMPANIONS |
                         ORIGINAL_TOUCH_DELIVERY_COMPANIONS | ORIGINAL_TOUCH_EXPORT_CONTROL_COMPANIONS |
                         PUBLIC_SCROLL_EXPORT_COMPANIONS | PUBLIC_SCROLL_NATURAL_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Original public scroll companion differs from reviewed source: {name}")


def verify_public_scroll_export(text: str) -> None:
    verify_common(PUBLIC_SCROLL_EXPORT, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_SCROLL_EXPORT_SHA256:
        fail("Public scroll export must keep one existing isolated sentinel, failure-only custody and free runner")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS |
                         CATALOG_INPUT_BOUNDARY_COMPANIONS | OVERSCROLL_GETTER_BOUNDARY_COMPANIONS |
                         ORIGINAL_TOUCH_DELIVERY_COMPANIONS | ORIGINAL_TOUCH_EXPORT_CONTROL_COMPANIONS |
                         PUBLIC_SCROLL_EXPORT_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public scroll export companion differs from reviewed source: {name}")


def verify_original_touch_cohort(text: str) -> None:
    verify_common(ORIGINAL_TOUCH_COHORT, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != ORIGINAL_TOUCH_COHORT_SHA256:
        fail("Original touch cohort must keep original23 context and one free runner")
    for name, digest in (NATIVE_ROOT_OBSERVATIONS_COMPANIONS | PUBLIC_SESSION_STRESS_COMPANIONS |
                         CATALOG_INPUT_BOUNDARY_COMPANIONS | OVERSCROLL_GETTER_BOUNDARY_COMPANIONS |
                         ORIGINAL_TOUCH_DELIVERY_COMPANIONS | ORIGINAL_TOUCH_COHORT_COMPANIONS).items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Original touch-cohort companion differs from reviewed source: {name}")


def verify_public_failure_coverage_control(text: str) -> None:
    verify_common(PUBLIC_FAILURE_COVERAGE_CONTROL, text)
    if hashlib.sha256(text.encode("utf-8")).hexdigest() != PUBLIC_FAILURE_COVERAGE_CONTROL_SHA256:
        fail("Public failure coverage must keep its exact free, read-only native control")
    for name, digest in PUBLIC_FAILURE_COVERAGE_CONTROL_COMPANIONS.items():
        path = ROOT / "scripts/verification/ios-input-boundaries" / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            fail(f"Public failure coverage companion differs from reviewed source: {name}")


def verify_android_runtime_smoke(name: str, text: str) -> None:
    required = (
        "scripts/android/run_release_managed_device_smoke.sh",
        "system-images;android-35;google_apis;x86_64",
        "test -c /dev/kvm",
        "sudo chmod 0666 /dev/kvm",
        ".toolchains.android_managed_device.system_image_revision",
        "**/build/reports/androidTests/managedDevice/",
        "**/build/outputs/androidTest-results/managedDevice/",
    )
    for token in required:
        if token not in text:
            fail(f"{name}: release managed-device smoke gate is missing {token!r}")


def validation_step(text: str, name: str) -> str:
    """Read one named step in the checked-in, statically reviewed workflow shape."""
    marker = f"\n      - name: {name}\n"
    if text.count(marker) != 1:
        fail(f"validation workflow requires exactly one step {name!r}")
    return text.split(marker, 1)[1].split("\n      - ", 1)[0]


def validation_job(text: str, name: str) -> str:
    """Keep checks inside one job even when lifecycle step names are shared."""
    marker = f"\n  {name}:\n"
    if text.count(marker) != 1:
        fail(f"validation workflow requires exactly one job {name!r}")
    return re.split(r"(?m)^  [A-Za-z_][A-Za-z0-9_-]*:\n", text.split(marker, 1)[1], maxsplit=1)[0]


def current_verification_artifact_paths(job: str) -> list[str]:
    """Keep all current/dynamically added module reports and external custody."""
    prefix = "${{ runner.temp }}/parlor-verification-${{ github.run_id }}-${{ github.run_attempt }}-${{ github.job }}"
    reports = ["**/build/reports/tests/", "**/build/test-results/**/*.xml"]
    custody = [prefix + "-ownership.json", prefix + "-stop-*.json"]
    if job == "desktop-android":
        paths = ["build/ci-evidence/", "**/build/reports/detekt/"] + reports + custody + [
            "**/build/reports/androidTests/managedDevice/",
            "**/build/outputs/androidTest-results/managedDevice/",
            "**/build/reports/lint-results-*",
            "composeApp/build/outputs/mapping/release/",
            "composeApp/build/outputs/bundle/release/*.aab",
        ]
    elif job in {"desktop-linux-arm64", "desktop-macos-x64", "desktop-windows-x64"}:
        paths = reports + custody
    elif job == "ios":
        paths = ["build/ci-evidence/"] + reports + custody + [
            prefix + "-apple-*-ownership.json", prefix + "-apple-ui-simulator-*.json"]
    elif job == "ios-release-simulator":
        paths = ["build/ci-evidence/",
                 "build/xcode-derived-data/Build/Products/Release-iphonesimulator/Parlor.app.dSYM/",
                 "**/build/reports/detekt/"] + custody + [prefix + "-apple-*-ownership.json"]
    elif job in {"ios-release-device", "ios-release-x64"}:
        paths = ["build/ci-evidence/", "**/build/reports/detekt/"] + custody + [prefix + "-apple-*-ownership.json"]
    else:
        fail("Unknown current verification artifact job")
    # These trees remain immutable Git evidence, not outputs of this invocation.
    return paths + ["!remediation-runs/**", "!docs/archives/**"]


def verify_current_verification_artifacts(text: str) -> None:
    uploads = {
        "desktop-android": ("Upload verification reports, mappings, and unsigned AAB", "desktop-android-verification"),
        "desktop-linux-arm64": ("Upload Desktop verification descriptors", "desktop-verification-${{ github.job }}"),
        "desktop-macos-x64": ("Upload Desktop verification descriptors", "desktop-verification-${{ github.job }}"),
        "desktop-windows-x64": ("Upload Desktop verification descriptors", "desktop-verification-${{ github.job }}"),
        "ios": ("Upload Apple runtime verification evidence", "ios-runtime-verification"),
        "ios-release-simulator": ("Upload Apple release verification evidence", "ios-release-verification"),
        "ios-release-device": ("Upload IosArm64 release verification evidence", "ios-release-device-verification"),
        "ios-release-x64": ("Upload IosX64 release verification evidence", "ios-release-x64-verification"),
    }
    for job, (name, artifact_name) in uploads.items():
        block = validation_step(validation_job(text, job), name)
        condition = (ANDROID_RUNTIME_FINALIZER if job == "desktop-android" else
                     FULL_VERIFICATION_FINALIZER if job.startswith("ios") else "always()")
        if (re.findall(r"(?m)^        ([^\s:#][^\s:]*):(?: (.*))?$", block) != [
                ("id", "verification_artifact"), ("if", condition),
                ("uses", "actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2"), ("with", "")] or
                re.findall(r"(?m)^          ([^\s:#][^\s:]*):(?: (.*))?$", block) != [
                    ("name", artifact_name), ("if-no-files-found", "warn"), ("retention-days", "14"), ("path", "|")] or
                re.findall(r"(?m)^            (\S.*)$", block) != current_verification_artifact_paths(job)):
            fail("current verification artifact must retain exact current reports/custody and only historical exclusions")


def verify_owned_ios_app_launch(text: str) -> None:
    prepare_name = "Claim apple-ui native resource ownership"
    launch_name = "Launch Swift host and Compose root on iOS Simulator"
    finish_name = "Stop Gradle after apple-ui and retire owned Apple resources"
    prepare = validation_step(text, prepare_name)
    launch = validation_step(text, launch_name)
    finish = validation_step(text, finish_name)
    helper = "/usr/bin/python3 -B -m scripts.ci.apple_verification_hygiene"
    for block, required in (
        (prepare, ("        id: apple_ui_prepare\n", f"        run: {helper} prepare apple-ui\n")),
        (launch, (
            "        id: apple_ui_run\n",
            '          simulator_udid="$(' + helper + ' create-simulator apple-ui)"\n',
            "          test ! -e build/ci-evidence/ios-ui-tests.xcresult\n",
            "-parallel-testing-enabled NO",
            "-maximum-concurrent-test-simulator-destinations 1",
            "-derivedDataPath build/xcode-derived-data",
        )),
        (finish, (
            "        id: apple_ui_finish\n",
            "          PARLOR_APPLE_PREPARE_OUTCOME: ${{ steps.apple_ui_prepare.outcome }}\n",
            "          PARLOR_APPLE_RUN_OUTCOME: ${{ steps.apple_ui_run.outcome }}\n",
            f"        run: {helper} finish apple-ui\n",
        )),
    ):
        for token in required:
            if token not in block:
                fail(f"validation workflow owned iOS app-launch lifecycle lacks {token!r}")
    if re.findall(r"(?m)^        if: (.*)$", finish) != [FULL_VERIFICATION_FINALIZER]:
        fail("validation workflow owned iOS app-launch finalizer must always run in full verification")
    if text.index(f"- name: {prepare_name}\n") >= text.index(f"- name: {launch_name}\n"):
        fail("validation workflow must claim iOS app-launch ownership before launch")
    next_step = text.split(f"\n      - name: {launch_name}\n", 1)[1].split("\n      - ", 1)[1]
    if not next_step.startswith(f"name: {finish_name}\n"):
        fail("validation workflow must finalize owned iOS resources immediately after app-launch")


def verify_windows_distribution_ownership(text: str) -> None:
    name = "Verify Windows distribution process ownership"
    step = validation_step(text, name)
    expected = '        timeout-minutes: 2\n        shell: python\n        run: |\n          import os, sys, unittest\n          sys.path.insert(0, os.getcwd())\n          unittest.main(module="scripts.release.tests.test_distribution_process_ownership", argv=["ownership-tests"], verbosity=2)\n'
    if step.strip("\n") != expected.strip("\n"):
        fail("Windows distribution process ownership must run its exact mandatory native tests")
    if not (text.index("- name: Require the Windows x64 host\n") < text.index(f"- name: {name}\n") <
            text.index("- name: Resolve and test the Windows x64 host-selected graphs\n")):
        fail("Windows distribution process ownership must execute on the verified host before build tools")


def verify_windows_checkout(text: str) -> None:
    """Preserve complete evidence and fail closed before a scoped Windows checkout."""
    header = text.split("\n    steps:\n", 1)[0]
    if (re.findall(r"(?m)^      (GIT_CONFIG_[A-Z0-9_]+): (.*)$", header) != [
            ("GIT_CONFIG_COUNT", '"1"'), ("GIT_CONFIG_KEY_0", "core.longpaths"),
            ("GIT_CONFIG_VALUE_0", '"true"')] or text.count("GIT_CONFIG_") != 3):
        fail("Windows checkout requires only job-local long-path configuration")
    name = "Validate Windows checkout prerequisites"
    gate = validation_step(text, name)
    expected_gate = '''        shell: pwsh
        env:
          PARLOR_WINDOWS_SCOPE: ${{ inputs.verification_scope }}
          PARLOR_WINDOWS_ONLY: ${{ github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'windows-only' }}
          PARLOR_WINDOWS_FROZEN_SOURCE_SHA: ${{ inputs.frozen_source_sha }}
          PARLOR_WINDOWS_NATIVE_SELECTION: ${{ inputs.native_selection }}
        run: |
          $longPaths = git config --bool --get core.longpaths
          if ($LASTEXITCODE -ne 0 -or $longPaths -cne "true") { throw "Expected job-local Git long paths" }
          Write-Output "core.longpaths=$longPaths (job-local)"
          if ($env:PARLOR_WINDOWS_ONLY -eq "true") {
            if ($env:PARLOR_WINDOWS_SCOPE -cne "windows-only") { throw "Windows-only dispatch requires its exact explicit scope" }
            $source = $env:PARLOR_WINDOWS_FROZEN_SOURCE_SHA
            if ($source -cnotmatch '^[0-9a-f]{40}$' -or $source -cne $env:GITHUB_SHA -or $source -cne $env:GITHUB_WORKFLOW_SHA) {
              throw "Windows-only dispatch requires the reviewed frozen source"
            }
            if ($env:PARLOR_WINDOWS_NATIVE_SELECTION -cnotin @("", "paired")) { throw "Windows-only dispatch cannot select native evidence" }
          }
'''
    if (gate.strip("\n") != expected_gate.strip("\n") or
            re.findall(r"(?m)^      - name: (.*)$", text)[:2] != [name, "Check out source"]):
        fail("Windows checkout requires exact long-path and frozen-source validation before checkout")
    checkout = validation_step(text, "Check out source")
    if re.findall(r"(?m)^          ([a-z-]+): (.*)$", checkout) != [
            ("fetch-depth", "1"), ("persist-credentials", "false")]:
        fail("Windows checkout must retain its complete, non-sparse, credential-free source checkout")
    source = validation_step(text, "Require the Windows x64 host")
    expected_source = '''        shell: pwsh
        run: |
          if (-not $IsWindows) { throw "Expected a Windows runner" }
          if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne [System.Runtime.InteropServices.Architecture]::X64) {
            throw "Expected an x64 runner"
          }
          $head = git rev-parse HEAD
          if ($LASTEXITCODE -ne 0 -or $head -cne $env:GITHUB_SHA) { throw "Checkout differs from the triggering source" }
          Write-Output $head
          $status = git status --porcelain
          if ($LASTEXITCODE -ne 0 -or $status) { throw "Checkout is not clean" }
'''
    if source.strip("\n") != expected_source.strip("\n"):
        fail("Windows checkout requires checked Git success, exact triggering HEAD, clean source and x64 host")


def verify_linux_process_probe(job: str) -> None:
    """Closed probe/managed-cleanup paths; full qualification keeps its complete gates."""
    full = ("Install pinned Android SDK packages", "Record exact source and require a clean checkout",
            "Run common, desktop, Android, static-analysis, and release gates",
            "Run release Android managed-device smoke tests", "Inspect unsigned Android release artifact and merged manifest")
    final = ("Stop Gradle after common-android", "Stop Gradle after managed-device",
             "Upload verification reports, mappings, and unsigned AAB", "Clean only attested verification outputs",
             "Upload verification cleanup receipt")
    probe = ("Validate Linux process-probe source and controls", "Observe Linux process access without a build",
             "Upload bounded Linux process-probe evidence", "Finalize Linux process-probe resources and uploaded custody",
             "Upload Linux process-probe cleanup receipt")
    admission = "Validate Android cleanup source and controls"
    prerequisite = "Install Linux process-probe emulator package"
    expected = ["Check out source", "Set up JDK 21", "Restore reviewed Gradle dependencies", probe[0], admission, prerequisite, "Claim fresh verification output ownership",
                full[0], full[1], full[2], final[0], full[3], final[1], full[4], *final[2:], *probe[1:]]
    if (re.findall(r"(?m)^      - name: (.*)$", job) != expected + ["Save dependencies from trusted main only"] or
            re.findall(r"(?m)^    runs-on: (.*)$", job) != ["ubuntu-24.04"] or
            re.findall(r"(?m)^    timeout-minutes: (.*)$", job) != [
                "${{ inputs.verification_scope == 'linux-process-probe' && 10 || inputs.verification_scope == 'android-cleanup-only' && 45 || 90 }}"]):
        fail("verification scope Linux requires closed Ubuntu24.04 probe10, managed-cleanup45 and full90 minute paths")
    for names, required in (
        ((full[2], full[4]), FULL_VERIFICATION_SCOPE), ((final[0],), FULL_VERIFICATION_FINALIZER),
        ((full[0], full[1], full[3]), ANDROID_RUNTIME_SCOPE), (final[1:], ANDROID_RUNTIME_FINALIZER),
        (probe[:2], LINUX_PROBE_SCOPE), (probe[2:], "always() && (" + LINUX_PROBE_SCOPE + ")"),
        ((admission,), ANDROID_CLEANUP_SCOPE), ((prerequisite,), LINUX_PREREQUISITE_SCOPE),
    ):
        for name in names:
            if re.findall(r"(?m)^        if: (.*)$", validation_step(job, name)) != [required]:
                fail("verification scope Linux must isolate the probe/managed cleanup and preserve full gates/finalizers")
    expected_smoke = ("        if: " + ANDROID_RUNTIME_SCOPE + "\n        shell: bash\n"
                      "        run: scripts/android/run_release_managed_device_smoke.sh")
    if validation_step(job, full[3]).strip("\n") != expected_smoke:
        fail("verification scope managed cleanup must run only the existing Android runtime script")
    expected_cleanup = '''        if: ''' + ANDROID_RUNTIME_FINALIZER + '''
        env:
          PARLOR_VERIFICATION_PREPARE_OUTCOME: ${{ steps.verification_ownership.outcome }}
          PARLOR_VERIFICATION_UPLOAD_OUTCOME: ${{ steps.verification_artifact.outcome }}
          PARLOR_VERIFICATION_ARTIFACT_ID: ${{ steps.verification_artifact.outputs.artifact-id }}
          PARLOR_VERIFICATION_ARTIFACT_DIGEST: ${{ steps.verification_artifact.outputs.artifact-digest }}
        shell: python
        run: |
          import runpy, sys
          sys.argv = ["verification_hygiene.py", "cleanup", "--android-reader=sudo-proc-exe-v2"]
          runpy.run_path("scripts/ci/verification_hygiene.py", run_name="__main__")
'''
    if validation_step(job, final[3]).strip("\n") != expected_cleanup.strip("\n"):
        fail("verification scope Linux runtime cleanup requires the explicit read-only reader and unchanged custody guards")
    expected_prerequisite = '''        if: ''' + LINUX_PREREQUISITE_SCOPE + '''
        timeout-minutes: 4
        shell: bash
        run: |
          set -euo pipefail
          /usr/bin/timeout --signal=TERM --kill-after=10s 180s \\
            "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_HOME}" "emulator"
'''
    if validation_step(job, prerequisite).strip("\n") != expected_prerequisite.strip("\n"):
        fail("verification scope Linux probe requires only the bounded emulator-package prerequisite in the admitted SDK")
    inputs = [("PARLOR_LINUX_PROBE_INPUTS", "${{ toJSON(inputs) }}")]
    prepare = [("PARLOR_VERIFICATION_PREPARE_OUTCOME", "${{ steps.verification_ownership.outcome }}")]
    upload = [("PARLOR_VERIFICATION_UPLOAD_OUTCOME", "${{ steps.linux_process_probe_artifact.outcome }}"),
              ("PARLOR_VERIFICATION_ARTIFACT_ID", "${{ steps.linux_process_probe_artifact.outputs.artifact-id }}"),
              ("PARLOR_VERIFICATION_ARTIFACT_DIGEST", "${{ steps.linux_process_probe_artifact.outputs.artifact-digest }}")]
    for name, mode, environment in ((probe[0], "validate", inputs), (admission, "validate-android-cleanup", inputs),
                                    (probe[1], "run", inputs + prepare),
                                    (probe[3], "cleanup", inputs + prepare + upload)):
        block = validation_step(job, name)
        if (re.findall(r"(?m)^        run: (.*)$", block) != ["/usr/bin/python3 -B scripts/ci/linux_process_probe.py " + mode] or
                re.findall(r"(?m)^          ([A-Z][A-Z0-9_]+): (.*)$", block) != environment or "github.token" in block):
            fail("verification scope Linux probe requires exact source/control/prepare/upload inputs without tokens")
    if (job.count("PARLOR_LINUX_PROBE_INPUTS:") != 4 or
            re.findall(r"(?m)^        id: (.*)$", validation_step(job, probe[1])) != ["linux_process_probe"] or
            re.findall(r"(?m)^        id: (.*)$", validation_step(job, probe[2])) != ["linux_process_probe_artifact"]):
        fail("verification scope Linux probe requires exact producer identities and no input overrides")
    prefix = "${{ runner.temp }}/parlor-verification-${{ github.run_id }}-${{ github.run_attempt }}-${{ github.job }}"
    for name, artifact, paths in ((probe[2], "linux-process-probe", [prefix + suffix for suffix in (
            "-linux-probe.json", "-ownership.json", "-stop-linux-probe.json")]),
            (probe[4], "linux-process-probe-cleanup", [prefix + "-cleanup.json"])):
        block = validation_step(job, name)
        fields = re.findall(r"(?m)^          (name|if-no-files-found|retention-days|path): (.*)$", block)
        expected_fields = [("name", artifact + "-${{ github.run_id }}-${{ github.run_attempt }}"),
                           ("if-no-files-found", "error"), ("retention-days", "14"),
                           ("path", "|" if len(paths) > 1 else paths[0])]
        if ("uses: actions/upload-artifact@" not in block or fields != expected_fields or
                len(paths) > 1 and [line.strip() for line in block.split("          path: |\n", 1)[-1].splitlines()
                                   if line.strip()] != paths):
            fail("verification scope Linux probe requires only exact compact evidence and cleanup paths")


def verify_verification_jdk(job: str) -> None:
    java = validation_step(job, "Set up JDK 21")
    if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", java) != [
            ("uses", "actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0"), ("with", "")] or
            re.findall(r"(?m)^          ([a-z-]+): (.*)$", java) != [
                ("distribution", "temurin"), ("java-version", '"21"'), ("check-latest", "false")]):
        fail("verification scope Apple jobs require pinned JDK 21 for builds and mandatory Gradle stops")


def verify_split_apple_scopes(text: str) -> None:
    """Two independent full jobs retain every gate and exactly their own cycles."""
    runtime_run = "Run all KMP iOS simulator runtime tests"
    release_run = "Run complete Apple static analysis"
    shared_start = ["Claim fresh verification output ownership", "Claim apple-aggregate native resource ownership",
                    "Record Xcode toolchain and exact source"]
    shared_end = ["Clean only attested verification outputs", "Upload verification cleanup receipt"]
    native_steps = ["Start focused native job clock", "Check out source", "Validate verification scope", "Set up JDK 21",
                    "Run focused native continuation", "Upload focused native evidence",
                    "Verify focused native cleanup and uploaded custody", "Upload native continuation cleanup receipt",
                    "Observe hosted native processes without an app build", "Upload bounded process-probe evidence",
                    "Verify process-probe cleanup and uploaded custody", "Upload process-probe cleanup receipt"]
    prefix = "${{ runner.temp }}/parlor-verification-${{ github.run_id }}-${{ github.run_attempt }}-${{ github.job }}"
    for job_id, cycle, run, upload, artifact_name in (
        ("ios", "apple-ui", runtime_run, "Upload Apple runtime verification evidence", "ios-runtime-verification"),
        ("ios-release-simulator", "apple-wrapper", release_run, "Upload Apple release verification evidence", "ios-release-verification"),
    ):
        job = validation_job(text, job_id)
        header = job.split("\n    steps:\n", 1)[0]
        if (re.findall(r"(?m)^    runs-on: (.*)$", header) != ["macos-15" if job_id == "ios" else FREE_APPLE_RELEASE_RUNNER] or
                re.search(r"(?m)^    (needs|strategy):", header) or
                re.findall(r"(?m)^      DEVELOPER_DIR: (.*)$", header) != ["/Applications/Xcode_26.3.app/Contents/Developer"]):
            fail("verification scope Apple jobs require independent pinned macOS runners, not a dependency or matrix")
        if job_id == "ios-release-simulator" and (
                re.findall(r"(?m)^    if: (.*)$", header) != [FULL_VERIFICATION_SCOPE] or
                re.findall(r"(?m)^    timeout-minutes: (.*)$", header) != ["180"] or "permissions:" in header):
            fail("verification scope Apple release job must be full-only, bounded and inherit read-only contents permission")
        verify_verification_jdk(job)
        checkout = validation_step(job, "Check out source")
        depth = ("${{ github.event_name == 'workflow_dispatch' && inputs.verification_scope != 'full' && '0' || '1' }}"
                 if job_id == "ios" else "1")
        if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", checkout) != [
                ("uses", "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1"), ("with", "")] or
                re.findall(r"(?m)^          ([a-z-]+): (.*)$", checkout) != [
                    ("fetch-depth", depth), ("persist-credentials", "false")]):
            fail("verification scope Apple jobs require independent credential-free triggering-source checkouts")
        finish = f"Stop Gradle after {cycle} and retire owned Apple resources"
        run_name = ("Launch Swift host and Compose root on iOS Simulator" if job_id == "ios" else
                    "Build unsigned Swift Release wrapper")
        full_steps = shared_start + [run, "Stop Gradle after apple-aggregate and retire owned Apple resources",
                                    "Validate iOS plist and privacy manifest", f"Claim {cycle} native resource ownership"]
        if job_id == "ios":
            full_steps += ["Verify effective Debug and Release application identities"]
        full_steps += [run_name, finish]
        if job_id == "ios-release-simulator":
            full_steps += ["Inspect complete unsigned iOS Release package"]
        full_steps += [upload] + shared_end
        if job_id == "ios-release-simulator":
            full_steps += ["Publish qualified Apple lane identity"]
        expected_steps = (native_steps if job_id == "ios" else ["Check out source", "Set up JDK 21"]) + full_steps
        expected_steps.insert(expected_steps.index("Set up JDK 21") + 1, "Restore reviewed Gradle dependencies")
        expected_steps += ["Save dependencies from trusted main only"]
        if re.findall(r"(?m)^      - name: (.*)$", job) != expected_steps:
            fail("verification scope split Apple jobs require all full gates and immediate finalizers in reviewed order")
        for name in full_steps:
            required = FULL_VERIFICATION_SCOPE
            if name.startswith("Stop Gradle after ") or name in [upload] + shared_end:
                required = FULL_VERIFICATION_FINALIZER
            elif name == "Publish qualified Apple lane identity":
                required = "success() && (" + FULL_VERIFICATION_SCOPE + ")"
            elif name == "Inspect complete unsigned iOS Release package":
                required = ("success() && steps.apple_wrapper_run.outcome == 'success' && "
                            "steps.apple_wrapper_finish.outcome == 'success' && (" + FULL_VERIFICATION_SCOPE + ")")
            if re.findall(r"(?m)^        if: (.*)$", validation_step(job, name)) != [required]:
                fail("verification scope must retain full Apple gates/finalizers and skip them only in focused modes")
        for current, current_run in (("apple-aggregate", run), (cycle, run_name)):
            stem = current.replace("-", "_")
            for suffix, name in (("prepare", f"Claim {current} native resource ownership"), ("run", current_run),
                                 ("finish", f"Stop Gradle after {current} and retire owned Apple resources")):
                block = validation_step(job, name)
                if re.findall(r"(?m)^        id: (.*)$", block) != [stem + "_" + suffix]:
                    fail("verification scope Apple cycles require exact producer identities")
                if suffix != "run" and re.findall(r"(?m)^        run: (.*)$", block) != [
                        f"/usr/bin/python3 -B -m scripts.ci.apple_verification_hygiene {suffix} {current}"]:
                    fail("verification scope Apple cycles require the guarded native lifecycle helper")
                if suffix == "finish" and re.findall(r"(?m)^          ([A-Z][A-Z0-9_]+): (.*)$", block) != [
                        ("PARLOR_APPLE_PREPARE_OUTCOME", "${{ steps." + stem + "_prepare.outcome }}"),
                        ("PARLOR_APPLE_RUN_OUTCOME", "${{ steps." + stem + "_run.outcome }}")]:
                    fail("verification scope Apple cycles require exact prepare/run outcomes")
        cleanup = validation_step(job, shared_end[0])
        for name, command in ((shared_start[0], "prepare"), (shared_end[0], "cleanup")):
            block = validation_step(job, name)
            script = ('          import runpy, sys\n'
                      '          sys.argv = ["verification_hygiene.py", "' + command + '"]\n'
                      '          runpy.run_path("scripts/ci/verification_hygiene.py", run_name="__main__")')
            expected_fields = [("if", FULL_VERIFICATION_SCOPE), ("id", "verification_ownership")] if command == "prepare" else [
                ("if", FULL_VERIFICATION_FINALIZER), ("env", "")]
            expected_fields += [("shell", "python"), ("run", "|")]
            if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", block) != expected_fields or
                    block.split("        run: |\n", 1)[-1].rstrip() != script):
                fail("verification scope Apple output ownership and cleanup must invoke only the guarded common helper")
        if cleanup.count("PARLOR_APPLE_CYCLE_OUTCOMES: >-\n") != 1:
            fail("verification scope Apple cleanup requires exact two-cycle outcome bindings")
        outcomes_text = cleanup.split("PARLOR_APPLE_CYCLE_OUTCOMES: >-\n", 1)[1].split(
            "\n          PARLOR_VERIFICATION_UPLOAD_OUTCOME:", 1)[0]
        def unique_pairs(pairs: list[tuple[str, object]]) -> dict:
            result = dict(pairs)
            if len(result) != len(pairs):
                raise ValueError("duplicate cycle/outcome key")
            return result
        try:
            outcomes = json.loads(outcomes_text, object_pairs_hook=unique_pairs)
        except ValueError:
            fail("verification scope Apple cleanup cycle bindings must be valid JSON")
        expected_outcomes = {
            current: {phase: "${{ steps." + current.replace("-", "_") + "_" + phase + ".outcome }}"
                      for phase in ("prepare", "run", "finish")}
            for current in ("apple-aggregate", cycle)
        }
        if outcomes != expected_outcomes or re.findall(r"(?m)^          ([A-Z][A-Z0-9_]+): (.*)$", cleanup) != [
                ("PARLOR_VERIFICATION_PREPARE_OUTCOME", "${{ steps.verification_ownership.outcome }}"),
                ("PARLOR_APPLE_CYCLE_OUTCOMES", ">-"),
                ("PARLOR_VERIFICATION_UPLOAD_OUTCOME", "${{ steps.verification_artifact.outcome }}"),
                ("PARLOR_VERIFICATION_ARTIFACT_ID", "${{ steps.verification_artifact.outputs.artifact-id }}"),
                ("PARLOR_VERIFICATION_ARTIFACT_DIGEST", "${{ steps.verification_artifact.outputs.artifact-digest }}")]:
            fail("verification scope Apple cleanup requires only its own two cycles and uploaded custody")
        evidence = validation_step(job, upload)
        paths = current_verification_artifact_paths(job_id)
        if (re.findall(r"(?m)^        id: (.*)$", evidence) != ["verification_artifact"] or
                re.findall(r"(?m)^          (name|if-no-files-found|retention-days|path): (.*)$", evidence) != [
                    ("name", artifact_name), ("if-no-files-found", "warn"), ("retention-days", "14"), ("path", "|")] or
                re.findall(r"(?m)^            (\S.*)$", evidence) != paths):
            fail("verification scope Apple evidence must retain exact per-job reports and lifecycle receipts")
        receipt = validation_step(job, shared_end[1])
        if re.findall(r"(?m)^          (name|if-no-files-found|retention-days|path): (.*)$", receipt) != [
                ("name", "verification-cleanup-${{ github.job }}"), ("if-no-files-found", "warn"),
                ("retention-days", "14"), ("path", prefix + "-cleanup.json")]:
            fail("verification scope Apple cleanup receipts must be distinct and source/run/job bound")


def verify_protection_probe(job: str) -> None:
    """The seventh job is an opt-in, token-free standalone diagnostic, never full."""
    header = job.split("\n    steps:\n", 1)[0]
    if (re.findall(r"(?m)^    ([a-z-]+):(?: (.*))?$", header) != [
            ("if", PROTECTION_PROBE_SCOPE), ("name", VERIFICATION_JOBS["ios-protection-probe"]),
            ("runs-on", "macos-15"), ("timeout-minutes", "20"), ("env", "")] or
            re.findall(r"(?m)^      ([A-Z][A-Z0-9_]+): (.*)$", header) != [
                ("DEVELOPER_DIR", "/Applications/Xcode_26.3.app/Contents/Developer"),
                ("PARLOR_DISPATCH_SCOPE", "${{ inputs.verification_scope }}"),
                ("PARLOR_PROTECTION_SELECTION", "${{ inputs.native_selection }}"),
                ("PARLOR_FROZEN_SOURCE_SHA", "${{ inputs.frozen_source_sha }}"),
                ("PARLOR_APPROVED_PROBE_CONTROL_SHA256", "${{ inputs.approved_probe_control_sha256 }}")]):
        fail("verification scope protection probe requires exact opt-in, toolchain, source/control and bounded job inputs")
    names = ["Check out source", "Run independently approved strict protection diagnostic",
             "Upload strict protection diagnostic evidence", "Finalize strict protection diagnostic resources and custody",
             "Upload strict protection diagnostic cleanup", "Assert diagnostic collection and cleanup outcomes"]
    if re.findall(r"(?m)^      - name: (.*)$", job) != names[:1] + ["Set up JDK 21"] + names[1:]:
        fail("verification scope protection probe requires exact collection/upload/cleanup/assertion order")
    checkout = validation_step(job, names[0])
    if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", checkout) != [
            ("uses", "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1"), ("with", "")] or
            re.findall(r"(?m)^          ([a-z-]+): (.*)$", checkout) != [
                ("fetch-depth", "0"), ("persist-credentials", "false")]):
        fail("verification scope protection probe requires complete history and a credential-free checkout")
    verify_verification_jdk(job)
    runner = "/usr/bin/python3 -B remediation-runs/2026-09-08-continuation/protection-diagnostic-01/run_probe.py "
    host_runner = "/usr/bin/python3 -B remediation-runs/2026-09-08-continuation/protection-host-image-01/run_host_probe.py "
    for name, command, step_id, environment in (
        (names[1], "run", "protection_probe", []),
        (names[3], "cleanup", "protection_probe_cleanup", [
            ("PARLOR_PROTECTION_UPLOAD_OUTCOME", "${{ steps.protection_probe_artifact.outcome }}"),
            ("PARLOR_PROTECTION_ARTIFACT_ID", "${{ steps.protection_probe_artifact.outputs.artifact-id }}"),
            ("PARLOR_PROTECTION_ARTIFACT_DIGEST", "${{ steps.protection_probe_artifact.outputs.artifact-digest }}")]),
        (names[5], "assert-result", None, [
            ("PARLOR_PROTECTION_RUN_OUTCOME", "${{ steps.protection_probe.outcome }}"),
            ("PARLOR_PROTECTION_CLEANUP_OUTCOME", "${{ steps.protection_probe_cleanup.outcome }}"),
            ("PARLOR_PROTECTION_UPLOAD_OUTCOME", "${{ steps.protection_probe_artifact.outcome }}"),
            ("PARLOR_PROTECTION_ARTIFACT_ID", "${{ steps.protection_probe_artifact.outputs.artifact-id }}"),
            ("PARLOR_PROTECTION_ARTIFACT_DIGEST", "${{ steps.protection_probe_artifact.outputs.artifact-digest }}"),
            ("PARLOR_PROTECTION_CLEANUP_UPLOAD_OUTCOME", "${{ steps.protection_probe_cleanup_artifact.outcome }}"),
            ("PARLOR_PROTECTION_CLEANUP_ARTIFACT_ID", "${{ steps.protection_probe_cleanup_artifact.outputs.artifact-id }}"),
            ("PARLOR_PROTECTION_CLEANUP_ARTIFACT_DIGEST", "${{ steps.protection_probe_cleanup_artifact.outputs.artifact-digest }}")]),
    ):
        block = validation_step(job, name)
        expected_fields = [("id", step_id)] if step_id is not None else []
        if command != "run":
            expected_fields += [("if", "always()")]
        expected_fields += [("shell", "bash")]
        if environment:
            expected_fields += [("env", "")]
        expected_fields += [("run", "|")]
        script = ('          set -euo pipefail\n'
                  '          case "$PARLOR_PROTECTION_SELECTION" in\n'
                  '            paired)\n'
                  '              ' + runner + command + '\n'
                  '              ;;\n'
                  '            protection-host-only)\n'
                  '              ' + host_runner + command + '\n'
                  '              ;;\n'
                  '            *)\n'
                  "              printf '%s\\n' 'Unsupported protection diagnostic selection' >&2\n"
                  '              exit 64\n'
                  '              ;;\n'
                  '          esac')
        if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", block) != expected_fields or
                re.findall(r"(?m)^          ([A-Z][A-Z0-9_]+): (.*)$", block) != environment or
                block.split("        run: |\n", 1)[-1].rstrip() != script):
            fail("verification scope protection probe requires identical closed routes, token-free commands, bindings and actual outcomes")
    prefix = "${{ runner.temp }}/parlor-protection-probe-${{ github.run_id }}-${{ github.run_attempt }}/"
    for name, step_id, artifact, path in (
        (names[2], "protection_probe_artifact", "ios-protection-probe", "evidence"),
        (names[4], "protection_probe_cleanup_artifact", "ios-protection-probe-cleanup", "cleanup"),
    ):
        block = validation_step(job, name)
        if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", block) != [
                ("id", step_id), ("if", "always()"),
                ("uses", "actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2"), ("with", "")] or
                re.findall(r"(?m)^          ([a-z-]+): (.*)$", block) != [
                    ("name", artifact + "-${{ github.run_id }}-${{ github.run_attempt }}"),
                    ("if-no-files-found", "error"), ("retention-days", "14"), ("path", prefix + path)]):
            fail("verification scope protection probe requires exact evidence/cleanup artifact custody")
    if any(token in job for token in ("github.token", "secrets.", "verification_hygiene.py", "./gradlew")):
        fail("verification scope protection probe forbids tokens, app builds and unrelated cleanup helpers")


def verify_apple_release_fanout(text: str) -> None:
    """Preserve the required check; all architectures/custody must join successfully."""
    simulator = validation_job(text, "ios-release-simulator")
    output_fields = ("source_sha", "source_tree", "evidence_id", "evidence_sha256", "cleanup_sha256")
    expected_outputs = [(field, "${{ steps.apple_lane.outputs." + field + " }}") for field in output_fields]
    publish = validation_step(simulator, "Publish qualified Apple lane identity")
    if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", publish) != [
            ("if", "success() && (" + FULL_VERIFICATION_SCOPE + ")"), ("id", "apple_lane"),
            ("shell", "bash"), ("run", "/usr/bin/python3 -B -m scripts.ci.apple_release_gate lane")]):
        fail("verification scope Release lane identity must follow successful evidence/cleanup")
    for job_id in ("ios-release-simulator", "ios-release-device", "ios-release-x64"):
        header = validation_job(text, job_id).split("\n    steps:\n", 1)[0]
        if (header.count("    outputs:\n") != 1 or
                re.findall(r"(?m)^      ([a-z_0-9]+): (.*)$", header) != expected_outputs or
                re.search(r"(?m)^    (needs|strategy|permissions|continue-on-error):", header)):
            fail("verification scope Release lanes require independent standard runners and exact custody outputs")
    shared_names = ["Check out source", "Set up JDK 21", "Restore reviewed Gradle dependencies", "Claim fresh verification output ownership",
                    "Claim apple-aggregate native resource ownership", "Record Xcode toolchain and exact source"]
    common_finish = ["Stop Gradle after apple-aggregate and retire owned Apple resources",
                     "Validate iOS plist and privacy manifest"]
    for job_id, target in (("ios-release-device", "IosArm64"), ("ios-release-x64", "IosX64")):
        job = validation_job(text, job_id)
        header = job.split("\n    steps:\n", 1)[0]
        if (re.findall(r"(?m)^    (if|runs-on|timeout-minutes): (.*)$", header) != [
                ("if", FULL_VERIFICATION_SCOPE), ("runs-on", FREE_APPLE_RELEASE_RUNNER), ("timeout-minutes", "180")] or
                re.findall(r"(?m)^      DEVELOPER_DIR: (.*)$", header) != ["/Applications/Xcode_26.3.app/Contents/Developer"]):
            fail("verification scope Release architecture requires pinned free standard runner/toolchain")
        run = "Link optimized " + target + " Release framework"
        upload = "Upload " + target + " release verification evidence"
        end = [upload, "Clean only attested verification outputs", "Upload verification cleanup receipt",
               "Publish qualified Apple lane identity", "Save dependencies from trusted main only"]
        if re.findall(r"(?m)^      - name: (.*)$", job) != shared_names + [run] + common_finish + end:
            fail("verification scope Release architecture must retain exact ownership/run/retirement/upload order")
        for name in shared_names + common_finish + [end[2], end[3]]:
            if validation_step(job, name) != validation_step(simulator, name):
                fail("verification scope Release architecture changed shared source/toolchain/resource controls")
        run_step = validation_step(job, run)
        command = "./gradlew :composeApp:linkReleaseFramework" + target + " --dependency-verification=strict --no-daemon --stacktrace --console=plain"
        architecture = "arm64" if target == "IosArm64" else "x86_64"
        binary_target = target[0].lower() + target[1:]
        # -verify_arch consumes the remaining arguments as architecture names.
        # The input binary must precede it; an incorrect order fails only after
        # the expensive optimized link and is not an architecture verification.
        expected_script = [
            "set -euo pipefail",
            'echo "Optimized ' + target + ' Release link; full Apple analysis and Swift packaging run in ios-release-simulator."',
            command,
            'binary="composeApp/build/bin/' + binary_target + '/releaseFramework/ComposeApp.framework/ComposeApp"',
            'test -s "$binary"',
            'xcrun lipo "$binary" -verify_arch ' + architecture,
            'file "$binary" | tee build/ci-evidence/release-framework-binary.txt',
            'shasum -a 256 "$binary" | tee build/ci-evidence/release-framework-binary.sha256',
        ]
        if (re.findall(r"(?m)^        (if|id|shell|run): (.*)$", run_step) != [
                ("if", FULL_VERIFICATION_SCOPE), ("id", "apple_aggregate_run"), ("shell", "bash"), ("run", "|")] or
                re.findall(r"(?m)^          (.*)$", run_step) != expected_script):
            fail("verification scope Release architecture must execute its real optimized link and exact binary inspection strictly")
        expected_upload = validation_step(simulator, "Upload Apple release verification evidence").replace(
            "name: ios-release-verification", "name: " + job_id + "-verification").replace(
            "            build/xcode-derived-data/Build/Products/Release-iphonesimulator/Parlor.app.dSYM/\n", "")
        if validation_step(job, upload) != expected_upload:
            fail("verification scope Release architecture must preserve current evidence and native custody")
        cleanup = validation_step(job, "Clean only attested verification outputs")
        expected_cleanup = validation_step(simulator, "Clean only attested verification outputs").replace(
            '"finish":"${{ steps.apple_aggregate_finish.outcome }}"},\n'
            '             "apple-wrapper":{"prepare":"${{ steps.apple_wrapper_prepare.outcome }}",'
            '"run":"${{ steps.apple_wrapper_run.outcome }}","finish":"${{ steps.apple_wrapper_finish.outcome }}"}}',
            '"finish":"${{ steps.apple_aggregate_finish.outcome }}"}}')
        if cleanup != expected_cleanup:
            fail("verification scope Release architecture must finalize only its assigned native cycle")
    gate = validation_job(text, "ios-release")
    header = gate.split("\n    steps:\n", 1)[0]
    if re.findall(r"(?m)^    ([a-z-]+):(?: (.*))?$", header) != [
            ("if", "always() && (" + FULL_VERIFICATION_SCOPE + ")"),
            ("name", "iOS release frameworks and Swift wrapper"),
            ("needs", "[ios-release-simulator, ios-release-device, ios-release-x64]"),
            ("runs-on", "ubuntu-24.04"), ("timeout-minutes", "5")]:
        fail("verification scope required Release check must fail closed across all three free-hosted lanes")
    if re.findall(r"(?m)^      - name: (.*)$", gate) != [
            "Check out source", "Require all Apple Release architectures and wrapper", "Upload aggregate Apple Release qualification"]:
        fail("verification scope Release join must check all lane results and publish custody")
    if validation_step(gate, "Check out source") != validation_step(simulator, "Check out source"):
        fail("verification scope Release join requires the triggering source")
    join = validation_step(gate, "Require all Apple Release architectures and wrapper")
    if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", join) != [
            ("shell", "bash"), ("env", ""), ("run", "python3 -B -m scripts.ci.apple_release_gate aggregate")] or
            re.findall(r"(?m)^          ([A-Z_]+): (.*)$", join) != [("PARLOR_APPLE_RELEASE_NEEDS", "${{ toJSON(needs) }}")]):
        fail("verification scope Release join must validate real needs, never substituted success")
    if "if:" in validation_step(gate, "Upload aggregate Apple Release qualification"):
        fail("verification scope Release join evidence must upload only after successful qualification")


def verify_free_ci_dependency_cache(text: str) -> None:
    """GitHub-owned dependency cache; only protected main pushes can write."""
    lanes = ("desktop-android", "desktop-linux-arm64", "desktop-macos-x64", "desktop-windows-x64",
             "ios", "ios-release-simulator", "ios-release-device", "ios-release-x64")
    key = "parlor-deps-v1-${{ runner.os }}-${{ runner.arch }}-jdk21-gradle8.13-kotlin2.4.10-xcode26.3-${{ hashFiles('**/*.gradle.kts', 'gradle/libs.versions.toml', 'gradle/verification-metadata.xml', 'gradle/wrapper/gradle-wrapper.properties') }}"
    expected = ('        if: ' + FULL_VERIFICATION_SCOPE + '\n'
                '        id: dependency_cache\n'
                '        uses: actions/cache/restore@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0\n'
                '        with:\n'
                '          path: |\n'
                '            ~/.gradle/caches/modules-2\n'
                '            ~/.gradle/wrapper/dists\n'
                '          key: ' + key)
    save = ("        if: success() && github.event_name == 'push' && github.ref == 'refs/heads/main' && steps.dependency_cache.outputs.cache-hit != 'true'\n"
            '        uses: actions/cache/save@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0\n'
            '        with:\n'
            '          path: |\n'
            '            ~/.gradle/caches/modules-2\n'
            '            ~/.gradle/wrapper/dists\n'
            '          key: ${{ steps.dependency_cache.outputs.cache-primary-key }}')
    if text.count('uses: actions/cache/restore@') != len(lanes) or text.count('uses: actions/cache/save@') != len(lanes):
        fail("verification scope requires only reviewed dependency caches in ordinary build lanes")
    for lane in lanes:
        body = validation_job(text, lane)
        cache = validation_step(body, "Restore reviewed Gradle dependencies")
        if cache.strip("\n") != expected or validation_step(body, "Save dependencies from trusted main only").strip("\n") != save:
            fail("verification scope dependency-only cache must be read-only except protected main pushes")
        if not (body.index('- name: Set up JDK 21\n') < body.index('- name: Restore reviewed Gradle dependencies\n') <
                body.index('- name: Claim fresh verification output ownership\n') < body.index('- name: Save dependencies from trusted main only\n')):
            fail("verification scope dependency cache must not restore owned build outputs")
    selections = re.findall(r"(?m)^      apple_release_runner:\n((?:        .*\n)+)", text.split("\nconcurrency:", 1)[0])
    if (len(selections) != 1 or
            re.findall(r"(?m)^        (type|default|options): (.*)$", selections[0]) != [
                ("type", "choice"), ("default", "arm64"), ("options", "[arm64, intel]")]):
        fail("verification scope Apple benchmarking permits only two standard free runner families")


DARWIN_CAPABILITY_JOB = """    if: github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'darwin-host-capability'
    name: Darwin native host capability (${{ matrix.architecture }})
    runs-on: ${{ matrix.runner }}
    timeout-minutes: 5
    strategy:
      fail-fast: false
      matrix:
        include:
          - runner: macos-15
            architecture: arm64
          - runner: macos-15-intel
            architecture: x86_64
    env:
      PARLOR_DISPATCH_SCOPE: ${{ inputs.verification_scope }}
      PARLOR_FROZEN_SOURCE_SHA: ${{ inputs.frozen_source_sha }}
      PARLOR_EXPECTED_ARCHITECTURE: ${{ matrix.architecture }}
    steps:
      - name: Check out exact capability source
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          fetch-depth: 1
          persist-credentials: false
      - name: Verify real Darwin identity tracking and audit-token signal
        timeout-minutes: 2
        run: /usr/bin/python3 -B -m scripts.ci.darwin_host_capability
      - name: Preserve native capability receipt
        if: always()
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: darwin-host-capability-${{ github.run_id }}-${{ github.run_attempt }}-${{ matrix.architecture }}
          if-no-files-found: error
          retention-days: 14
          path: ${{ runner.temp }}/parlor-darwin-capability-${{ github.run_id }}-${{ github.run_attempt }}-${{ matrix.architecture }}.json
"""


def verify_darwin_host_capability(text: str) -> None:
    if validation_job(text, "darwin-host-capability").strip() != DARWIN_CAPABILITY_JOB.strip():
        fail("verification scope Darwin capability requires its exact free hosted matrix, explicit source/run context and bounded native receipt control")


def verify_verification_scopes(text: str) -> None:
    """Only closed reviewed diagnostic/follow-up scopes bypass six mandatory full jobs."""
    jobs_text = text.split("\njobs:\n", 1)[-1]
    jobs = re.findall(r"(?m)^  ([A-Za-z_][A-Za-z0-9_-]*):$", jobs_text)
    expected = list(VERIFICATION_JOBS)
    if jobs != expected:
        fail("verification scope contract requires all full lanes, the required Release join, and one focused protection probe")
    for name, display in VERIFICATION_JOBS.items():
        if re.findall(r"(?m)^    name: (.*)$", validation_job(text, name)) != [display]:
            fail("verification scope requires the exact reviewed job/check identities")
    if text.count("--android-reader") != 1 or text.count("sudo-proc-exe-v2") != 1:
        fail("verification scope permits the explicit Android reader only in Linux runtime cleanup")
    for name in expected[:4]:
        block = validation_job(text, name)
        required = (WINDOWS_VERIFICATION_SCOPE if name == "desktop-windows-x64" else
                    LINUX_VERIFICATION_SCOPE if name == "desktop-android" else FULL_VERIFICATION_SCOPE)
        if re.findall(r"(?m)^    if: (.*)$", block) != [required]:
            fail("verification scope must retain full jobs and isolate the Windows-only follow-up")
        if name == "desktop-windows-x64":
            verify_windows_checkout(block)
            verify_windows_distribution_ownership(block)
        elif name == "desktop-android":
            verify_linux_process_probe(block)
    ios = validation_job(text, "ios")
    if re.findall(r"(?m)^    if: (.*)$", ios) != [IOS_VERIFICATION_SCOPE]:
        fail("verification scope may skip iOS only for separately guarded Windows, Linux or protection follow-ups")
    if re.findall(r"(?m)^    timeout-minutes: (.*)$", ios) != [
            "${{ inputs.verification_scope == 'native-process-probe' && 10 || inputs.verification_scope == 'native-evidence' && inputs.native_selection == 'settings-sheet-only' && 40 || inputs.verification_scope == 'native-evidence' && inputs.native_selection == 'os-recovery-only' && 120 || inputs.verification_scope == 'native-evidence' && inputs.native_selection == 'protection-application-only' && 90 || inputs.verification_scope == 'native-evidence' && 240 || 120 }}"]:
        fail("verification scope must keep full/preflight120, probe10, settings-sheet evidence40, OS-recovery evidence120, protection-application evidence90 and other native-evidence240 minute bounds")
    clock_name = "Start focused native job clock"
    clock = validation_step(ios, clock_name)
    clock_script = '''        run: |
          /usr/bin/python3 -B - <<'PY' >> "$GITHUB_ENV"
          import json
          import os
          import time
          value = time.clock_gettime_ns(time.CLOCK_MONOTONIC_RAW)
          if type(value) is not int or value <= 0:
              raise RuntimeError("invalid-native-job-kernel-clock")
          clock = dict(clock="CLOCK_MONOTONIC_RAW", start_ns=value,
                       run_id=int(os.environ["GITHUB_RUN_ID"]), run_attempt=int(os.environ["GITHUB_RUN_ATTEMPT"]),
                       job=os.environ["GITHUB_JOB"], head_sha=os.environ["GITHUB_SHA"])
          print("PARLOR_NATIVE_JOB_CLOCK=" + json.dumps(clock, separators=(",", ":")))
          PY
'''
    expected_clock = ("        if: github.event_name == 'workflow_dispatch' && inputs.verification_scope == 'native-evidence'\n"
                      "        shell: bash\n" + clock_script)
    if (clock.strip("\n") != expected_clock.strip("\n") or
            re.findall(r"(?m)^      - name: (.*)$", ios)[0] != clock_name or
            ios.count("PARLOR_NATIVE_JOB_CLOCK=") != 1):
        fail("focused native job clock must be fresh, source/run-bound, RAW kernel time before checkout and evidence-only")
    for token in ("        default: full\n", "        options: [full, native-preflight, native-evidence, native-process-probe, windows-only, linux-process-probe, android-cleanup-only, ios-protection-probe, darwin-host-capability]\n"):
        if token not in text.split("\nconcurrency:", 1)[0]:
            fail("verification scope must default to full with only the reviewed native, Windows and Linux modes")
    selections = re.findall(r"(?m)^      native_selection:\n((?:        .*\n)+)", text.split("\nconcurrency:", 1)[0])
    if (len(selections) != 1 or
            re.findall(r"(?m)^        (type|default|options): (.*)$", selections[0]) !=
            [("type", "choice"), ("default", "paired"), ("options", "[paired, l08-only, settings-sheet-only, os-recovery-only, protection-application-only, protection-host-only]")]):
        fail("verification scope must retain closed paired-default, l08-only, settings-sheet-only, os-recovery-only, protection-application-only or protection-host-only selection")
    for name in ("Validate verification scope", "Run focused native continuation",
                 "Verify focused native cleanup and uploaded custody"):
        if re.findall(r"(?m)^          PARLOR_NATIVE_SELECTION: (.*)$", validation_step(ios, name)) != [
                "${{ inputs.native_selection }}"]:
            fail("verification scope must bind identical explicit native selection into validation/run/cleanup")
    if text.count("PARLOR_NATIVE_SELECTION:") != 3:
        fail("verification scope must not override native selection outside its three reviewed boundaries")
    if text.count("PARLOR_PROTECTION_SELECTION:") != 1:
        fail("verification scope must bind protection selection only once at the separate diagnostic job boundary")
    diagnostic = validation_step(ios, "Run focused native continuation")
    if (re.findall(r"(?m)^          PARLOR_APPROVED_PROBE_CONTROL_SHA256: (.*)$", diagnostic) != [
            "${{ inputs.approved_probe_control_sha256 }}"] or ios.count("PARLOR_APPROVED_PROBE_CONTROL_SHA256:") != 3 or
            text.count("PARLOR_APPROVED_PROBE_CONTROL_SHA256:") != 4):
        fail("verification scope requires the explicit diagnostic control hash only at its four reviewed boundaries")
    validator = validation_step(ios, "Validate verification scope")
    if (re.findall(r"(?m)^        if: (.*)$", validator) or
            "run: /usr/bin/python3 -B scripts/ci/native_continuation.py validate-scope\n" not in validator or
            any(ios.index("- name: Validate verification scope\n") > ios.index("- name: " + name + "\n")
                for name in ("Run focused native continuation", "Observe hosted native processes without an app build"))):
        fail("verification scope must reject unknown values before any native execution")
    verify_split_apple_scopes(text)
    verify_apple_release_fanout(text)
    verify_free_ci_dependency_cache(text)
    verify_current_verification_artifacts(text)
    verify_protection_probe(validation_job(text, "ios-protection-probe"))
    verify_darwin_host_capability(text)
    focused_steps = ("Run focused native continuation", "Upload focused native evidence",
                     "Verify focused native cleanup and uploaded custody", "Upload native continuation cleanup receipt")
    for name in focused_steps:
        required = NATIVE_VERIFICATION_SCOPE if name == focused_steps[0] else "always() && (" + NATIVE_VERIFICATION_SCOPE + ")"
        if re.findall(r"(?m)^        if: (.*)$", validation_step(ios, name)) != [required]:
            fail("verification scope must restrict native execution and always preserve focused evidence/cleanup")
    probe_steps = ("Observe hosted native processes without an app build", "Upload bounded process-probe evidence",
                   "Verify process-probe cleanup and uploaded custody", "Upload process-probe cleanup receipt")
    for name in probe_steps:
        required = PROCESS_PROBE_SCOPE if name == probe_steps[0] else "always() && (" + PROCESS_PROBE_SCOPE + ")"
        if re.findall(r"(?m)^        if: (.*)$", validation_step(ios, name)) != [required]:
            fail("verification scope must isolate the non-app process probe and preserve its evidence/cleanup")
    probe = validation_step(ios, probe_steps[0])
    cleanup = validation_step(ios, probe_steps[2])
    public_binding = [
        ("PARLOR_DISPATCH_SCOPE", "${{ inputs.verification_scope }}"),
        ("PARLOR_FROZEN_SOURCE_SHA", "${{ inputs.frozen_source_sha }}"),
        ("PARLOR_APPROVED_PROBE_CONTROL_SHA256", "${{ inputs.approved_probe_control_sha256 }}"),
    ]
    for block, command, environment in (
        (probe, "run", public_binding),
        (cleanup, "cleanup", public_binding + [
            ("PARLOR_PROBE_UPLOAD_OUTCOME", "${{ steps.process_probe_artifact.outcome }}"),
            ("PARLOR_PROBE_ARTIFACT_ID", "${{ steps.process_probe_artifact.outputs.artifact-id }}"),
            ("PARLOR_PROBE_ARTIFACT_DIGEST", "${{ steps.process_probe_artifact.outputs.artifact-digest }}"),
        ]),
    ):
        if (re.findall(r"(?m)^        run: (.*)$", block) != [
                "/usr/bin/python3 -B scripts/ci/native_process_probe.py " + command] or
                re.findall(r"(?m)^          ([A-Z][A-Z0-9_]+): (.*)$", block) != environment or
                "github.token" in block):
            fail("verification scope process probe requires exact reviewed source/control and token-free custody inputs")
    artifact = validation_step(ios, probe_steps[1])
    cleanup_artifact = validation_step(ios, probe_steps[3])
    if (re.findall(r"(?m)^        id: (.*)$", probe) != ["process_probe"] or
            re.findall(r"(?m)^        id: (.*)$", artifact) != ["process_probe_artifact"]):
        fail("verification scope process probe requires exact producer/upload step identities")
    for block, name, path in (
        (artifact, "native-process-probe-${{ github.run_id }}-${{ github.run_attempt }}",
         "${{ runner.temp }}/parlor-process-probe-${{ github.run_id }}-${{ github.run_attempt }}/bundle/"),
        (cleanup_artifact, "native-process-probe-cleanup-${{ github.run_id }}-${{ github.run_attempt }}",
         "${{ runner.temp }}/parlor-process-probe-${{ github.run_id }}-${{ github.run_attempt }}-cleanup.json"),
    ):
        if ("uses: actions/upload-artifact@" not in block or
                re.findall(r"(?m)^          (name|path|if-no-files-found|retention-days): (.*)$", block) !=
                [("name", name), ("if-no-files-found", "error"), ("retention-days", "14"), ("path", path)]):
            fail("verification scope process probe requires exact evidence and cleanup artifact custody")
    if (sorted(ios.index("- name: " + name + "\n") for name in probe_steps) !=
            [ios.index("- name: " + name + "\n") for name in probe_steps]):
        fail("verification scope process probe requires reviewed source/control, no token, and upload-before-cleanup custody")


def verify_validation(text: str) -> None:
    if "secrets." in text:
        fail("main/PR validation workflow must not reference secrets")
    if "main" not in text or "testing" not in text or "release" not in text:
        fail("validation workflow must qualify all protected branches")
    if "productionReleaseAutomationCheck" not in text:
        fail("validation workflow does not enforce release-system tests")
    verify_android_runtime_smoke("production-verification.yml", text)
    desktop_android_marker = "\n  desktop-android:"
    next_job_marker = "\n  desktop-linux-arm64:"
    if desktop_android_marker not in text or next_job_marker not in text:
        fail("validation workflow is missing the desktop-android job boundary")
    desktop_android_job = text.split(desktop_android_marker, 1)[1].split(next_job_marker, 1)[0]
    if "fetch-depth: 0" not in desktop_android_job:
        fail("validation workflow review-inventory gate requires full Git history")
    runtime = validation_job(text, "ios")
    release = validation_job(text, "ios-release-simulator")
    native_controls = validation_step(runtime, "Run all KMP iOS simulator runtime tests")
    temporary_scope = '''          (
          test -d "$RUNNER_TEMP" && test ! -L "$RUNNER_TEMP"
          test "$(cd -- "$RUNNER_TEMP" && pwd -P)" = "$RUNNER_TEMP"
          native_control_tmp="$(mktemp -d "$RUNNER_TEMP/parlor-native-controls.XXXXXX")"
          retire_native_control_tmp() {
            native_control_status=$?
            trap - EXIT
            if ! rmdir -- "$native_control_tmp"; then
              echo "Native control temporary directory is not empty; preserve it and fail." >&2
              native_control_status=1
            fi
            exit "$native_control_status"
          }
          trap 'retire_native_control_tmp' EXIT
          export TMPDIR="$native_control_tmp"
'''
    if (native_controls.count(temporary_scope) != 1 or
            native_controls.count('          )\n') != 1 or
            native_controls.count('          retire_native_control_tmp\n          )\n') != 1 or
            native_controls.count('export TMPDIR=') != 1):
        fail("macOS diagnostic controls require scoped canonical private temporary ownership and fail-closed retirement")
    for directory, report in (
        ("scripts/verification/ios-readiness", "ios-readiness-control-tests.log"),
        ("remediation-runs/2026-09-08-continuation/protection-application-01", "ios-protection-application-control-tests.log"),
    ):
        command = ("/usr/bin/python3 -B -m unittest discover -s " + directory +
                   " -p 'test_*.py' -v 2>&1 | tee build/ci-evidence/" + report)
        if (native_controls.count(command) != 1 or
                re.findall(r"(?m)^          (.*" + re.escape(directory) + r".*)$", native_controls) != [command] or
                native_controls.index(command) > native_controls.index("./gradlew ") or
                native_controls.index(command) > native_controls.index('          )\n') or
                native_controls.index(command) < native_controls.index('export TMPDIR=') or
                "          set -euo pipefail\n" not in native_controls):
            fail("validation workflow must run complete macOS diagnostic controls fail-closed before the Apple runtime aggregate")
    for job, name, task in (
        (runtime, "Run all KMP iOS simulator runtime tests", "productionIosSimulatorRuntimeTests"),
        (release, "Run complete Apple static analysis", "productionAppleStaticAnalysis"),
    ):
        aggregate = validation_step(job, name)
        command = "./gradlew " + task + " --dependency-verification=strict --no-daemon --stacktrace --console=plain"
        if (re.findall(r"(?m)^          (\./gradlew .*)$", aggregate) != [command] or
                re.findall(r"(?m)^          (\./gradlew .*)$", job) != [command]):
            fail("validation workflow must enforce each complete split Apple aggregate without replacing or duplicating its gates")
        if "./gradlew allTests" in job:
            fail("Apple validation duplicates the Linux common/desktop/Android test aggregate")
    app_launch_marker = "- name: Launch Swift host and Compose root on iOS Simulator"
    swift_release_marker = "- name: Build unsigned Swift Release wrapper"
    if app_launch_marker not in runtime or swift_release_marker not in release:
        fail("validation workflow does not run the iOS app-launch UI test")
    app_launch_step = validation_step(runtime, "Launch Swift host and Compose root on iOS Simulator")
    required_app_launch_contract = (
        "-project iosApp/iosApp.xcodeproj",
        "-scheme iosApp",
        "-configuration Debug",
        "-sdk iphonesimulator",
        'platform=iOS Simulator,id=$simulator_udid',
        "-resultBundlePath build/ci-evidence/ios-ui-tests.xcresult",
        "test | tee build/ci-evidence/xcode-ui-test.log",
    )
    for token in required_app_launch_contract:
        if token not in app_launch_step:
            fail(f"validation workflow iOS app-launch test lacks {token!r}")
    verify_owned_ios_app_launch(runtime)
    for step, command in (
        ("Inspect unsigned Android release artifact and merged manifest",
         'python3 -B scripts/verification/third_party_notices.py --package "$aab" --json >build/ci-evidence/android-release-notices.json'),
        ("Build unsigned Swift Release wrapper",
         '/usr/bin/python3 -B scripts/verification/third_party_notices.py --package "$app" --json >build/ci-evidence/ios-release-notices.json'),
    ):
        if f"          {command}\n" not in validation_step(text, step):
            fail(f"validation workflow lacks packaged-notice verification in {step!r}")
    android_inspection = validation_step(text, "Inspect unsigned Android release artifact and merged manifest")
    for token in (
        'scripts/verification/android_release_artifacts.py --package "$aab"',
        '--bundletool "$tools/bundletool.jar" --dexdump "$ANDROID_HOME/build-tools/36.0.0/dexdump"',
        '>build/ci-evidence/android-release-artifact-inventory.json',
        'trap \'rm -rf "$tools"\' EXIT',
    ):
        if token not in android_inspection:
            fail("validation workflow lacks complete unsigned Android artifact inspection/cleanup")
    ios_inspection = validation_step(release, "Inspect complete unsigned iOS Release package")
    for token in (
        "if: success() && steps.apple_wrapper_run.outcome == 'success' && steps.apple_wrapper_finish.outcome == 'success'",
        '/usr/bin/python3 -B scripts/verification/ios_release_artifacts.py',
        '--app build/xcode-derived-data/Build/Products/Release-iphonesimulator/Parlor.app',
        '--source "$GITHUB_WORKSPACE" --json >build/ci-evidence/ios-release-artifact-inventory.json',
    ):
        if token not in ios_inspection:
            fail("validation workflow lacks current-source complete iOS artifact inspection")
    if not (release.index('id: apple_wrapper_finish') < release.index('name: Inspect complete unsigned iOS Release package')
            < release.index('name: Upload Apple release verification evidence')):
        fail("complete iOS artifact inspection must follow successful immediate finalization and precede evidence upload")
    if '$1 ~ /PRODUCT_BUNDLE_IDENTIFIER$/' in text:
        fail("validation workflow can confuse the Mac Catalyst derivation flag with the Bundle ID")
    if text.count('key == "PRODUCT_BUNDLE_IDENTIFIER"') != 2:
        fail("validation workflow does not parse the exact Xcode Bundle-ID build setting")
    verify_verification_scopes(text)
    source_script = '''          set -euo pipefail
          mkdir -p build/ci-evidence
          test -x "$DEVELOPER_DIR/usr/bin/xcodebuild"
          xcodebuild -version | tee build/ci-evidence/xcode-version.txt
          test "$(sed -n '1p' build/ci-evidence/xcode-version.txt)" = "Xcode 26.3"
          test "$(sed -n '2p' build/ci-evidence/xcode-version.txt)" = "Build version 17C529"
          test "$(jq -r .toolchains.apple.developer_dir config/release-policy.json)" = "$DEVELOPER_DIR"
          test "$(jq -r .toolchains.apple.xcode_build config/release-policy.json)" = "17C529"
          xcrun --sdk iphoneos --show-sdk-version | tee build/ci-evidence/iphoneos-sdk.txt
          xcrun --sdk iphonesimulator --show-sdk-version | tee build/ci-evidence/iphonesimulator-sdk.txt
          test "$(cut -d. -f1 build/ci-evidence/iphoneos-sdk.txt)" -ge "$(jq -r .toolchains.apple.minimum_ios_sdk_major config/release-policy.json)"
          git rev-parse HEAD | tee build/ci-evidence/commit.txt
          test -z "$(git status --porcelain)"'''
    for job in (runtime, release):
        source = validation_step(job, "Record Xcode toolchain and exact source")
        if (re.findall(r"(?m)^        ([a-z-]+):(?: (.*))?$", source) != [
                ("if", FULL_VERIFICATION_SCOPE), ("shell", "bash"), ("run", "|")] or
                source.split("        run: |\n", 1)[-1].rstrip() != source_script):
            fail("validation workflow requires exact independent Apple source/toolchain guards")


def verify_store_workflow(name: str, text: str) -> None:
    if "workflow_dispatch:" not in text:
        fail(f"{name}: Store mutation must require workflow_dispatch")
    if re.search(r"(?m)^\s{0,4}(push|pull_request):", text):
        fail(f"{name}: Store workflow must never run automatically from push/PR")
    if "cancel-in-progress: false" not in text:
        fail(f"{name}: Store mutation concurrency must not cancel in progress")
    if "environment:" not in text:
        fail(f"{name}: Store jobs must use protected environments")
    if "validation_only" not in text and "publish" not in text and "execute" not in text:
        fail(f"{name}: Store workflow lacks explicit mutation mode")
    if "assert-store-identity-approved" not in text:
        fail(f"{name}: Store workflow does not fail closed on unverified Store identity ownership")
    preflight = text.split("\n  android:", 1)[0]
    if "secrets." in preflight:
        fail(f"{name}: preflight code can access Store credentials before protected-environment approval")
    verify_store_jobs_disabled(name, text)


def verify_store_jobs_disabled(name: str, text: str) -> None:
    """Require an unconditional repository-owned stop on every Store job."""
    lines = text.splitlines()
    try:
        jobs_start = lines.index("jobs:")
    except ValueError:
        fail(f"{name}: Store workflow has no jobs section")

    jobs: dict[str, list[str]] = {}
    current_job: str | None = None
    for line in lines[jobs_start + 1 :]:
        if line and not line[0].isspace():
            if line.startswith("#"):
                continue
            break
        match = re.fullmatch(r"  ([A-Za-z_][A-Za-z0-9_-]*):\s*(?:#.*)?", line)
        if match:
            current_job = match.group(1)
            jobs[current_job] = []
        elif current_job is not None:
            jobs[current_job].append(line)

    if not jobs:
        fail(f"{name}: Store workflow has no reviewable jobs")
    for job, block in jobs.items():
        conditions = [
            line.strip()
            for line in block
            if re.match(r"^ {4}if:", line)
        ]
        if conditions != [DISABLED_STORE_JOB_CONDITION]:
            fail(
                f"{name}: Store job {job!r} must remain repository-disabled with "
                f"{DISABLED_STORE_JOB_CONDITION!r}"
            )


def verify_store_serialization(files: dict[str, str]) -> None:
    android_group = "group: parlor-google-play-production-identity"
    ios_group = "group: parlor-app-store-connect-production-identity"
    for name in STORE_WORKFLOWS:
        text = files[name]
        if text.count(android_group) != 1 or text.count(ios_group) != 1:
            fail(f"{name}: Store jobs do not share the reviewed cross-workflow platform locks")


def verify_promotions(name: str, text: str) -> None:
    forbidden_build_tokens = (
        "./gradlew",
        "gradle ",
        "xcodebuild",
        "bundleRelease",
        "assembleRelease",
        "build_ios_candidate",
        "jarsigner",
        "codesign",
    )
    for token in forbidden_build_tokens:
        if token in text:
            fail(f"{name}: promotion workflow contains build/sign command {token!r}")
    if "fetch-artifact" not in text or "verify-source" not in text:
        fail(f"{name}: promotion does not load provenance and verify source")
    if "candidate_run_id" not in text or "candidate_run_attempt" not in text:
        fail(f"{name}: candidate selection is not exact")
    required_attestation_policy = (
        "gh attestation verify build/release-promotion/candidate-manifest.json",
        "--signer-workflow",
        "--source-digest",
        "--deny-self-hosted-runners",
    )
    for token in required_attestation_policy:
        if token not in text:
            fail(f"{name}: candidate provenance verification lacks {token!r}")
    required_candidate_run_binding = (
        "workflow-run-record",
        "--expected-workflow .github/workflows/testing-candidate.yml",
        "--expected-branch testing",
        "candidate-workflow-run.json",
    )
    for token in required_candidate_run_binding:
        if token not in text:
            fail(f"{name}: candidate evidence is not bound to its exact workflow attempt: {token!r}")


def verify_production(text: str) -> None:
    if "platform:" not in text or not all(value in text for value in ("android", "ios", "both")):
        fail("production workflow lacks explicit android/ios/both selection")
    if "refs/heads/release" not in text:
        fail("production workflow does not enforce the release branch")
    if "production-android" not in text or "production-ios" not in text:
        fail("production platforms do not have independent protected environments")
    if "external-receipt" not in text or "external_run_id" not in text:
        fail("production workflow does not require external-testing evidence")
    required_external_run_binding = (
        "workflow-run-record",
        "--expected-workflow .github/workflows/testing-external-promotion.yml",
        "--expected-branch testing",
        "external_source_sha",
    )
    for token in required_external_run_binding:
        if token not in text:
            fail(f"production workflow does not bind external evidence to its workflow run: {token!r}")
    marker = "gh attestation verify build/release-promotion/external-evidence.json"
    if marker not in text:
        fail("production workflow does not verify canonical external-testing evidence")
    evidence_block = text[text.index(marker) : text.index(marker) + 500]
    if '--source-digest "$external_source_sha"' not in evidence_block:
        fail("production workflow verifies external evidence against the wrong workflow source")
    for token in (
        "validate-external-evidence",
        "external_evidence_b64",
        "--require-platform android",
        "--require-platform ios",
    ):
        if token not in text:
            fail(f"production workflow does not consume canonical external evidence: {token!r}")
    for receipt in ("production-android-receipt.json", "production-ios-receipt.json"):
        marker = f"subject-path: build/release-promotion/{receipt}"
        if marker not in text:
            fail(f"production workflow does not attest {receipt}")


def verify_candidate(text: str) -> None:
    if "group: parlor-store-candidate-claim" not in text:
        fail("candidate workflow does not serialize the claim check/create transaction")
    if "group: parlor-store-candidate-${{ inputs.candidate_sha }}" in text:
        fail("candidate claim lock is incorrectly scoped per SHA and permits build-number races")
    if "refs/heads/testing" not in text:
        fail("candidate workflow does not enforce the testing branch")
    preflight = text[text.index("  preflight:") : text.index("  android:")]
    if "environment: testing-candidate" not in preflight:
        fail("candidate claim is not protected by the testing-candidate environment")
    if "secrets." in preflight:
        fail("candidate control/preflight job must not receive or reference Store secrets")
    verify_android_runtime_smoke("testing-candidate.yml preflight", preflight)
    if "candidate_sha" not in text or "^[0-9a-f]{40}$" not in text:
        fail("candidate workflow does not require an exact full commit SHA")
    if "testing-android" not in text or "testing-ios" not in text:
        fail("candidate platforms do not have independent protected environments")
    if "attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8" not in text:
        fail("candidate workflow does not create pinned GitHub artifact attestations")
    if "retention-days: 90" not in text:
        fail("candidate evidence retention is shorter than the release policy")
    if "--max-filesize 209715200" not in text:
        fail("candidate bundletool download is not bounded")
    if "--configuration releaseRuntimeClasspath" not in text:
        fail("candidate dependency evidence is not scoped to the Android release runtime")
    if "Retain Android dependency and deep-validation evidence" not in text:
        fail("candidate workflow does not retain Android dependency/validation evidence")
    required_apple_toolchain = (
        "/Applications/Xcode_26.3.app/Contents/Developer",
        'test "$(xcodebuild -version | sed -n \'1p\')" = "Xcode 26.3"',
        'test "$(xcodebuild -version | sed -n \'2p\')" = "Build version 17C529"',
        ".toolchains.apple.minimum_ios_sdk_major",
    )
    for token in required_apple_toolchain:
        if token not in text:
            fail(f"candidate workflow does not pin the reviewed Apple toolchain: {token!r}")
    required_recovery = (
        "assert-candidate-claim-exclusive",
        "create-candidate-claim",
        "parlor-candidate-claim-build-",
        "google-check-unique",
        "Reject a reused App Store build number before compiling or signing",
        "fetch-binary-artifact",
        "assert-artifact-prefix-absent",
        "gh attestation verify",
        "android-descriptor-attempt-",
        "ios-descriptor-attempt-",
        "ios-upload-transport-attempt-",
        "android-mutation-intent-attempt-",
        "ios-mutation-intent-attempt-",
        "validate-mutation-intent",
        "--recover-only",
        "Refuse an indeterminate Apple upload instead of sending the IPA twice",
        "validate-apple-upload-transport",
        "--source-digest \"$CANDIDATE_SHA\"",
        "--deny-self-hosted-runners",
    )
    for token in required_recovery:
        if token not in text:
            fail(f"candidate workflow lacks build-once recovery control {token!r}")
    android_checkpoint = text.index("Checkpoint immutable Android artifact descriptor")
    android_intent = text.index("Checkpoint Android Store mutation intent")
    android_store = text.index("Upload once to Google Play internal testing")
    ios_checkpoint = text.index("Checkpoint immutable iOS artifact descriptor")
    ios_intent = text.index("Checkpoint Apple Store mutation intent")
    ios_store = text.index("Upload the exact IPA once")
    ios_transport = text.index("Checkpoint accepted Apple upload transport")
    ios_readback = text.index("Read back internal TestFlight state")
    claim = text.index("Reserve this version/build before either signed binary is created")
    android_unique = text.index("Reject a reused Google Play version before compiling or signing")
    android_build = text.index("Build and sign the only Android Store candidate")
    ios_unique = text.index("Reject a reused App Store build number before compiling or signing")
    ios_build = text.index("Create the only signed iOS archive and exported IPA")
    if not (
        claim < android_build
        and claim < ios_build
        and android_unique < android_build
        and ios_unique < ios_build
        and
        android_checkpoint < android_intent < android_store
        and ios_checkpoint < ios_intent < ios_store < ios_transport < ios_readback
    ):
        fail("candidate durable checkpoints are not ordered before their Store mutations/readback")


def verify_external_receipt_attestations(text: str) -> None:
    for receipt in ("external-android-receipt.json", "external-ios-receipt.json"):
        marker = f"subject-path: build/release-promotion/{receipt}"
        if marker not in text:
            fail(f"external-testing workflow does not attest {receipt}")
    if "Require actual external TestFlight availability" not in text or "available_to_external_testers" not in text:
        fail("external-testing workflow can succeed before the Apple build is externally available")
    for token in (
        "Seal one successful external-testing evidence record",
        "create-external-evidence",
        "parlor-external-evidence-",
        "subject-path: build/release-promotion/external-evidence.json",
    ):
        if token not in text:
            fail(f"external-testing workflow cannot safely aggregate partial rerun receipts: {token!r}")


def verify_policy() -> None:
    policy = json.loads((ROOT / "config" / "release-policy.json").read_text(encoding="utf-8"))
    android = policy["applications"]["android"]
    ios = policy["applications"]["ios"]
    android_identity_pattern = re.compile(r"^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$")
    apple_identity_pattern = re.compile(r"^[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$")
    if not android_identity_pattern.fullmatch(str(android["store_application_id"])):
        fail("release policy has an invalid Android Store identity")
    if not apple_identity_pattern.fullmatch(str(ios["store_bundle_id"])):
        fail("release policy has an invalid iOS Store identity")
    managed_device = policy.get("toolchains", {}).get("android_managed_device")
    expected_managed_device = {
        "device": "Pixel 2",
        "api_level": 35,
        "system_image_source": "google",
        "system_image_package": "system-images;android-35;google_apis;x86_64",
        "system_image_revision": "9",
        "abi": "x86_64",
    }
    if managed_device != expected_managed_device:
        fail("release policy does not pin the reviewed Android managed device")
    if android["debug_application_id"] == android["store_application_id"]:
        fail("Android Debug identity is not isolated")
    if ios["debug_bundle_id"] == ios["store_bundle_id"]:
        fail("iOS Debug identity is not isolated")
    for platform, application in (("android", android), ("ios", ios)):
        approval = application.get("store_identity_ownership")
        expected_fields = {"status", "reason", "verified_at", "verification_reference"}
        if not isinstance(approval, dict) or set(approval) != expected_fields:
            fail(f"{platform} Store identity ownership approval is malformed")
        if approval["status"] not in {"blocked", "verified"}:
            fail(f"{platform} Store identity ownership status is invalid")
        if approval["status"] == "blocked":
            if not isinstance(approval["reason"], str) or not approval["reason"]:
                fail(f"{platform} blocked Store identity lacks a reason")
            if approval["verified_at"] is not None or approval["verification_reference"] is not None:
                fail(f"{platform} blocked Store identity contains false verification evidence")
        else:
            if approval["reason"] is not None:
                fail(f"{platform} verified Store identity retains a blocking reason")
            if not isinstance(approval["verified_at"], str) or not approval["verified_at"]:
                fail(f"{platform} verified Store identity lacks a timestamp")
            reference = approval["verification_reference"]
            if not isinstance(reference, str) or not reference or len(reference) > 512:
                fail(f"{platform} verified Store identity lacks bounded evidence")
    # Both public Stores currently assign this reverse-DNS identifier to an
    # unrelated application. It may never be approved by changing only a flag.
    if android["store_application_id"] == "com.parlor.app" and android["store_identity_ownership"]["status"] != "blocked":
        fail("known-colliding Android Store identity was marked verified")
    if ios["store_bundle_id"] == "com.parlor.app" and ios["store_identity_ownership"]["status"] != "blocked":
        fail("known-colliding iOS Store identity was marked verified")
    manifest_schema = json.loads((ROOT / "config/candidate-manifest.schema.json").read_text(encoding="utf-8"))
    manifest_applications = manifest_schema["properties"]["applications"]["properties"]
    google_receipt = manifest_schema["$defs"]["googleReceipt"]["allOf"][1]["properties"]
    apple_receipt = manifest_schema["$defs"]["appleReceipt"]["allOf"][1]["properties"]
    if manifest_applications["android_application_id"].get("const") != android["store_application_id"]:
        fail("candidate manifest schema Android identity drifted from release policy")
    if manifest_applications["ios_bundle_id"].get("const") != ios["store_bundle_id"]:
        fail("candidate manifest schema iOS identity drifted from release policy")
    if google_receipt["package_name"].get("const") != android["store_application_id"]:
        fail("candidate manifest Google receipt identity drifted from release policy")
    if apple_receipt["bundle_id"].get("const") != ios["store_bundle_id"]:
        fail("candidate manifest Apple receipt identity drifted from release policy")
    expected_environments = {
        "candidate_control": "testing-candidate",
        "candidate_android": "testing-android",
        "candidate_ios": "testing-ios",
        "external_android": "external-testing-android",
        "external_ios": "external-testing-ios",
        "production_android": "production-android",
        "production_ios": "production-ios",
    }
    if policy.get("github", {}).get("environments") != expected_environments:
        fail("protected release environments differ from the reviewed authorization model")
    apple_toolchain = policy.get("toolchains", {}).get("apple", {})
    if apple_toolchain != {
        "xcode_version": "26.3",
        "xcode_build": "17C529",
        "developer_dir": "/Applications/Xcode_26.3.app/Contents/Developer",
        "minimum_ios_sdk_major": 26,
        "deployment_target": "16.0",
    }:
        fail("Apple Store toolchain policy differs from the reviewed Xcode/SDK/deployment contract")
    bundletool = policy["tools"]["bundletool"]
    if not bundletool["url"].startswith("https://github.com/google/bundletool/releases/download/"):
        fail("bundletool URL is not the official release location")
    if not re.fullmatch(r"[0-9a-f]{64}", bundletool["sha256"]):
        fail("bundletool is not pinned by SHA-256")
    expected_platforms = {"linux-amd64", "linux-arm64", "darwin-amd64", "darwin-arm64"}
    for tool in ("actionlint", "shellcheck"):
        artifacts = policy["tools"][tool].get("artifacts", {})
        if set(artifacts) != expected_platforms:
            fail(f"{tool} does not pin every supported CI host architecture")
        for platform, release in artifacts.items():
            owner_path = "rhysd/actionlint" if tool == "actionlint" else "koalaman/shellcheck"
            expected_prefix = f"https://github.com/{owner_path}/releases/download/"
            if not str(release.get("url", "")).startswith(expected_prefix):
                fail(f"{tool} {platform} URL is not an official HTTPS GitHub release")
            if not re.fullmatch(r"[0-9a-f]{64}", str(release.get("sha256", ""))):
                fail(f"{tool} {platform} is not pinned by SHA-256")
    expected_limits = {
        "control_artifact_bytes": 1_048_576,
        "android_artifact_bytes": 536_870_912,
        "ios_artifact_bytes": 2_147_483_648,
        "github_binary_archive_bytes": 2_147_483_648,
        "archive_entry_count": 100_000,
    }
    if policy.get("limits") != expected_limits:
        fail("release artifact safety limits differ from the reviewed policy")


def verify_tool_downloader(script: str) -> None:
    if re.search(r"\|\|\s+return(?:\s|$)", script):
        fail("release-tool download/extraction can lose the failing command status")
    if script.count("return 2") != 3:
        fail("release-tool download/extraction can lose the failing command status")
    for token in (
        "could not download pinned $tool release",
        "pinned $tool release digest mismatch",
        "could not safely extract pinned $tool release",
    ):
        if token not in script:
            fail(f"release-tool downloader is not fail-closed: {token!r}")


def verify_review_inventory_gate(script: str) -> None:
    command = "python3 scripts/generate_review_inventory.py --check"
    if script.count(command) != 1:
        fail("release-system validation does not enforce review-inventory freshness exactly once")


def verify_signing_scripts() -> None:
    build = (ROOT / "scripts" / "release" / "build_ios_candidate.sh").read_text(encoding="utf-8")
    validator = (ROOT / "scripts" / "release" / "validate_ios_artifact.sh").read_text(encoding="utf-8")
    store_client = (ROOT / "scripts" / "release" / "store_api.py").read_text(encoding="utf-8")
    release_tool = (ROOT / "scripts" / "release" / "release_tool.py").read_text(encoding="utf-8")
    release_system_validator = (
        ROOT / "scripts" / "release" / "validate_release_system.sh"
    ).read_text(encoding="utf-8")
    verify_tool_downloader(release_system_validator)
    verify_review_inventory_gate(release_system_validator)
    for name, script in (
        ("build_ios_candidate.sh", build),
        (
            "upload_ios_candidate.sh",
            (ROOT / "scripts" / "release" / "upload_ios_candidate.sh").read_text(encoding="utf-8"),
        ),
    ):
        if "assert-store-identity-approved --platform ios" not in script:
            fail(f"{name} can use Apple signing/Store credentials before identity ownership approval")
    cleanup_contract = (
        "cleanup_status=0",
        "could not restore the original user keychain search list",
        "could not restore the original default keychain",
        "could not delete the ephemeral signing keychain",
        "trap 'cleanup $?' EXIT",
        "trap 'exit 130' INT",
        "trap 'exit 143' TERM",
        'exit "$cleanup_status"',
    )
    for token in cleanup_contract:
        if token not in build:
            fail(f"iOS signing cleanup is not fail-closed: {token!r}")
    for token in (
        "unset PARLOR_APPLE_CERTIFICATE_PASSWORD",
        "unset PARLOR_APPLE_CERTIFICATE_P12_PATH",
        "unset PARLOR_APPLE_PROFILE_PATH",
    ):
        if token not in build:
            fail(f"iOS signing source secret remains visible to Xcode: {token!r}")
    validator_contract = (
        '"toolchains"]["apple"]["xcode_build"',
        '"toolchains"]["apple"]["minimum_ios_sdk_major"',
        '"toolchains"]["apple"]["deployment_target"',
        "DTXcodeBuild",
        "DTSDKName",
        "MinimumOSVersion",
        '"xcrun", "vtool", "-show-build"',
        'platform != "IOS"',
        "macho_minimum_os_versions",
    )
    for token in validator_contract:
        if token not in validator:
            fail(f"iOS artifact validation lacks the release-toolchain contract: {token!r}")
    for token in (
        "require_internal_candidate_readback",
        "store_sha256_readback",
        "store_bundle_sha256",
        "Google Play reported a SHA-256 that differs from the uploaded candidate",
    ):
        if token not in store_client and token not in release_tool:
            fail(f"Google Play partial-success recovery lacks exact digest evidence: {token!r}")
    if 'urllib.parse.urlencode({"filter[builds]": build_id, "limit": "200"})' not in store_client:
        fail("App Store Connect beta-group readback does not use the supported build filter")
    if '/builds/{quote(build_id)}/betaGroups' in store_client:
        fail("App Store Connect client uses a nonexistent build beta-groups endpoint")
    if '/betaGroups/{quote(group_id)}/relationships/app' not in store_client:
        fail("App Store Connect beta-group validation lacks authoritative app linkage readback")
    for token in (
        "validate_store_eligible_apple_build",
        "APP_STORE_ELIGIBLE",
        "usesNonExemptEncryption",
        'attributes.get("expired") is not False',
    ):
        if token not in store_client:
            fail(f"App Store Connect build eligibility readback is incomplete: {token!r}")
    for name in (
        "upload_ios_candidate.sh",
        "validate_android_artifact.sh",
        "validate_ios_artifact.sh",
        "validate_release_system.sh",
    ):
        script = (ROOT / "scripts" / "release" / name).read_text(encoding="utf-8")
        for token in ("trap 'cleanup $?' EXIT", "trap 'exit 130' INT", "trap 'exit 143' TERM"):
            if token not in script:
                fail(f"{name} does not preserve exit/signal status during cleanup: {token!r}")
    upload = (ROOT / "scripts" / "release" / "upload_ios_candidate.sh").read_text(encoding="utf-8")
    if 'export API_PRIVATE_KEYS_DIR="$temporary_dir/private_keys"' not in upload or "export HOME=" in upload:
        fail("Apple upload does not use the supported scoped API-key directory")
    if 'rm -f "$raw_log"' not in upload:
        fail("Apple upload can retain its raw response after completion")


def verify_android_runtime_script() -> None:
    script = (ROOT / "scripts" / "android" / "run_release_managed_device_smoke.sh").read_text(
        encoding="utf-8"
    )
    required = (
        "productionAndroidRuntimeCheck",
        "-Pparlor.androidRuntimeTestVariant=release",
        "productionAndroidNavigationRuntimeCheck",
        "-Pparlor.androidRuntimeTestVariant=debug",
        '"${navigation_command[@]}"',
        "android-navigation-device-supervision.json",
        "--dependency-verification=strict",
        "--no-daemon",
        "--max-workers=2",
        "android.injected.signing.store.file",
        "android.injected.signing.store.password",
        "android.injected.signing.key.alias",
        "android.injected.signing.key.password",
        "trap 'cleanup $?' EXIT",
        "trap 'exit 130' INT",
        "trap 'exit 143' TERM",
        '"$repo_root/gradlew" --stop',
    )
    for token in required:
        if token not in script:
            fail(f"Android managed-device runner is missing {token!r}")
    for production_secret in (
        "PARLOR_ANDROID_KEYSTORE_PATH",
        "PARLOR_ANDROID_KEYSTORE_PASSWORD",
        "PARLOR_ANDROID_KEY_ALIAS",
        "PARLOR_ANDROID_KEY_PASSWORD",
    ):
        if production_secret in script:
            fail("Android managed-device runner must not consume production signing material")


def verify_github_distribution(text: str) -> None:
    """Separate protected GitHub channel; never a back door into Store delivery."""
    jobs = re.findall(r"(?m)^  ([a-z][a-z-]*):\n    name:", text)
    if jobs != ["preflight", "build", "seal", "publish"]:
        fail("GitHub distribution requires exactly its four reviewed jobs")
    for token in (
        "branches: [feat/last-light]", "tags: ['github-v*-b*']",
        "default: rehearsal", "options: [rehearsal, candidate, publish]",
        "  group: github-distribution\n  cancel-in-progress: false",
    ):
        if text.count(token) != 1:
            fail(f"GitHub distribution trigger/serialization policy differs: {token}")
    if text.count("contents: write") != 1 or "secrets: inherit" in text or "actions: write" in text:
        fail("GitHub distribution permissions are excessive")
    if "overwrite: true" in text or "continue-on-error" in text or "workflow_run:" in text or "pull_request:" in text:
        fail("GitHub distribution cannot overwrite custody or trust pull-request/run triggers")
    for name in jobs:
        job = validation_job(text, name)
        if "timeout-minutes:" not in job or "persist-credentials: false" not in job or "fetch-depth: 0" not in job:
            fail("GitHub distribution jobs need bounded clean-source checkouts")
        if name != "publish" and "contents: write" in job:
            fail("GitHub distribution allows publication authority only in the publish job")
    preflight = validation_job(text, "preflight")
    if "scripts/release/workflow_contract.py" not in preflight or "scripts.release.tests.test_github_distribution" not in preflight:
        fail("GitHub distribution must test release tools before any native build")
    build = validation_job(text, "build")
    required = (
        "    needs: preflight\n", "    if: needs.preflight.outputs.mode != 'publish'\n",
        "      fail-fast: false\n", "      PYTHONPATH: ${{ github.workspace }}\n",
        "    environment: ${{ needs.preflight.outputs.mode == 'candidate' && matrix.environment || 'github-rehearsal' }}\n",
        "GH_DIST_APPROVED_SHA: ${{ vars.GH_DIST_APPROVED_SHA }}", "GH_DIST_ACCEPTANCE_SHA: ${{ vars.GH_DIST_ACCEPTANCE_SHA }}",
        "GH_DIST_ACCEPTANCE_REFERENCE: ${{ vars.GH_DIST_ACCEPTANCE_REFERENCE }}",
        "distribution: temurin", "java-version: '21'", "check-latest: false",
        "assert_new(os.environ['GH_DIST_PLATFORM'])", "build(os.environ['GH_DIST_PLATFORM'])",
        "freeze(os.environ['GH_DIST_PLATFORM'], False)", "subject-path: build/github-distribution/frozen/*",
        "          path: build/github-distribution/frozen/*\n", "overwrite: false", "if-no-files-found: error",
        "if: always() && needs.preflight.outputs.mode == 'candidate'",
        "cleanup_candidate(os.environ['GH_DIST_PLATFORM'])",
    )
    for token in required:
        if token not in build:
            fail(f"GitHub distribution build/sign/freeze policy lacks {token!r}")
    variants = re.findall(r"(?m)^          - platform: (.+)\n            runner: (.+)\n            environment: (.+)$", build)
    if variants != [("android", "ubuntu-24.04", "github-sign-android"), ("macos-arm64", "macos-15", "github-sign-macos"),
                    ("macos-x64", "macos-15-intel", "github-sign-macos"), ("windows-x64", "windows-2025", "github-sign-windows"),
                    ("linux-x64", "ubuntu-24.04", "github-sign-linux")]:
        fail("GitHub distribution native matrix or signing scope differs")
    signing = (
        ("Sign Android candidate without Store upload", "matrix.platform == 'android'", "ANDROID"),
        ("Sign notarize and validate macOS candidate", "startsWith(matrix.platform, 'macos-')", "MACOS"),
        ("Sign timestamp and validate Windows candidate", "matrix.platform == 'windows-x64'", "WINDOWS"),
    )
    signing_blocks = []
    for name, condition, family in signing:
        block = validation_step(build, name)
        if re.findall(r"(?m)^        if: (.*)$", block) != ["needs.preflight.outputs.mode == 'candidate' && " + condition]:
            fail("GitHub distribution must scope signing credentials to the protected candidate and platform")
        if f"secrets.GH_DIST_{family}_" not in block or "from scripts.release.sign_distribution import sign" not in block:
            fail("GitHub distribution signing step is incomplete")
        signing_blocks.append(block)
    remainder = text
    for block in signing_blocks:
        remainder = remainder.replace(block, "")
    if "secrets." in remainder:
        fail("GitHub distribution secrets cannot reach build, rehearsal, seal, or publication")
    sequence = ["Refuse rebuilding an already frozen platform", "Build test and inspect unsigned release packages",
                *[item[0] for item in signing], "Validate Linux candidate provenance", "Freeze exact inspected platform bytes",
                "Attest platform artifact and descriptor", "Upload immutable platform candidate", "Retire scoped signing and unsigned outputs"]
    positions = [build.index(f"- name: {name}\n") for name in sequence]
    if positions != sorted(positions):
        fail("GitHub distribution signing/freeze/custody order differs")
    seal = validation_job(text, "seal")
    for token in ("needs: [preflight, build]", "if: needs.preflight.outputs.mode != 'publish'",
                  "python3 -m scripts.release.github_distribution seal", "subject-path: build/github-distribution/bundle/*",
                  "path: build/github-distribution/bundle/*", "overwrite: false", "if-no-files-found: error"):
        if token not in seal:
            fail("GitHub distribution must seal and attest all successfully built platforms")
    publication = validation_job(text, "publish")
    for token in ("needs: preflight", "if: needs.preflight.outputs.mode == 'publish'", "environment: github-publish",
                  "contents: write", "attestations: read", "GH_DIST_APPROVED_SHA: ${{ vars.GH_DIST_APPROVED_SHA }}",
                  "GH_DIST_ACCEPTANCE_SHA: ${{ vars.GH_DIST_ACCEPTANCE_SHA }}", "GH_DIST_ANDROID_CERT_SHA256:",
                  "GH_DIST_MACOS_CERT_SHA256:", "GH_DIST_MACOS_TEAM_ID:", "GH_DIST_WINDOWS_CERT_SHA256:",
                  'python3 -m scripts.release.github_distribution publish --candidate "$GH_DIST_CANDIDATE_RUN"'):
        if token not in publication:
            fail("GitHub distribution publication must consume an approved frozen signed candidate")
    if any(word in publication for word in ("gradlew", "setup-java", "notarytool", "signtool", "desktop_package", "sign_distribution")):
        fail("GitHub distribution publication cannot rebuild or re-sign")


def verify_public_testing(text: str) -> None:
    """The owner's opt-in testing channel must never become a signed-release fallback."""
    verify_common("github-test-release.yml", text)
    required = (
        "name: GitHub public testing", "on:\n  workflow_dispatch:", "default: build", "options: [build, publish]",
        "acknowledge_test_limits:", "default: false", "group: github-distribution", "cancel-in-progress: false",
        "permissions:\n  contents: read\n", "GH_TEST_INPUT_MODE: ${{ inputs.mode }}",
        "GH_TEST_INPUT_RUN: ${{ inputs.test_run }}", "GH_TEST_PUBLISH_ACK: ${{ inputs.acknowledge_test_limits }}",
    )
    for token in required:
        if token not in text:
            fail(f"Public testing workflow lacks {token!r}")
    if (re.search(r"(?m)^  (pull_request|pull_request_target|workflow_run|schedule):", text)
            or re.search(r"\bsecrets\b", text) or "write-all" in text or "overwrite: true" in text):
        fail("Public testing must be explicit, credential-free and immutable")
    pushes = re.findall(r"(?m)^  push:\n(?:    .*\n)*", text)
    if pushes != ["  push:\n    branches: [feat/last-light]\n    paths: [.github/workflows/github-test-release.yml]\n"]:
        fail("Only the narrow read-only feature-branch registration push is allowed")
    jobs = set(re.findall(r"(?m)^  ([a-z][a-z-]+):\n", text.split("\njobs:\n", 1)[1]))
    if jobs != {"register", "preflight", "build", "seal", "publish"}:
        fail("Unexpected public testing jobs")
    registration = """    name: Register testing workflow without builds or publication
    if: github.event_name == 'push'
    runs-on: ubuntu-24.04
    timeout-minutes: 2
    permissions: {}
    steps:
      - name: Register without repository access
        run: echo 'Registration only; use an explicit workflow dispatch.'"""
    if validation_job(text, "register").strip() != registration.strip():
        fail("Workflow registration must not check out code, build, publish or grant repository permissions")
    expected_permissions = {
        "preflight": {"contents": "read", "actions": "read"},
        "build": {"contents": "read", "actions": "read", "id-token": "write", "attestations": "write"},
        "seal": {"contents": "read", "actions": "read", "id-token": "write", "attestations": "write"},
        "publish": {"contents": "write", "actions": "read", "attestations": "read"},
    }
    for name, expected in expected_permissions.items():
        job = validation_job(text, name)
        permissions = re.findall(r"(?m)^    permissions:\n((?:      [a-z-]+: [^\n]+\n)+)", job)
        actual = re.findall(r"(?m)^      ([a-z-]+): (.*)$", permissions[0]) if len(permissions) == 1 else []
        if len(actual) != len(expected) or dict(actual) != expected:
            fail("Public testing may grant release writes only to its dedicated publisher")
        if "timeout-minutes:" not in job or "fetch-depth: 0" not in job or "persist-credentials: false" not in job:
            fail("Public testing requires bounded exact-source, credential-free checkouts")
    preflight = validation_job(text, "preflight")
    for token in ("if: github.event_name == 'workflow_dispatch'",
                  "scripts.release.tests.test_github_test_release", "scripts.release.tests.test_android_test_package",
                  "scripts.release.tests.test_test_release_workflow", "python3 scripts/release/workflow_contract.py",
                  "python3 -m scripts.release.github_test_release preflight"):
        if token not in preflight:
            fail("Public testing must validate its source/operation and adversarial contracts before builds")
    build = validation_job(text, "build")
    matrix = re.findall(r"- platform: ([a-z0-9-]+)\n            runner: ([a-z0-9.-]+)", build)
    if matrix != [("android", "ubuntu-24.04"), ("macos-arm64", "macos-15"), ("macos-x64", "macos-15-intel"),
                  ("windows-x64", "windows-2025"), ("linux-x64", "ubuntu-24.04")]:
        fail("Public testing needs every native platform on its reviewed host")
    for token in ("needs: preflight", "if: needs.preflight.outputs.mode == 'build'", "fail-fast: false",
                  "distribution: temurin", "java-version: '21'", "check-latest: false",
                  "      PYTHONPATH: ${{ github.workspace }}\n",
                  "assert_new(os.environ['GH_TEST_PLATFORM'])", "build(os.environ['GH_TEST_PLATFORM'])",
                  "subject-path: build/github-test-release/frozen/*", "path: build/github-test-release/frozen/*",
                  "if-no-files-found: error", "overwrite: false", "test -c /dev/kvm",
                  "system-images;android-35;google_apis;x86_64", "core.longpaths"):
        if token not in build:
            fail(f"Public testing build policy lacks {token!r}")
    ordered = ("Refuse rebuilding frozen test artifacts", "Build inspect and freeze test installers",
               "Attest test artifact and descriptor", "Upload frozen test artifact")
    positions = [build.find("- name: " + name) for name in ordered]
    if min(positions) < 0 or positions != sorted(positions):
        fail("Public testing must build once, inspect, freeze, attest, then upload")
    seal = validation_job(text, "seal")
    for token in ("name: Seal public test bundle", "needs: [preflight, build]", "if: needs.preflight.outputs.mode == 'build'",
                  "python3 -m scripts.release.github_test_release seal", "subject-path: build/github-test-release/bundle/*",
                  "path: build/github-test-release/bundle/*", "if-no-files-found: error", "overwrite: false"):
        if token not in seal:
            fail("Public testing must seal and attest every platform before publication")
    publication = validation_job(text, "publish")
    for token in ("needs: preflight", "if: needs.preflight.outputs.mode == 'publish'",
                  'python3 -m scripts.release.github_test_release publish --run "$GH_TEST_INPUT_RUN"'):
        if token not in publication:
            fail("Public testing publication must consume the explicitly selected frozen bundle")
    if any(word in publication for word in ("gradlew", "setup-java", "notarytool", "signtool", "desktop_package", "sign_distribution")):
        fail("Public testing publication cannot rebuild, re-sign or call a production signer")


def main() -> int:
    files = load_files()
    verify_policy()
    verify_signing_scripts()
    verify_android_runtime_script()
    for name, text in files.items():
        verify_common(name, text)
    verify_validation(files["production-verification.yml"])
    verify_input_investigation(files["ios-input-investigation.yml"])
    if PUBLIC_TEXT_REVIEW in files:
        verify_public_text_review(files[PUBLIC_TEXT_REVIEW])
    if PUBLIC_BACK_REVIEW in files:
        verify_public_back_review(files[PUBLIC_BACK_REVIEW])
    if PUBLIC_CATALOG_REVIEW in files:
        verify_public_catalog_review(files[PUBLIC_CATALOG_REVIEW])
    if SETTLED_FAILURE_REVIEW in files:
        verify_settled_failure_review(files[SETTLED_FAILURE_REVIEW])
    if PUBLIC_SETUP_PAIR_REVIEW in files:
        verify_public_setup_pair_review(files[PUBLIC_SETUP_PAIR_REVIEW])
    if NATIVE_ACCESSIBILITY_CAPABILITY in files:
        verify_native_accessibility_capability(files[NATIVE_ACCESSIBILITY_CAPABILITY])
    if NATIVE_BOUNDARY_OBSERVATIONS in files:
        verify_native_boundary_observations(files[NATIVE_BOUNDARY_OBSERVATIONS])
    if NATIVE_ROOT_CAPABILITY in files:
        verify_native_root_capability(files[NATIVE_ROOT_CAPABILITY])
    if NATIVE_ROOT_OBSERVATIONS in files:
        verify_native_root_observations(files[NATIVE_ROOT_OBSERVATIONS])
    if PUBLIC_SESSION_STRESS in files:
        verify_public_session_stress(files[PUBLIC_SESSION_STRESS])
    if CATALOG_INPUT_BOUNDARY in files:
        verify_catalog_input_boundary(files[CATALOG_INPUT_BOUNDARY])
    if OVERSCROLL_GETTER_BOUNDARY in files:
        verify_overscroll_getter_boundary(files[OVERSCROLL_GETTER_BOUNDARY])
    if ORIGINAL_TOUCH_DELIVERY in files:
        verify_original_touch_delivery(files[ORIGINAL_TOUCH_DELIVERY])
    if ORIGINAL_TOUCH_EXPORT_CONTROL in files:
        verify_original_touch_export_control(files[ORIGINAL_TOUCH_EXPORT_CONTROL])
    if OWNED_LEAF_PAIR in files:
        verify_owned_leaf_pair(files[OWNED_LEAF_PAIR])
    if OWNED_LEAF_EXPORT in files:
        verify_owned_leaf_export(files[OWNED_LEAF_EXPORT])
    if ORIGINAL_INPUT_ONLY in files:
        verify_original_input_only(files[ORIGINAL_INPUT_ONLY])
    if NATIVE_ONLY_SAMPLES in files:
        verify_native_only_samples(files[NATIVE_ONLY_SAMPLES])
    if NATIVE_ONLY_EXPORT in files:
        verify_native_only_export(files[NATIVE_ONLY_EXPORT])
    if PUBLIC_SCROLL_SAMPLES in files:
        verify_public_scroll_samples(files[PUBLIC_SCROLL_SAMPLES])
    if PUBLIC_SCROLL_ORDER_CORRECTION in files:
        verify_public_scroll_order_correction(files[PUBLIC_SCROLL_ORDER_CORRECTION])
    if PUBLIC_SCROLL_NATURAL in files:
        verify_public_scroll_natural(files[PUBLIC_SCROLL_NATURAL])
    if PUBLIC_SCROLL_EXPORT in files:
        verify_public_scroll_export(files[PUBLIC_SCROLL_EXPORT])
    if ORIGINAL_TOUCH_COHORT in files:
        verify_original_touch_cohort(files[ORIGINAL_TOUCH_COHORT])
    if PUBLIC_FAILURE_COVERAGE_CONTROL in files:
        verify_public_failure_coverage_control(files[PUBLIC_FAILURE_COVERAGE_CONTROL])
    if CURRENT_INPUT_BOUNDARIES in files:
        verify_current_input_boundaries(files[CURRENT_INPUT_BOUNDARIES])
    if SYNTHETIC_FAILURE_EXPORT in files:
        verify_synthetic_failure_export(files[SYNTHETIC_FAILURE_EXPORT])
    if PUBLIC_SETUP_BOUNDARIES in files:
        verify_public_setup_boundaries(files[PUBLIC_SETUP_BOUNDARIES])
    for name in STORE_WORKFLOWS:
        verify_store_workflow(name, files[name])
    verify_store_serialization(files)
    for name in PROMOTION_WORKFLOWS:
        verify_promotions(name, files[name])
    verify_candidate(files["testing-candidate.yml"])
    verify_external_receipt_attestations(files["testing-external-promotion.yml"])
    verify_production(files["production-promotion.yml"])
    verify_github_distribution(files["github-distribution.yml"])
    verify_public_testing(files["github-test-release.yml"])
    print("release workflow contract: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"release workflow contract: FAIL: {error}", file=sys.stderr)
        raise SystemExit(2)
