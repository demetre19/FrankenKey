package juloo.keyboard2.suggestions;

import android.content.SharedPreferences;
import android.os.Handler;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import juloo.cdict.Cdict;
import juloo.keyboard2.CurrentlyTypedWord;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.Logs;
import juloo.keyboard2.autocorrect.Hunspell;
import juloo.keyboard2.lang.LanguagePack;

/**
 * Serial owner for shared suggestion and autocorrect decoding.
 *
 * Main-thread state is copied into immutable requests. Native dictionaries,
 * Hunspell and personalization are used only by the single worker. Keystroke
 * requests use a latest-wins mailbox, rather than the executor's task queue.
 */
public final class SharedDecoder implements AutoCloseable
{
  public static interface Callback
  {
    public void decoder_state_changed(Presentation state);
  }

  /** Immutable candidate-row state. Only READY contains clickable data. */
  public static final class Presentation
  {
    public static enum State
    {
      PENDING,
      READY,
      EMPTY
    }

    public static enum Feedback
    {
      NONE,
      LEARNED,
      FORGOT
    }

    public final State state;
    public final long sessionEpoch;
    public final Decoder.RequestKey key;
    public final Decoder.Result result;
    public final Feedback feedback;
    public final String feedbackWord;

    private Presentation(State state_, long sessionEpoch_,
        Decoder.RequestKey key_, Decoder.Result result_, Feedback feedback_,
        String feedbackWord_)
    {
      state = state_;
      sessionEpoch = sessionEpoch_;
      key = key_;
      result = result_;
      feedback = feedback_;
      feedbackWord = feedbackWord_;
    }

    static Presentation pending(long sessionEpoch, Decoder.RequestKey key)
    {
      return new Presentation(State.PENDING, sessionEpoch, key, null,
          Feedback.NONE, null);
    }

    static Presentation ready(long sessionEpoch, Decoder.Result result,
        Feedback feedback, String feedbackWord)
    {
      return new Presentation(State.READY, sessionEpoch, result.key, result,
          feedback, feedbackWord);
    }

    static Presentation empty(long sessionEpoch, Decoder.RequestKey key)
    {
      return new Presentation(State.EMPTY, sessionEpoch, key, null,
          Feedback.NONE, null);
    }
  }

  /**
   * Strong, immutable owner for one desired dictionary/language epoch.
   *
   * The key is caller-defined and must change when the selected resources or
   * their contents change. The full Cdict array is retained even though only
   * the selected main and emoji views are queried.
   */
  public static final class ResourceSpec
  {
    public final String key;
    public final Cdict mainDictionary;
    public final Cdict emojiDictionary;
    public final LanguagePack languagePack;
    public final boolean initiallyFailed;

    private final Cdict[] _owners;

    public ResourceSpec(String key_, Cdict[] owners, Cdict mainDictionary_,
        Cdict emojiDictionary_, LanguagePack languagePack_)
    {
      this(key_, owners, mainDictionary_, emojiDictionary_, languagePack_,
          false);
    }

    public ResourceSpec(String key_, Cdict[] owners, Cdict mainDictionary_,
        Cdict emojiDictionary_, LanguagePack languagePack_,
        boolean initiallyFailed_)
    {
      if (key_ == null)
        throw new IllegalArgumentException("resource key must not be null");
      _owners = owners == null ? new Cdict[0] : owners.clone();
      if (!owns(mainDictionary_) || !owns(emojiDictionary_))
        throw new IllegalArgumentException(
            "selected dictionaries must belong to the strong owner array");
      key = key_;
      mainDictionary = mainDictionary_;
      emojiDictionary = emojiDictionary_;
      languagePack = languagePack_;
      initiallyFailed = initiallyFailed_;
    }

    public static ResourceSpec empty(String key)
    {
      return new ResourceSpec(key, null, null, null, null, false);
    }

    public Cdict[] owners()
    {
      return _owners.clone();
    }

    private boolean owns(Cdict dictionary)
    {
      if (dictionary == null)
        return true;
      for (Cdict owned : _owners)
        if (owned == dictionary)
          return true;
      return false;
    }

    private boolean has_same_key(ResourceSpec other)
    {
      return other != null && key.equals(other.key);
    }
  }

  /** Descriptor used to construct the mutable store on the decoder worker. */
  public static final class PersonalizationSpec
  {
    public final String key;
    public final SharedPreferences preferences;

    public PersonalizationSpec(String key_, SharedPreferences preferences_)
    {
      if (key_ == null)
        throw new IllegalArgumentException(
            "personalization key must not be null");
      key = key_;
      preferences = preferences_;
    }

    public static PersonalizationSpec empty(String key)
    {
      return new PersonalizationSpec(key, null);
    }

    private boolean has_same_key(PersonalizationSpec other)
    {
      return other != null && key.equals(other.key);
    }
  }

  /** Opaque, single-use learning envelope captured before editor mutation. */
  public static final class CommitToken
  {
    private final SharedDecoder _owner;
    private final long _sessionEpoch;
    private final long _personalizationDomainEpoch;
    private final PendingDecode _source;
    private final String _committedWord;
    private final String _correctedFrom;
    private final Boolean _recognized;
    private boolean _consumed;

    private CommitToken(SharedDecoder owner, long sessionEpoch,
        long personalizationDomainEpoch, PendingDecode source,
        String committedWord, String correctedFrom, Boolean recognized)
    {
      _owner = owner;
      _sessionEpoch = sessionEpoch;
      _personalizationDomainEpoch = personalizationDomainEpoch;
      _source = source;
      _committedWord = committedWord;
      _correctedFrom = correctedFrom;
      _recognized = recognized;
    }
  }

