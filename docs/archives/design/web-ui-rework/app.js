(function () {
  'use strict';

  var state = {
    route: 'home',
    game: 'whodunit',
    topology: 'peer',
    theme: 'dark',
    direction: 'ltr',
    reducedMotion: false,
    language: 'system',
    caseFilter: 'all',
    caseId: 'last-dinner',
    mode: 'classic',
    lobbyDecision: 'pending',
    codeCopied: false,
    joinConnected: false,
    whodunitStage: 'clue',
    round: 3,
    discussionPaused: false,
    whodunitVoteTarget: null,
    mafiaStage: 'cover',
    mafiaTarget: null,
    mafiaCounts: {
      mafia: 2,
      detective: 1,
      doctor: 1
    }
  };

  var cases = {
    'last-dinner': {
      title: 'The Last Dinner',
      language: 'en',
      copy: 'A storm, a poisoned brandy, and six guests trapped in a country manor until morning.'
    },
    'zamalek-ramadan': {
      title: 'ليلة رمضان في الزمالك',
      language: 'ar',
      copy: 'جريمة قتل على مائدة إفطار في برج بالزمالك.'
    },
    'khan-el-khalili': {
      title: 'ليلة الخان',
      language: 'ar',
      copy: 'جريمة قتل في ديوان تاجر أنتيكات بخان الخليلي.'
    },
    'jasmine-ring': {
      title: 'خاتم الياسمين',
      language: 'ar',
      copy: 'موت تاجر الأنتيكا في حارة الياسمين.'
    },
    'iskenderia-corniche': {
      title: 'صيف الإسكندرية',
      language: 'ar',
      copy: 'جريمة قتل في فيلا على كورنيش الإسكندرية.'
    },
    'layla-halabi': {
      title: 'ليلة العاصفة في بيت الحلبي',
      language: 'ar',
      copy: 'جريمة قتل في قاعة دمشقية قديمة.'
    },
    'saidi-inheritance': {
      title: 'حصاد الصعيد',
      language: 'ar',
      copy: 'جريمة قتل في دار عُمدة بصعيد مصر.'
    }
  };

  var validRoutes = [
    'home', 'setup', 'cases', 'mafia-setup', 'host', 'join',
    'recovery', 'whodunit', 'mafia', 'settings'
  ];
  var initialParameters = new URLSearchParams(window.location.search);
  var requestedRoute = initialParameters.get('screen');
  var requestedGame = initialParameters.get('game');
  if (validRoutes.indexOf(requestedRoute) >= 0) {
    state.route = requestedRoute;
  }
  if (requestedGame === 'mafia' || state.route === 'mafia' || state.route === 'mafia-setup') {
    state.game = 'mafia';
  }

  var routeNotes = {
    home: {
      kicker: 'SCREEN 01 · FOUNDATION',
      title: 'A game-first front door',
      summary: 'Resume is visible without overpowering discovery. Each game has its own mood, player range, and promise before the player chooses a topology.',
      points: [
        'Large game cards replace generic navigation with clear invitations.',
        'Local recovery is labeled by game, round, and device mode.',
        'No account, public lobby, or internet-play affordance is implied.'
      ],
      tags: ['HomeScreen', 'GameShellRegistry', 'SnapshotStore'],
      guardrail: 'Only recovery metadata appears here. No dossier, role, room secret, or another player’s private state is previewed.'
    },
    setup: {
      kicker: 'SCREEN 02 · ENTRY MODEL',
      title: 'Choose by table shape',
      summary: 'The setup decision is framed around one shared device versus one device per person, matching how players naturally organize a party game.',
      points: [
        'Pass & Play remains a first-class path with explicit privacy hand-offs.',
        'Host and Join stay separate because their transport responsibilities differ.',
        'Unsupported Solo is explained once instead of consuming a hero card.'
      ],
      tags: ['PlayModePickerScreen', 'GameShellCapabilities', 'PlayMode'],
      guardrail: 'This changes presentation only. It does not add Solo, matchmaking, raw-IP entry, spectators, or host migration.'
    },
    cases: {
      kicker: 'SCREEN 03 · WHODUNIT',
      title: 'A bilingual story shelf',
      summary: 'Language is visible before selection, and the seven bundled stories feel like authored cases rather than rows in a settings list.',
      points: [
        'English and Arabic filters address the mixed-language library directly.',
        'Player count, duration, offline status, and supported modes stay visible.',
        'RTL titles retain their own reading direction inside an LTR interface.'
      ],
      tags: ['WhodunitCasePickerScreen', 'BundledWhodunitCatalog', 'CaseRepository'],
      guardrail: 'The prototype uses only bundled catalog metadata. Production play remains offline and validates every case before starting.'
    },
    'mafia-setup': {
      kicker: 'SCREEN 03B · MAFIA',
      title: 'Rules you can audit at a glance',
      summary: 'Role balance is visual, but every control remains explicit and textual. The setup avoids suggesting unsupported timed rounds.',
      points: [
        'The 5–16 player rule remains the outer constraint.',
        'Mafia minority and at-least-one-Civilian requirements stay visible.',
        'Detective and Doctor controls cap at one, matching shipping rules.'
      ],
      tags: ['MafiaSetupScreen', 'MafiaSettings', 'MafiaSessionRules'],
      guardrail: 'Validation still belongs to the reducer and settings policy; a polished button state is never a rules boundary.'
    },
    host: {
      kicker: 'SCREEN 04 · MULTIPLAYER',
      title: 'The host sees authority clearly',
      summary: 'Room identity, local-network scope, seat ownership, pending approval, and the start gate are presented as one operational surface.',
      points: [
        'The six-character code is prominent without claiming internet reach.',
        'Pending connections are distinct from admitted, ready seats.',
        'Start remains gated while approval or player-count requirements are unresolved.'
      ],
      tags: ['HostLobbyFlow', 'RoomTransport', 'SessionStartReady'],
      guardrail: 'The host remains the only reducer owner. A display name is a label, while transport-bound PlayerId owns the seat.'
    },
    join: {
      kicker: 'SCREEN 05 · MULTIPLAYER',
      title: 'Join without network jargon',
      summary: 'The peer supplies only a display name and room code. Copy explains local discovery and host approval before connecting.',
      points: [
        'Input format is visible and compact.',
        'Waiting state never implies admission before host approval.',
        'Local Wi-Fi and no-location wording sets honest expectations.'
      ],
      tags: ['NameInputScreen', 'JoinPromptScreen', 'RoomInputPolicy'],
      guardrail: 'The code discovers a same-app LAN room. It is not an account credential, public matchmaking key, or manual endpoint.'
    },
    recovery: {
      kicker: 'SCREEN 06 · LIFECYCLE',
      title: 'A disconnect becomes a decision',
      summary: 'The host can see why play stopped, what is protected, and what ending without the missing player will do.',
      points: [
        'The disconnected seat remains visibly reserved.',
        'Gameplay-blocked state is separate from network-connected state.',
        'The destructive path states that the game ends and reveals; it never promises fair continuation.'
      ],
      tags: ['HostDisconnectedOverlay', 'ContinueWithoutPlayer', '120s grace'],
      guardrail: 'There is no silent player removal or host migration. Active hidden-role games end if the required seat cannot return.'
    },
    whodunit: {
      kicker: 'SCREEN 07 · PUBLIC GAMEPLAY',
      title: 'Evidence owns the stage',
      summary: 'The host’s public round view makes the new clue readable across a table while keeping progress and controls unmistakable.',
      points: [
        'A four-step rail communicates round position without exposing the killer.',
        'The clue uses a high-contrast paper surface and an explicit read-aloud cue.',
        'Timer controls appear only in Whodunit discussion, where they are implemented.'
      ],
      tags: ['WhodunitPhase.Round', 'ClueCard', 'TimerRibbon'],
      guardrail: 'Only public clues render here. Killer identity, seed, seat map, and individual dossiers never enter this view.'
    },
    mafia: {
      kicker: 'SCREEN 08 · PRIVATE GAMEPLAY',
      title: 'Privacy is a visible ceremony',
      summary: 'A hard cover, named recipient, private context ribbon, and explicit hide action separate every sensitive Mafia moment.',
      points: [
        'Nothing private is present in the cover state.',
        'Role and action screens continually name the intended viewer.',
        'Peer copy states that the host must confirm the action before state changes.'
      ],
      tags: ['MafiaPrivate', 'MafiaHandoffScreens', 'MafiaActionAuthority'],
      guardrail: 'The full role map stays host-only. A peer receives only public state plus that peer’s private slice.'
    },
    settings: {
      kicker: 'SCREEN 09 · PREFERENCES',
      title: 'Small settings, real behavior',
      summary: 'Only shipped preferences appear: language, appearance, and reduced motion. The prototype controls update the design board too.',
      points: [
        'English, Arabic, and system language remain distinct choices.',
        'Light and dark palettes share hierarchy and semantic roles.',
        'Reduced motion removes continuous ornament and shortens transitions.'
      ],
      tags: ['SettingsStore', 'ProvideAppLanguage', 'ParlorTheme'],
      guardrail: 'No placeholder sound, analytics, consent, or cloud control is shown. Settings must correspond to implemented behavior.'
    }
  };

  var phone = document.getElementById('phone-screen');

  function one(selector, root) {
    return (root || document).querySelector(selector);
  }

  function all(selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  }

  function gameLabel() {
    return state.game === 'mafia' ? 'MAFIA' : 'WHODUNIT';
  }

  function templateRoute(route) {
    return route === 'mafia-setup' ? 'mafia-setup' : route;
  }

  function navigate(route, options) {
    options = options || {};
    if (route === 'cases' || route === 'whodunit') {
      state.game = 'whodunit';
    }
    if (route === 'mafia-setup') {
      state.game = 'mafia';
    }
    if (route === 'mafia') {
      state.game = 'mafia';
      if (options.reset !== false) {
        state.mafiaStage = 'cover';
        state.mafiaTarget = null;
      }
    }
    state.route = route;
    var nextUrl = new URL(window.location.href);
    if (route === 'home') {
      nextUrl.search = '';
    } else {
      nextUrl.searchParams.set('screen', route);
      if (route === 'setup' || route === 'host' || route === 'join' || route === 'recovery') {
        nextUrl.searchParams.set('game', state.game);
      } else {
        nextUrl.searchParams.delete('game');
      }
    }
    window.history.replaceState(null, '', nextUrl);
    render(true);
  }

  function render(shouldFocus) {
    var template = document.getElementById('screen-' + templateRoute(state.route));
    if (!template) {
      return;
    }

    phone.setAttribute('aria-busy', 'true');
    phone.replaceChildren(template.content.cloneNode(true));
    phone.scrollTop = 0;
    var contextualRoute = ['setup', 'host', 'join', 'recovery', 'whodunit', 'mafia', 'mafia-setup'].indexOf(state.route) >= 0;
    document.body.dataset.game = contextualRoute && state.game === 'mafia' ? 'mafia' : 'whodunit';

    hydrateCommon();
    if (state.route === 'cases') {
      hydrateCases();
    } else if (state.route === 'host') {
      hydrateLobby();
    } else if (state.route === 'join') {
      hydrateJoin();
    } else if (state.route === 'whodunit') {
      hydrateWhodunit();
    } else if (state.route === 'mafia') {
      hydrateMafia();
    } else if (state.route === 'mafia-setup') {
      hydrateMafiaSetup();
    } else if (state.route === 'settings') {
      hydrateSettings();
    }

    updateScenarioNav();
    updateInspector();
    syncGlobalControls();
    phone.classList.remove('is-changing');
    void phone.offsetWidth;
    phone.classList.add('is-changing');
    phone.setAttribute('aria-busy', 'false');
    if (shouldFocus) {
      var title = one('h1, h2', phone);
      if (title) {
        title.setAttribute('tabindex', '-1');
        title.focus({ preventScroll: true });
      }
    }
  }

  function hydrateCommon() {
    all('[data-game-name]', phone).forEach(function (node) {
      node.textContent = gameLabel();
    });

    var soloCopy = one('[data-solo-copy]', phone);
    if (soloCopy) {
      soloCopy.textContent = state.game === 'mafia'
        ? 'Mafia needs at least five people.'
        : 'This mystery needs a full table of six.';
    }
  }

  function hydrateCases() {
    var selected = cases[state.caseId];
    all('[data-action="case-filter"]', phone).forEach(function (button) {
      var active = button.dataset.filter === state.caseFilter;
      button.classList.toggle('is-selected', active);
      button.setAttribute('aria-pressed', String(active));
    });

    all('[data-action="case-select"]', phone).forEach(function (button) {
      var visible = state.caseFilter === 'all' || button.dataset.language === state.caseFilter;
      button.classList.toggle('is-hidden', !visible);
      button.classList.toggle('is-selected', button.dataset.case === state.caseId);
    });

    var title = one('[data-case-detail-title]', phone);
    var copy = one('[data-case-detail-copy]', phone);
    title.textContent = selected.title;
    title.dir = selected.language === 'ar' ? 'rtl' : 'ltr';
    copy.textContent = selected.copy;
    copy.dir = selected.language === 'ar' ? 'rtl' : 'ltr';

    all('[data-action="mode-select"]', phone).forEach(function (button) {
      var active = button.dataset.mode === state.mode;
      button.classList.toggle('is-selected', active);
      button.setAttribute('aria-pressed', String(active));
    });
    one('[data-action="start-case"]', phone).textContent = state.mode === 'classic'
      ? 'Continue with Classic Vote'
      : 'Continue with Elimination';
  }

  function hydrateLobby() {
    var approved = state.lobbyDecision === 'approved';
    var pending = state.lobbyDecision === 'pending';
    var count = approved ? 6 : 5;
    var required = state.game === 'mafia' ? 5 : 6;
    var ready = !pending && count >= required;

    one('[data-approved-player]', phone).classList.toggle('is-hidden', !approved);
    one('[data-join-request]', phone).classList.toggle('is-hidden', !pending);
    one('[data-request-heading]', phone).classList.toggle('is-hidden', !pending);
    one('[data-request-result]', phone).classList.toggle('is-hidden', pending);
    one('[data-roster-count]', phone).textContent = String(count);
    one('[data-required-count]', phone).textContent = state.game === 'mafia' ? '5 min' : '6';
    one('[data-start-room]', phone).disabled = !ready;
    one('[data-start-room]', phone).textContent = ready
      ? 'Start ' + (state.game === 'mafia' ? 'Mafia' : 'the investigation')
      : 'Start game';

    if (pending) {
      one('[data-readiness-copy]', phone).textContent = 'Review the join request first.';
      one('[data-ready-state]', phone).textContent = 'Pending';
    } else if (ready) {
      one('[data-readiness-copy]', phone).textContent = count + ' admitted players.';
      one('[data-ready-state]', phone).textContent = 'Ready';
    } else {
      one('[data-readiness-copy]', phone).textContent = 'One more player is required.';
      one('[data-ready-state]', phone).textContent = 'Not ready';
    }

    var result = one('[data-request-result]', phone);
    if (result && !pending) {
      result.textContent = approved ? 'Nadia is admitted to seat 6.' : 'Request declined. The connection was closed.';
    }
    one('[data-copy-toast]', phone).classList.toggle('is-hidden', !state.codeCopied);
  }

  function hydrateJoin() {
    one('[data-join-form-view]', phone).classList.toggle('is-hidden', state.joinConnected);
    one('[data-join-waiting]', phone).classList.toggle('is-hidden', !state.joinConnected);
  }

  function hydrateWhodunit() {
    var discussion = state.whodunitStage === 'discussion';
    var voting = state.whodunitStage === 'vote';
    one('[data-clue-view]', phone).classList.toggle('is-hidden', discussion || voting);
    one('[data-discussion-view]', phone).classList.toggle('is-hidden', !discussion);
    one('[data-vote-view]', phone).classList.toggle('is-hidden', !voting);
    var roundFour = state.round === 4;
    one('[data-round-label]', phone).textContent = voting
      ? 'FINAL VOTE · LEILA'
      : roundFour
      ? 'ROUND 4 · FINAL EVIDENCE'
      : 'ROUND 3 · CONTRADICTIONS';
    one('[data-round-title]', phone).textContent = voting
      ? 'Who killed Maxwell?'
      : roundFour
      ? 'One last truth before the vote.'
      : 'Someone’s story doesn’t fit.';
    one('[data-round-clue]', phone).textContent = roundFour
      ? '“A page from the missing letters was found on the pantry counter, beside the decanter.”'
      : '“The pantry door creaked at 8:50. Clara saw a shoulder in dark wool.”';
    var progress = one('[data-round-progress]', phone);
    progress.setAttribute('aria-label', voting ? 'Final vote' : 'Round ' + state.round + ' of 4');
    all('span', progress).forEach(function (segment, index) {
      segment.className = index + 1 < state.round ? 'done' : (index + 1 === state.round ? 'active' : '');
    });
    var advance = one('[data-action="advance-round"]', phone);
    if (advance) {
      advance.textContent = roundFour ? 'Move to final vote' : 'Move to final evidence';
    }
    var context = one('[data-round-context]', phone);
    context.classList.toggle('private-context', voting);
    one('[data-round-context-label]', phone).textContent = voting ? 'PRIVATE BALLOT' : 'PUBLIC SCREEN';
    one('[data-round-context-detail]', phone).textContent = voting ? 'LEILA ONLY' : 'HOST ADVANCES';
    all('[data-action="select-whodunit-vote"]', phone).forEach(function (button) {
      var selected = button.dataset.target === state.whodunitVoteTarget;
      button.classList.toggle('is-selected', selected);
      button.setAttribute('aria-checked', String(selected));
    });
    var voteSubmit = one('[data-submit-whodunit-vote]', phone);
    voteSubmit.disabled = !state.whodunitVoteTarget;
    voteSubmit.textContent = state.whodunitVoteTarget
      ? 'Accuse ' + state.whodunitVoteTarget
      : 'Cast accusation';
    var pause = one('[data-action="pause-discussion"]', phone);
    if (pause) {
      pause.lastChild.textContent = state.discussionPaused ? ' Resume timer' : ' Pause timer';
      var digits = one('.timer-digits', phone);
      digits.textContent = state.discussionPaused ? '02:14' : '02:14';
      digits.style.opacity = state.discussionPaused ? '.55' : '1';
    }
  }

  function hydrateMafia() {
    one('[data-mafia-cover]', phone).classList.toggle('is-hidden', state.mafiaStage !== 'cover');
    one('[data-mafia-role]', phone).classList.toggle('is-hidden', state.mafiaStage !== 'role');
    one('[data-mafia-night]', phone).classList.toggle('is-hidden', state.mafiaStage !== 'night');
    one('[data-mafia-submitted]', phone).classList.toggle('is-hidden', state.mafiaStage !== 'submitted');

    all('[data-action="select-target"]', phone).forEach(function (button) {
      var selected = button.dataset.target === state.mafiaTarget;
      button.classList.toggle('is-selected', selected);
      button.setAttribute('aria-checked', String(selected));
    });
    var submit = one('[data-submit-target]', phone);
    if (submit) {
      submit.disabled = !state.mafiaTarget;
      submit.textContent = state.mafiaTarget ? 'Inspect ' + state.mafiaTarget : 'Inspect player';
    }

    var authorityLabel = one('[data-authority-label]', phone);
    var authorityCopy = one('[data-authority-copy]', phone);
    var submitEyebrow = one('[data-submit-eyebrow]', phone);
    var submitTitle = one('[data-submit-title]', phone);
    var submitCopy = one('[data-submit-copy]', phone);
    if (state.topology === 'local') {
      if (authorityLabel) authorityLabel.textContent = 'LOCAL SESSION';
      if (authorityCopy) authorityCopy.textContent = 'This device submits to the same deterministic reducer, then advances only after acceptance.';
      if (submitEyebrow) submitEyebrow.textContent = 'CHOICE RECORDED';
      if (submitTitle) submitTitle.textContent = 'Hide the screen.';
      if (submitCopy) submitCopy.textContent = 'Your private choice is recorded. Cover the phone before passing it to the next player.';
    } else if (state.topology === 'host') {
      if (authorityLabel) authorityLabel.textContent = 'HOST DEVICE';
      if (authorityCopy) authorityCopy.textContent = 'The host validates this action through the canonical session controller before advancing.';
      if (submitEyebrow) submitEyebrow.textContent = 'ACTION ACCEPTED';
      if (submitTitle) submitTitle.textContent = 'Hide the screen.';
      if (submitCopy) submitCopy.textContent = 'The host reducer accepted the action. No other player can see the private target or result.';
    }
  }

  function hydrateMafiaSetup() {
    var used = state.mafiaCounts.mafia + state.mafiaCounts.detective + state.mafiaCounts.doctor;
    var civilian = Math.max(1, 8 - used);
    all('[data-role-output]', phone).forEach(function (output) {
      var role = output.dataset.roleOutput;
      output.textContent = role === 'civilian' ? String(civilian) : String(state.mafiaCounts[role]);
    });
  }

  function hydrateSettings() {
    all('[data-action="language-choice"]', phone).forEach(function (button) {
      setSelected(button, button.dataset.choice === state.language);
    });
    all('[data-action="theme-choice"]', phone).forEach(function (button) {
      setSelected(button, button.dataset.choice === state.theme);
    });
    var motion = one('[data-action="motion-choice"]', phone);
    setSelected(motion, state.reducedMotion);
  }

  function setSelected(button, selected) {
    if (!button) return;
    button.classList.toggle('is-selected', selected);
    button.setAttribute('aria-pressed', String(selected));
  }

  function updateScenarioNav() {
    var activeRoute = state.route === 'mafia-setup' ? 'setup' : state.route;
    all('.scenario-link').forEach(function (button) {
      var active = button.dataset.route === activeRoute;
      button.classList.toggle('is-active', active);
      if (active) {
        button.setAttribute('aria-current', 'page');
      } else {
        button.removeAttribute('aria-current');
      }
    });
  }

  function updateInspector() {
    var note = routeNotes[state.route] || routeNotes.home;
    document.getElementById('inspector-kicker').textContent = note.kicker;
    document.getElementById('inspector-title').textContent = note.title;
    document.getElementById('inspector-summary').textContent = note.summary;
    document.getElementById('inspector-guardrail').textContent = note.guardrail;

    var points = document.getElementById('inspector-points');
    points.replaceChildren();
    note.points.forEach(function (point) {
      var item = document.createElement('li');
      item.textContent = point;
      points.appendChild(item);
    });

    var tags = document.getElementById('inspector-tags');
    tags.replaceChildren();
    note.tags.forEach(function (tag) {
      var item = document.createElement('span');
      item.textContent = tag;
      tags.appendChild(item);
    });
  }

  function syncGlobalControls() {
    document.documentElement.dir = state.direction;
    document.body.dataset.theme = state.theme === 'system'
      ? (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark')
      : state.theme;
    document.body.classList.toggle('reduced-motion', state.reducedMotion);

    var themeButton = one('[data-action="toggle-theme"]');
    var directionButton = one('[data-action="toggle-direction"]');
    var motionButton = one('[data-action="toggle-motion"]');
    var isLight = document.body.dataset.theme === 'light';

    themeButton.setAttribute('aria-pressed', String(isLight));
    one('span', themeButton).textContent = isLight ? 'Dark' : 'Light';
    directionButton.setAttribute('aria-pressed', String(state.direction === 'rtl'));
    one('span', directionButton).textContent = state.direction === 'rtl' ? 'LTR' : 'RTL';
    motionButton.setAttribute('aria-pressed', String(state.reducedMotion));
  }

  function chooseCaseFilter(filter) {
    state.caseFilter = filter;
    var current = cases[state.caseId];
    if (filter !== 'all' && current.language !== filter) {
      state.caseId = filter === 'en' ? 'last-dinner' : 'zamalek-ramadan';
    }
    render(false);
  }

  function changeRoleCount(role, delta) {
    var minimum = role === 'mafia' ? 1 : 0;
    var maximum = role === 'mafia' ? 3 : 1;
    var next = Math.max(minimum, Math.min(maximum, state.mafiaCounts[role] + delta));
    var otherUsed = Object.keys(state.mafiaCounts).reduce(function (sum, key) {
      return sum + (key === role ? 0 : state.mafiaCounts[key]);
    }, 0);
    if (8 - (otherUsed + next) < 1) {
      return;
    }
    state.mafiaCounts[role] = next;
    render(false);
  }

  document.addEventListener('click', function (event) {
    var control = event.target.closest('[data-action]');
    if (!control) return;
    var action = control.dataset.action;

    if (action === 'route') {
      event.preventDefault();
      navigate(control.dataset.route);
    } else if (action === 'toggle-theme') {
      state.theme = document.body.dataset.theme === 'dark' ? 'light' : 'dark';
      syncGlobalControls();
      if (state.route === 'settings') render(false);
    } else if (action === 'toggle-direction') {
      state.direction = state.direction === 'ltr' ? 'rtl' : 'ltr';
      syncGlobalControls();
    } else if (action === 'toggle-motion') {
      state.reducedMotion = !state.reducedMotion;
      syncGlobalControls();
      if (state.route === 'settings') render(false);
    } else if (action === 'open-game') {
      state.game = control.dataset.game;
      state.lobbyDecision = 'pending';
      navigate('setup');
    } else if (action === 'choose-local') {
      state.topology = 'local';
      navigate(state.game === 'mafia' ? 'mafia-setup' : 'cases');
    } else if (action === 'host-room') {
      state.topology = 'host';
      state.lobbyDecision = 'pending';
      state.codeCopied = false;
      navigate('host');
    } else if (action === 'join-room') {
      state.topology = 'peer';
      state.joinConnected = false;
      navigate('join');
    } else if (action === 'case-filter') {
      chooseCaseFilter(control.dataset.filter);
    } else if (action === 'case-select') {
      state.caseId = control.dataset.case;
      render(false);
    } else if (action === 'mode-select') {
      state.mode = control.dataset.mode;
      render(false);
    } else if (action === 'start-case') {
      state.topology = 'local';
      state.whodunitStage = 'clue';
      navigate('whodunit');
    } else if (action === 'role-count') {
      changeRoleCount(control.dataset.role, Number(control.dataset.delta));
    } else if (action === 'demo-toggle') {
      setSelected(control, !control.classList.contains('is-selected'));
    } else if (action === 'start-mafia') {
      state.topology = 'local';
      state.mafiaStage = 'cover';
      state.mafiaTarget = null;
      navigate('mafia', { reset: false });
    } else if (action === 'approve-player') {
      state.lobbyDecision = 'approved';
      render(false);
    } else if (action === 'decline-player') {
      state.lobbyDecision = 'declined';
      render(false);
    } else if (action === 'copy-code') {
      state.codeCopied = true;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText('MK7P2Q').catch(function () {});
      }
      render(false);
    } else if (action === 'start-room') {
      if (!control.disabled) {
        if (state.game === 'mafia') {
          state.mafiaStage = 'cover';
          state.mafiaTarget = null;
          navigate('mafia', { reset: false });
        } else {
          state.whodunitStage = 'clue';
          navigate('whodunit');
        }
      }
    } else if (action === 'reset-join') {
      state.joinConnected = false;
      render(false);
    } else if (action === 'begin-discussion') {
      state.whodunitStage = 'discussion';
      render(false);
    } else if (action === 'pause-discussion') {
      state.discussionPaused = !state.discussionPaused;
      render(false);
    } else if (action === 'advance-round') {
      if (state.round === 4) {
        state.whodunitStage = 'vote';
        state.whodunitVoteTarget = null;
      } else {
        state.round = 4;
        state.whodunitStage = 'clue';
      }
      render(false);
    } else if (action === 'select-whodunit-vote') {
      state.whodunitVoteTarget = control.dataset.target;
      render(false);
    } else if (action === 'submit-whodunit-vote' || action === 'refuse-whodunit-vote') {
      state.whodunitVoteTarget = null;
      state.whodunitStage = 'clue';
      state.round = 3;
      navigate('home');
    } else if (action === 'reveal-mafia-role') {
      state.mafiaStage = 'role';
      render(false);
    } else if (action === 'open-night-action') {
      state.mafiaStage = 'night';
      render(false);
    } else if (action === 'select-target') {
      state.mafiaTarget = control.dataset.target;
      render(false);
    } else if (action === 'submit-target') {
      if (state.mafiaTarget) {
        state.mafiaStage = 'submitted';
        render(false);
      }
    } else if (action === 'hide-private') {
      state.mafiaStage = 'cover';
      state.mafiaTarget = null;
      render(false);
    } else if (action === 'language-choice') {
      state.language = control.dataset.choice;
      if (state.language === 'ar') state.direction = 'rtl';
      if (state.language === 'en') state.direction = 'ltr';
      render(false);
    } else if (action === 'theme-choice') {
      state.theme = control.dataset.choice;
      render(false);
    } else if (action === 'motion-choice') {
      state.reducedMotion = !state.reducedMotion;
      render(false);
    } else if (action === 'demo-confirm') {
      control.textContent = 'Confirmation required';
      control.disabled = true;
    }
  });

  document.addEventListener('submit', function (event) {
    var form = event.target.closest('[data-join-form]');
    if (!form) return;
    event.preventDefault();
    if (!form.reportValidity()) return;
    state.joinConnected = true;
    state.topology = 'peer';
    render(false);
  });

  document.addEventListener('input', function (event) {
    if (!event.target.matches('.code-input')) return;
    event.target.value = event.target.value.replace(/[^a-z0-9]/gi, '').toUpperCase().slice(0, 6);
  });

  var colorPreference = window.matchMedia('(prefers-color-scheme: light)');
  if (colorPreference.addEventListener) {
    colorPreference.addEventListener('change', function () {
      if (state.theme === 'system') syncGlobalControls();
    });
  }

  render(false);
}());