  public SharedDecoder(Handler mainHandler, Callback callback)
  {
    if (mainHandler == null || callback == null)
      throw new IllegalArgumentException(
          "main handler and callback must not be null");
    _mainHandler = mainHandler;
    _callback = callback;
    _executor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
          @Override
          public Thread newThread(Runnable runnable)
          {
            return new Thread(runnable, "FrankenKey decoder");
          }
        });
  }

  /**
   * Passively install the exact resources expected by the next editor session.
   * This does not create a session, presentation, or editor context.
   */
  public void prewarm(ResourceSpec resources,
      PersonalizationSpec personalization)
  {
    if (resources == null || personalization == null)
      throw new IllegalArgumentException("prewarm fields must not be null");
    synchronized (_lock)
    {
      ensure_open_locked();
      if (_active)
        throw new IllegalStateException(
            "active sessions must use explicit update methods");
      if (_resources == null || !_resources.has_same_key(resources))
      {
        _resources = resources;
        _resourceEpoch++;
      }
      if (_personalization == null
          || !_personalization.has_same_key(personalization))
      {
        _personalization = personalization;
        _personalizationSpecEpoch++;
        _personalizationDomainEpoch++;
        _personalizationEpoch++;
      }
      ensure_drain_locked();
    }
  }

  /** Start a fresh editor session. Every call returns a new session epoch. */
  public long start_session(Decoder.DecoderConfig config,
      ResourceSpec resources, KeyboardData layout,
      PersonalizationSpec personalization)
  {
    if (config == null || resources == null || personalization == null)
      throw new IllegalArgumentException("session fields must not be null");
    Decoder.Geometry geometry = Decoder.Geometry.from(layout);
    synchronized (_lock)
    {
      ensure_open_locked();
      if (_active)
        finish_locked(_sessionEpoch);

      _sessionEpoch++;
      _active = true;
      _config = config;
      _configEpoch++;
      _geometry = geometry;
      _layoutEpoch++;

      if (_resources == null || !_resources.has_same_key(resources))
      {
        _resources = resources;
        _resourceEpoch++;
      }
      if (_personalization == null
          || !_personalization.has_same_key(personalization))
      {
        _personalization = personalization;
        _personalizationSpecEpoch++;
        _personalizationDomainEpoch++;
        _personalizationEpoch++;
      }

      // Context never crosses editor sessions. This ordered reset runs after
      // controls from the preceding session and before this session decodes.
      _personalizationEpoch++;
      enqueue_control_locked(Control.reset(_sessionEpoch, _resourceEpoch,
            _resources, _personalizationSpecEpoch, _personalizationEpoch,
            _personalization));

      _latestWord = null;
      _latestKey = null;
      _acceptedResult = null;
      _acceptedEnvelope = null;
      _lastRequestEnvelope = null;
      _feedback = null;
      _pending = null;
      _presentation = Presentation.empty(_sessionEpoch, null);
      post_presentation_locked(_presentation);
      ensure_drain_locked();
      return _sessionEpoch;
    }
  }

  /** Idempotently stop one editor session without closing reusable resources. */
  public void finish_session(long sessionEpoch)
  {
    synchronized (_lock)
    {
      if (_closed || !_active || sessionEpoch != _sessionEpoch)
        return;
      finish_locked(sessionEpoch);
      ensure_drain_locked();
    }
  }

  public void update_config(long sessionEpoch, Decoder.DecoderConfig config)
  {
    if (config == null)
      throw new IllegalArgumentException("config must not be null");
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch) || config.equals(_config))
        return;
      boolean resetContext = _config.useTypingAssistance
        && !config.useTypingAssistance;
      _config = config;
      _configEpoch++;
      if (resetContext)
      {
        _personalizationEpoch++;
        enqueue_control_locked(Control.reset(sessionEpoch, _resourceEpoch,
              _resources, _personalizationSpecEpoch, _personalizationEpoch,
              _personalization));
      }
      resubmit_latest_locked();
      ensure_drain_locked();
    }
  }

  /** Explicit resource updates always allocate a new resource epoch. */
  public void update_resources(long sessionEpoch, ResourceSpec resources)
  {
    if (resources == null)
      throw new IllegalArgumentException("resources must not be null");
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch))
        return;
      _resources = resources;
      _resourceEpoch++;
      resubmit_latest_locked();
      ensure_drain_locked();
    }
  }

  /** The supplied layout must be the exact layout installed in Keyboard2View. */
  public void update_layout(long sessionEpoch, KeyboardData layout)
  {
    Decoder.Geometry geometry = Decoder.Geometry.from(layout);
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch))
        return;
      _geometry = geometry;
      _layoutEpoch++;
      resubmit_latest_locked();
      ensure_drain_locked();
    }
  }

  /** Replace the worker-owned personalization store/domain. */
  public void update_personalization(long sessionEpoch,
      PersonalizationSpec personalization)
  {
    if (personalization == null)
      throw new IllegalArgumentException("personalization must not be null");
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch))
        return;
      boolean changedDomain = _personalization == null
        || !_personalization.has_same_key(personalization);
      _personalization = personalization;
      _personalizationSpecEpoch++;
      if (changedDomain)
        _personalizationDomainEpoch++;
      _personalizationEpoch++;
      resubmit_latest_locked();
      ensure_drain_locked();
    }
  }

  /**
   * Submit an immutable word snapshot. At most one request can be running and
   * one newer request pending; a newer pending request replaces the older one.
   */
  public Decoder.RequestKey request(long sessionEpoch,
      CurrentlyTypedWord.Snapshot word)
  {
    if (word == null)
      throw new IllegalArgumentException("word must not be null");
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch))
        return null;
      PendingDecode envelope = make_request_locked(word);
      _latestWord = word;
      _latestKey = envelope.request.key;
      _lastRequestEnvelope = envelope;
      _acceptedResult = null;
      _acceptedEnvelope = null;
      _feedback = null;

      if (should_decode_locked(word))
      {
        _pending = envelope;
        if (should_publish_candidates_locked())
          _presentation = Presentation.pending(_sessionEpoch,
              envelope.request.key);
        else
          _presentation = Presentation.empty(_sessionEpoch,
              envelope.request.key);
      }
      else
      {
        _pending = null;
        _presentation = Presentation.empty(_sessionEpoch,
            envelope.request.key);
      }
      post_presentation_locked(_presentation);
      ensure_drain_locked();
      return envelope.request.key;
    }
  }

  /** Immediately invalidate all candidate/correction targets for this session. */
  public void invalidate(long sessionEpoch)
  {
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch))
        return;
      invalidate_locked();
    }
  }

  /** Return only the exact latest completed result; never wait for the worker. */
  public Decoder.Result current_result(Decoder.RequestKey key)
  {
    synchronized (_lock)
    {
      if (!is_current_locked(key) || _acceptedResult == null
          || !_acceptedResult.key.equals(key))
        return null;
      return _acceptedResult;
    }
  }

  public boolean is_current(Decoder.RequestKey key)
  {
    synchronized (_lock)
    {
      return is_current_locked(key);
    }
  }

  public Decoder.RequestKey current_key()
  {
    synchronized (_lock)
    {
      return _active && !_closed ? _latestKey : null;
    }
  }

  public Presentation current_presentation()
  {
    synchronized (_lock)
    {
      return _presentation;
    }
  }

  /**
   * Capture the exact-current request and learning domain before the editor is
   * mutated. A null token suppresses learning only; it does not authorize or
   * reject the caller's editor operation.
   */
  public CommitToken prepare_commit(long sessionEpoch,
      Decoder.RequestKey source, String committedWord, String correctedFrom)
  {
    if (source == null || committedWord == null)
      return null;
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch) || !is_current_locked(source)
          || !_config.useTypingAssistance
          || (!_config.suggestionsEnabled && !_config.autocorrectEnabled))
        return null;
      PendingDecode sourceEnvelope = find_envelope_locked(source);
      if (sourceEnvelope == null)
        return null;
      Boolean recognized = recognized_from_result_locked(source,
          committedWord, sourceEnvelope);
      String acceptedCorrection = accepted_correction_source_locked(source,
          sourceEnvelope, committedWord, correctedFrom);
      return new CommitToken(this, sessionEpoch,
          _personalizationDomainEpoch, sourceEnvelope, committedWord,
          acceptedCorrection, recognized);
    }
  }

  /**
   * Consume one prepared envelope without requiring its captured request to
   * remain latest. Session and personalization-domain validity are checked
   * atomically with one-time consumption.
   */
  public void commit_prepared(CommitToken token)
  {
    if (token == null || token._owner != this)
      return;
    synchronized (_lock)
    {
      if (token._consumed)
        return;
      token._consumed = true;
      if (!is_active_session_locked(token._sessionEpoch)
          || token._personalizationDomainEpoch
            != _personalizationDomainEpoch)
        return;
      _personalizationEpoch++;
      enqueue_control_locked(Control.record(token._sessionEpoch,
            token._source, _personalizationSpecEpoch, _personalization,
            token._committedWord, token._correctedFrom, token._recognized,
            _personalizationEpoch));
      resubmit_latest_locked();
      ensure_drain_locked();
    }
  }

  public void learn_word(long sessionEpoch, String word)
  {
    if (!PersonalizationStore.is_learnable(word))
      return;
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch)
          || !_config.useTypingAssistance || !_config.suggestionsEnabled)
        return;
      _personalizationEpoch++;
      Decoder.RequestKey feedbackKey = resubmit_latest_locked();
      enqueue_control_locked(Control.learn(sessionEpoch, _resourceEpoch,
            _resources, _personalizationSpecEpoch, _personalizationEpoch,
            _personalization, word, feedbackKey));
      ensure_drain_locked();
    }
  }

  public void unlearn_word(long sessionEpoch, Decoder.RequestKey source,
      String word)
  {
    if (source == null || word == null)
      return;
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch) || !is_current_locked(source)
          || !_config.useTypingAssistance || !_config.suggestionsEnabled)
        return;
      _personalizationEpoch++;
      Decoder.RequestKey feedbackKey = resubmit_latest_locked();
      enqueue_control_locked(Control.unlearn(sessionEpoch, _resourceEpoch,
            _resources, _personalizationSpecEpoch, _personalizationEpoch,
            _personalization, word, feedbackKey));
      ensure_drain_locked();
    }
  }

  public void clear_personalization(long sessionEpoch)
  {
    synchronized (_lock)
    {
      boolean activeCall = is_active_session_locked(sessionEpoch);
      boolean managementCall = !_closed && !_active && sessionEpoch == 0
        && _personalization != null;
      if (!activeCall && !managementCall)
        return;
      _personalizationEpoch++;
      if (activeCall)
        resubmit_latest_locked();
      enqueue_control_locked(Control.clear(sessionEpoch, _resourceEpoch,
            _resources, _personalizationSpecEpoch, _personalizationEpoch,
            _personalization));
      ensure_drain_locked();
    }
  }

  public void reset_context(long sessionEpoch)
  {
    synchronized (_lock)
    {
      if (!is_active_session_locked(sessionEpoch))
        return;
      _personalizationEpoch++;
      resubmit_latest_locked();
      enqueue_control_locked(Control.reset(sessionEpoch, _resourceEpoch,
            _resources, _personalizationSpecEpoch, _personalizationEpoch,
            _personalization));
      ensure_drain_locked();
    }
  }

  /**
   * Reject callbacks immediately, then let the worker drain ordered semantic
   * controls and close Hunspell after the current native call returns.
   */
  @Override
  public void close()
  {
    synchronized (_lock)
    {
      if (_closed)
        return;
      _closed = true;
      _active = false;
      _pending = null;
      _latestWord = null;
      _latestKey = null;
      _acceptedResult = null;
      _acceptedEnvelope = null;
      _feedback = null;
      _closeRequested = true;
      _presentation = Presentation.empty(_sessionEpoch, null);
      ensure_drain_locked();
    }
  }

  private void finish_locked(long sessionEpoch)
  {
    _personalizationEpoch++;
    enqueue_control_locked(Control.reset(sessionEpoch, _resourceEpoch,
          _resources, _personalizationSpecEpoch, _personalizationEpoch,
          _personalization));
    _active = false;
    _pending = null;
    _latestWord = null;
    _latestKey = null;
    _acceptedResult = null;
    _acceptedEnvelope = null;
    _lastRequestEnvelope = null;
    _feedback = null;
    _presentation = Presentation.empty(sessionEpoch, null);
  }

  private PendingDecode make_request_locked(CurrentlyTypedWord.Snapshot word)
  {
    Decoder.RequestKey key = new Decoder.RequestKey(_sessionEpoch,
        ++_requestGeneration, word.revision, _resourceEpoch, _layoutEpoch,
        _configEpoch, _personalizationEpoch);
    Decoder.Request request = new Decoder.Request(key, word, _geometry, _config);
    return new PendingDecode(request, _resources, _personalizationSpecEpoch,
        _personalization);
  }

  /** Return the resubmitted key, or null when there is no current word. */
  private Decoder.RequestKey resubmit_latest_locked()
  {
    if (!_active || _latestWord == null)
    {
      _pending = null;
      _latestKey = null;
      _acceptedResult = null;
      _acceptedEnvelope = null;
      _feedback = null;
      _presentation = Presentation.empty(_sessionEpoch, null);
      if (_active)
        post_presentation_locked(_presentation);
      return null;
    }

    PendingDecode envelope = make_request_locked(_latestWord);
    _latestKey = envelope.request.key;
    _lastRequestEnvelope = envelope;
    _acceptedResult = null;
    _acceptedEnvelope = null;
    _feedback = null;
    if (should_decode_locked(_latestWord))
    {
      _pending = envelope;
      if (should_publish_candidates_locked())
        _presentation = Presentation.pending(_sessionEpoch,
            envelope.request.key);
      else
        _presentation = Presentation.empty(_sessionEpoch,
            envelope.request.key);
    }
    else
    {
      _pending = null;
      _presentation = Presentation.empty(_sessionEpoch,
          envelope.request.key);
    }
    post_presentation_locked(_presentation);
    return envelope.request.key;
  }

  private boolean should_decode_locked(CurrentlyTypedWord.Snapshot word)
  {
    if (word.hasSelection || !_config.useTypingAssistance)
      return false;
    boolean display = _config.suggestionsEnabled && _config.showCandidates;
    if (!display && !_config.autocorrectEnabled)
      return false;
    return word.word.length() != 0 || display;
  }

  private boolean should_publish_candidates_locked()
  {
    return _config.useTypingAssistance && _config.suggestionsEnabled
      && _config.showCandidates;
  }

  private void invalidate_locked()
  {
    _pending = null;
    _latestWord = null;
    _latestKey = null;
    _acceptedResult = null;
    _acceptedEnvelope = null;
    _feedback = null;
    _presentation = Presentation.empty(_sessionEpoch, null);
    post_presentation_locked(_presentation);
  }

  private PendingDecode find_envelope_locked(Decoder.RequestKey key)
  {
    if (_lastRequestEnvelope != null
        && _lastRequestEnvelope.request.key.equals(key))
      return _lastRequestEnvelope;
    if (_running != null && _running.request.key.equals(key))
      return _running;
    if (_pending != null && _pending.request.key.equals(key))
      return _pending;
    if (_acceptedEnvelope != null
        && _acceptedEnvelope.request.key.equals(key))
      return _acceptedEnvelope;
    return null;
  }

  private Boolean recognized_from_result_locked(Decoder.RequestKey key,
      String word, PendingDecode source)
  {
    if (_lastCompletedResult == null || !_lastCompletedResult.key.equals(key))
      return null;
    Decoder.Request probe = new Decoder.Request(key, word, null,
        source.request.geometry, source.request.config);
    String normalized = probe.normalized;
    Decoder.Candidate literal = _lastCompletedResult.literal;
    if (literal != null && literal.canonical.equals(normalized))
      return has_word_evidence(literal);
    Decoder.Candidate correction = _lastCompletedResult.autocorrection;
    if (correction != null && correction.canonical.equals(normalized))
      return has_word_evidence(correction);
    for (Decoder.Candidate candidate : _lastCompletedResult.words())
      if (candidate.canonical.equals(normalized))
        return has_word_evidence(candidate);
    return null;
  }

  private static boolean has_word_evidence(Decoder.Candidate candidate)
  {
    return candidate.recognized || candidate.unigramCount > 0;
  }

  private String accepted_correction_source_locked(Decoder.RequestKey key,
      PendingDecode sourceEnvelope, String committedWord, String correctedFrom)
  {
    if (correctedFrom == null
        || !PersonalizationStore.is_plausible_correction(correctedFrom,
          committedWord))
      return null;

    String source = Decoder.normalize_correction_text(correctedFrom);
    String target = Decoder.normalize_correction_text(committedWord);
    String queried = sourceEnvelope.request.correctionSource;
    String targetCanonical = Decoder.normalize(committedWord);
    if (_acceptedResult != null && _acceptedResult.key.equals(key))
    {
      if (source.equals(queried) && !target.equals(queried))
      {
        Decoder.Candidate correction = _acceptedResult.autocorrection;
        if (is_accepted_correction_target(correction, target,
              targetCanonical, queried))
          return correctedFrom;
        for (Decoder.Candidate candidate : _acceptedResult.words())
          if (is_accepted_correction_target(candidate, target,
                targetCanonical, queried))
            return correctedFrom;
        return null;
      }

      Decoder.Candidate literal = _acceptedResult.literal;
      if (target.equals(queried) && literal != null
          && literal.canonical.equals(targetCanonical))
        return correctedFrom;
    }

    // A fast manual correction may reach its separator before the immutable
    // request finishes decoding. The key-event layer has already verified the
    // exact editor word and boundary; require that its request was actually
    // eligible for worker decoding so selected/unsafe requests still fail shut.
    if (target.equals(queried) && !source.equals(queried)
        && is_decode_envelope_locked(sourceEnvelope))
      return correctedFrom;
    return null;
  }

  private boolean is_decode_envelope_locked(PendingDecode source)
  {
    return source != null && (source == _pending || source == _running
        || source == _acceptedEnvelope);
  }

  private static boolean is_accepted_correction_target(
      Decoder.Candidate candidate, String target, String targetCanonical,
      String queried)
  {
    return candidate != null && candidate.canonical.equals(targetCanonical)
      && Decoder.normalize_correction_text(candidate.surface).equals(target)
      && !target.equals(queried)
      && PersonalizationStore.is_plausible_correction(queried, target)
      && (candidate.recognized || candidate.learned);
  }

  private boolean is_active_session_locked(long sessionEpoch)
  {
    return !_closed && _active && sessionEpoch == _sessionEpoch;
  }

  private boolean is_current_locked(Decoder.RequestKey key)
  {
    return key != null && !_closed && _active && _latestKey != null
      && _latestKey.equals(key)
      && key.sessionEpoch == _sessionEpoch
      && key.resourceEpoch == _resourceEpoch
      && key.layoutEpoch == _layoutEpoch
      && key.configEpoch == _configEpoch
      && key.personalizationEpoch == _personalizationEpoch;
  }

  private void post_presentation_locked(Presentation state)
  {
    _postedPresentation = state;
    if (_presentationDeliveryScheduled)
      return;
    _presentationDeliveryScheduled = true;
    boolean posted = _mainHandler.post(new Runnable()
        {
          @Override
          public void run()
          {
            synchronized (_lock)
            {
              Presentation state = _postedPresentation;
              _postedPresentation = null;
              _presentationDeliveryScheduled = false;
              if (state == null || _closed || !_active
                  || state.sessionEpoch != _sessionEpoch)
                return;
              if (state.key != null)
              {
                if (_latestKey == null || !_latestKey.equals(state.key))
                  return;
                if (state.state == Presentation.State.READY
                    && (_acceptedResult == null
                      || _acceptedResult != state.result))
                  return;
              }
              else if (_presentation != state)
                return;
              _callback.decoder_state_changed(state);
            }
          }
        });
    if (!posted)
    {
      _postedPresentation = null;
      _presentationDeliveryScheduled = false;
    }
  }

  private void ensure_open_locked()
  {
    if (_closed)
      throw new IllegalStateException("decoder is closed");
  }

  /** Submit only the one long-lived drain runnable, never a task per request. */
  private void ensure_drain_locked()
  {
    if (_drainScheduled || _workerClosed)
      return;
    _drainScheduled = true;
    try
    {
      _executor.execute(_drainRunnable);
    }
    catch (RejectedExecutionException e)
    {
      _drainScheduled = false;
      if (!_closed)
        throw e;
    }
  }

  private final Runnable _drainRunnable = new Runnable()
  {
    @Override
    public void run()
    {
      worker_loop();
    }
  };

  private void worker_loop()
  {
    while (true)
    {
      Control control = null;
      InstallState install = null;
      PendingDecode request = null;
      boolean close = false;
      synchronized (_lock)
      {
        if (!_controls.isEmpty())
          control = _controls.removeFirst();
        else if (_closeRequested)
          close = true;
        else if (_workerResourceEpoch != _resourceEpoch
            || _workerPersonalizationSpecEpoch != _personalizationSpecEpoch
            || _workerPersonalizationEpoch != _personalizationEpoch)
          install = new InstallState(_resourceEpoch, _resources,
              _personalizationSpecEpoch, _personalizationEpoch,
              _personalization);
        else if (_pending != null)
        {
          request = _pending;
          _pending = null;
          _running = request;
        }
        else
        {
          _drainScheduled = false;
          return;
        }
      }

      if (control != null)
        run_control(control);
      else if (close)
      {
        close_worker();
        return;
      }
      else if (install != null)
        install_state(install);
      else if (request != null)
        run_decode(request);
    }
  }

  private void install_state(InstallState state)
  {
    install_resources(state.resourceEpoch, state.resources);
    install_personalization(state.personalizationSpecEpoch,
        state.personalization);
    if (_workerPersonalizationEpoch != state.personalizationEpoch)
    {
      try
      {
        _workerPersonalization.reset_context();
      }
      catch (RuntimeException e)
      {
        Logs.exn("Failed to reset decoder context", e);
      }
      _workerPersonalizationEpoch = state.personalizationEpoch;
    }
    synchronized (_lock)
    {
      _workerResourceEpoch = state.resourceEpoch;
      _workerPersonalizationSpecEpoch = state.personalizationSpecEpoch;
      _workerPersonalizationEpoch = state.personalizationEpoch;
    }
  }

  private void install_resources(long epoch, ResourceSpec resources)
  {
    if (_workerResources != null && _workerResources.epoch == epoch)
      return;
    if (_workerResources != null && _workerResources.hunspell != null)
    {
      try
      {
        _workerResources.hunspell.close();
      }
      catch (RuntimeException e)
      {
        Logs.exn("Failed to close Hunspell", e);
      }
    }

    WorkerResources next = new WorkerResources(epoch, resources);
    if (resources.languagePack != null)
    {
      try
      {
        next.hunspell = Hunspell.load(resources.languagePack);
      }
      catch (Exception e)
      {
        next.hunspellFailed = true;
        Logs.exn("Failed to load Hunspell", e);
      }
    }
    _workerResources = next;
    synchronized (_lock)
    {
      _workerResourceEpoch = epoch;
    }
  }

  private void install_personalization(long specEpoch,
      PersonalizationSpec personalization)
  {
    if (_workerPersonalization != null
        && _workerPersonalizationSpecEpoch == specEpoch)
      return;
    try
    {
      _workerPersonalization = new PersonalizationStore(
          personalization.preferences);
      _workerPersonalizationFailed = false;
    }
    catch (RuntimeException e)
    {
      _workerPersonalization = PersonalizationStore.empty();
      _workerPersonalizationFailed = true;
      Logs.exn("Failed to load personalization", e);
    }
    synchronized (_lock)
    {
      _workerPersonalizationSpecEpoch = specEpoch;
    }
  }

  private void run_decode(PendingDecode envelope)
  {
    install_resources(envelope.request.key.resourceEpoch, envelope.resources);
    install_personalization(envelope.personalizationSpecEpoch,
        envelope.personalization);
    if (_workerPersonalizationEpoch != envelope.request.key.personalizationEpoch)
    {
      try
      {
        _workerPersonalization.reset_context();
      }
      catch (RuntimeException e)
      {
        _workerPersonalizationFailed = true;
        Logs.exn("Failed to repair decoder context", e);
      }
      _workerPersonalizationEpoch =
        envelope.request.key.personalizationEpoch;
    }

    Decoder.Result result = null;
    try
    {
      Cdict main = _workerResources.cdictFailed
        ? null : _workerResources.spec.mainDictionary;
      Cdict emoji = _workerResources.cdictFailed
        ? null : _workerResources.spec.emojiDictionary;
      result = _decoder.decode(envelope.request, main, emoji,
          _workerResources.hunspell, _workerPersonalization,
          _workerResources.failed() || _workerPersonalizationFailed);
      if (result.failure == Decoder.Failure.NATIVE_CORRUPT)
        _workerResources.cdictFailed = true;
    }
    catch (RuntimeException e)
    {
      Logs.exn("Shared decoder failed", e);
      if (_workerResources.hunspell != null)
      {
        try
        {
          _workerResources.hunspell.close();
        }
        catch (RuntimeException closeError)
        {
          Logs.exn("Failed to retire Hunspell after decoder error",
              closeError);
        }
        _workerResources.hunspell = null;
      }
      _workerResources.hunspellFailed = true;
      try
      {
        result = _decoder.decode(envelope.request,
            _workerResources.cdictFailed
              ? null : _workerResources.spec.mainDictionary,
            _workerResources.cdictFailed
              ? null : _workerResources.spec.emojiDictionary,
            null, _workerPersonalization, true);
      }
      catch (RuntimeException fallbackError)
      {
        Logs.exn("Literal decoder fallback failed", fallbackError);
      }
    }

    complete_decode(envelope, result);
    synchronized (_lock)
    {
      if (_running == envelope)
        _running = null;
    }
  }

  private void complete_decode(PendingDecode envelope, Decoder.Result result)
  {
    Presentation presentation = null;
    synchronized (_lock)
    {
      if (result == null || !_active || _closed
          || _latestKey == null
          || !_latestKey.equals(envelope.request.key)
          || !result.key.equals(envelope.request.key)
          || _latestWord == null
          || !result.queriedWord.equals(_latestWord.word)
          || _workerResourceEpoch != result.key.resourceEpoch
          || _workerPersonalizationEpoch != result.key.personalizationEpoch)
        return;

      _acceptedResult = result;
      _acceptedEnvelope = envelope;
      _lastCompletedResult = result;
      FeedbackRecord feedback = _feedback;
      if (feedback != null && !feedback.key.equals(result.key))
        feedback = null;
      if (should_publish_candidates_locked())
        presentation = Presentation.ready(_sessionEpoch, result,
            feedback == null ? Presentation.Feedback.NONE : feedback.feedback,
            feedback == null ? null : feedback.word);
      else
        presentation = Presentation.empty(_sessionEpoch, result.key);
      _presentation = presentation;
      post_presentation_locked(presentation);
    }
  }

  private void run_control(Control control)
  {
    install_personalization(control.personalizationSpecEpoch,
        control.personalization);
    boolean feedback = false;
    try
    {
      switch (control.kind)
      {
        case RECORD:
          install_resources(control.source.request.key.resourceEpoch,
              control.source.resources);
          boolean recognized = control.recognized != null
            ? control.recognized.booleanValue()
            : is_recognized_worker(control.source, control.word);
          if (recognized)
            _workerPersonalization.record_commit(control.word,
                control.correctedFrom);
          else
          {
            if (control.correctedFrom != null)
              _workerPersonalization.record_correction(control.correctedFrom,
                  control.word);
            _workerPersonalization.reset_context();
          }
          break;
        case LEARN:
        {
          long generation = _workerPersonalization.generation();
          _workerPersonalization.record_word(control.word);
          feedback = _workerPersonalization.generation() != generation;
          break;
        }
        case UNLEARN:
          feedback = _workerPersonalization.unlearn_word(control.word);
          break;
        case CLEAR:
          _workerPersonalization.clear();
          break;
        case RESET:
          _workerPersonalization.reset_context();
          break;
      }
    }
    catch (RuntimeException e)
    {
      _workerPersonalizationFailed = true;
      Logs.exn("Decoder control failed", e);
    }
    _workerPersonalizationEpoch = control.personalizationEpoch;
    synchronized (_lock)
    {
      _workerPersonalizationSpecEpoch = control.personalizationSpecEpoch;
      _workerPersonalizationEpoch = control.personalizationEpoch;
      if (feedback && control.feedbackKey != null
          && is_current_locked(control.feedbackKey))
        _feedback = new FeedbackRecord(control.feedbackKey,
            control.kind == ControlKind.UNLEARN
              ? Presentation.Feedback.FORGOT
              : Presentation.Feedback.LEARNED,
            control.word);
    }
  }

  private boolean is_recognized_worker(PendingDecode source, String word)
  {
    Decoder.Request probe = new Decoder.Request(source.request.key, word, null,
        source.request.geometry, source.request.config);
    String normalized = probe.normalized;
    if (_workerPersonalization.is_learned(normalized))
      return true;
    if (!_workerResources.cdictFailed
        && _workerResources.spec.mainDictionary != null)
    {
      try
      {
        if (_workerResources.spec.mainDictionary.find(normalized).found)
          return true;
      }
      catch (IllegalStateException e)
      {
        _workerResources.cdictFailed = true;
        Logs.exn("Cdict recognition failed", e);
      }
      catch (RuntimeException e)
      {
        Logs.exn("Cdict recognition failed", e);
      }
    }
    if (_workerResources.hunspell != null)
    {
      try
      {
        return _workerResources.hunspell.spell(normalized);
      }
      catch (RuntimeException e)
      {
        Logs.exn("Hunspell recognition failed", e);
        try
        {
          _workerResources.hunspell.close();
        }
        catch (RuntimeException closeError)
        {
          Logs.exn("Failed to retire broken Hunspell", closeError);
        }
        _workerResources.hunspell = null;
        _workerResources.hunspellFailed = true;
      }
    }
    return false;
  }

  private void close_worker()
  {
    if (_workerResources != null && _workerResources.hunspell != null)
    {
      try
      {
        _workerResources.hunspell.close();
      }
      catch (RuntimeException e)
      {
        Logs.exn("Failed to close decoder resources", e);
      }
    }
    _workerResources = null;
    _workerPersonalization = null;
    synchronized (_lock)
    {
      _controls.clear();
      _running = null;
      _acceptedEnvelope = null;
      _lastRequestEnvelope = null;
      _acceptedResult = null;
      _lastCompletedResult = null;
      _resources = null;
      _personalization = null;
      _postedPresentation = null;
      _presentationDeliveryScheduled = false;
      _workerClosed = true;
      _drainScheduled = false;
    }
    _executor.shutdown();
  }

  private void enqueue_control_locked(Control control)
  {
    if (_controls.size() >= MAX_CONTROLS)
      throw new IllegalStateException("decoder control queue is full");
    _controls.addLast(control);
  }

  private final Object _lock = new Object();
  private final Handler _mainHandler;
  private final Callback _callback;
  private final ExecutorService _executor;
  private final Decoder _decoder = new Decoder();
  private final ArrayDeque<Control> _controls = new ArrayDeque<Control>();

  private boolean _active = false;
  private boolean _closed = false;
  private boolean _closeRequested = false;
  private boolean _drainScheduled = false;
  private boolean _workerClosed = false;

  private long _sessionEpoch = 0;
  private long _requestGeneration = 0;
  private long _resourceEpoch = 0;
  private long _layoutEpoch = 0;
  private long _configEpoch = 0;
  private long _personalizationSpecEpoch = 0;
  private long _personalizationDomainEpoch = 0;
  private long _personalizationEpoch = 0;

  private Decoder.DecoderConfig _config;
  private Decoder.Geometry _geometry = Decoder.Geometry.from(null);
  private ResourceSpec _resources;
  private PersonalizationSpec _personalization;
  private CurrentlyTypedWord.Snapshot _latestWord;
  private Decoder.RequestKey _latestKey;
  private PendingDecode _pending;
  private PendingDecode _running;
  private PendingDecode _acceptedEnvelope;
  private PendingDecode _lastRequestEnvelope;
  private Decoder.Result _acceptedResult;
  private Decoder.Result _lastCompletedResult;
  private FeedbackRecord _feedback;
  private Presentation _presentation = Presentation.empty(0, null);
  private Presentation _postedPresentation;
  private boolean _presentationDeliveryScheduled = false;

  // These mirrors are written by the worker under _lock and let the mailbox
  // choose a coalesced install action without submitting another task.
  private long _workerResourceEpoch = -1;
  private long _workerPersonalizationSpecEpoch = -1;
  private long _workerPersonalizationEpoch = -1;

  // Worker-confined state below. Only epoch mirrors above cross the lock.
  private WorkerResources _workerResources;
  private PersonalizationStore _workerPersonalization;
  private boolean _workerPersonalizationFailed = false;

  private static final int MAX_CONTROLS = 64;

  private static final class PendingDecode
  {
    final Decoder.Request request;
    final ResourceSpec resources;
    final long personalizationSpecEpoch;
    final PersonalizationSpec personalization;

    PendingDecode(Decoder.Request request_, ResourceSpec resources_,
        long personalizationSpecEpoch_, PersonalizationSpec personalization_)
    {
      request = request_;
      resources = resources_;
      personalizationSpecEpoch = personalizationSpecEpoch_;
      personalization = personalization_;
    }
  }

  private static final class WorkerResources
  {
    final long epoch;
    final ResourceSpec spec;
    Hunspell hunspell;
    boolean hunspellFailed;
    boolean cdictFailed;

    WorkerResources(long epoch_, ResourceSpec spec_)
    {
      epoch = epoch_;
      spec = spec_;
      hunspellFailed = spec_.initiallyFailed;
      cdictFailed = spec_.initiallyFailed
        && spec_.mainDictionary == null && spec_.emojiDictionary == null;
    }

    boolean failed()
    {
      return spec.initiallyFailed || hunspellFailed || cdictFailed;
    }
  }

  private static final class InstallState
  {
    final long resourceEpoch;
    final ResourceSpec resources;
    final long personalizationSpecEpoch;
    final long personalizationEpoch;
    final PersonalizationSpec personalization;

    InstallState(long resourceEpoch_, ResourceSpec resources_,
        long personalizationSpecEpoch_, long personalizationEpoch_,
        PersonalizationSpec personalization_)
    {
      resourceEpoch = resourceEpoch_;
      resources = resources_;
      personalizationSpecEpoch = personalizationSpecEpoch_;
      personalizationEpoch = personalizationEpoch_;
      personalization = personalization_;
    }
  }

  private static final class FeedbackRecord
  {
    final Decoder.RequestKey key;
    final Presentation.Feedback feedback;
    final String word;

    FeedbackRecord(Decoder.RequestKey key_, Presentation.Feedback feedback_,
        String word_)
    {
      key = key_;
      feedback = feedback_;
      word = word_;
    }
  }

  private static enum ControlKind
  {
    RECORD,
    LEARN,
    UNLEARN,
    CLEAR,
    RESET
  }

  private static final class Control
  {
    final ControlKind kind;
    final long sessionEpoch;
    final long resourceEpoch;
    final ResourceSpec resources;
    final long personalizationSpecEpoch;
    final long personalizationEpoch;
    final PersonalizationSpec personalization;
    final PendingDecode source;
    final String word;
    final String correctedFrom;
    final Boolean recognized;
    final Decoder.RequestKey feedbackKey;

    private Control(ControlKind kind_, long sessionEpoch_, long resourceEpoch_,
        ResourceSpec resources_, long personalizationSpecEpoch_,
        long personalizationEpoch_, PersonalizationSpec personalization_,
        PendingDecode source_, String word_, String correctedFrom_,
        Boolean recognized_, Decoder.RequestKey feedbackKey_)
    {
      kind = kind_;
      sessionEpoch = sessionEpoch_;
      resourceEpoch = resourceEpoch_;
      resources = resources_;
      personalizationSpecEpoch = personalizationSpecEpoch_;
      personalizationEpoch = personalizationEpoch_;
      personalization = personalization_;
      source = source_;
      word = word_;
      correctedFrom = correctedFrom_;
      recognized = recognized_;
      feedbackKey = feedbackKey_;
    }

    static Control record(long sessionEpoch, PendingDecode source,
        long personalizationSpecEpoch,
        PersonalizationSpec personalization, String word,
        String correctedFrom, Boolean recognized, long personalizationEpoch)
    {
      return new Control(ControlKind.RECORD, sessionEpoch,
          source.request.key.resourceEpoch, source.resources,
          personalizationSpecEpoch, personalizationEpoch, personalization,
          source, word, correctedFrom, recognized, null);
    }

    static Control learn(long sessionEpoch, long resourceEpoch,
        ResourceSpec resources, long personalizationSpecEpoch,
        long personalizationEpoch, PersonalizationSpec personalization,
        String word, Decoder.RequestKey feedbackKey)
    {
      return new Control(ControlKind.LEARN, sessionEpoch, resourceEpoch,
          resources, personalizationSpecEpoch, personalizationEpoch,
          personalization, null, word, null, null, feedbackKey);
    }

    static Control unlearn(long sessionEpoch, long resourceEpoch,
        ResourceSpec resources, long personalizationSpecEpoch,
        long personalizationEpoch, PersonalizationSpec personalization,
        String word, Decoder.RequestKey feedbackKey)
    {
      return new Control(ControlKind.UNLEARN, sessionEpoch, resourceEpoch,
          resources, personalizationSpecEpoch, personalizationEpoch,
          personalization, null, word, null, null, feedbackKey);
    }

    static Control clear(long sessionEpoch, long resourceEpoch,
        ResourceSpec resources, long personalizationSpecEpoch,
        long personalizationEpoch, PersonalizationSpec personalization)
    {
      return new Control(ControlKind.CLEAR, sessionEpoch, resourceEpoch,
          resources, personalizationSpecEpoch, personalizationEpoch,
          personalization, null, null, null, null, null);
    }

    static Control reset(long sessionEpoch, long resourceEpoch,
        ResourceSpec resources, long personalizationSpecEpoch,
        long personalizationEpoch, PersonalizationSpec personalization)
    {
      return new Control(ControlKind.RESET, sessionEpoch, resourceEpoch,
          resources, personalizationSpecEpoch, personalizationEpoch,
          personalization, null, null, null, null, null);
    }
  }
}
