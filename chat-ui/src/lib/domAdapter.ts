import { reconcileAnswerText } from './answerTokens';
import { renderSources } from './sourceDisplay';
import { renderTraceTimeline } from './traceDisplay';
import type { SessionDisplay, SessionTurn } from './sessionDisplay';

export interface DomElements {
  form: HTMLFormElement;
  questionInput: HTMLTextAreaElement;
  submitButton: HTMLButtonElement;
  stopButton: HTMLButtonElement;
  status: HTMLParagraphElement;
  messageThread: HTMLElement;
}

export interface DomAdapter {
  showValidationStatus(message: string): void;
  setComposerEnabled(enabled: boolean): void;
  setStopVisible(visible: boolean): void;
  focusQuestion(): void;
  restoreComposer(): void;
  renderSession(session: SessionDisplay, activeTurnIndex: number | null): void;
  bindScrollBehavior(): void;
  bindComposerKeys(onSubmit: () => void): void;
}

const SCROLL_PIN_THRESHOLD_PX = 40;

function errorLabel(turn: SessionTurn): string {
  if (turn.status === 'aborted') {
    return 'Stopped — incomplete';
  }
  if (turn.status === 'error') {
    switch (turn.errorKind) {
      case 'validation-error':
        return turn.errorMessage ?? 'Question could not be submitted.';
      case 'connection-error':
        return turn.errorMessage ?? 'Connection to the assistant failed.';
      case 'stream-error':
        return turn.errorMessage ?? 'The assistant stream reported an error.';
      default:
        return turn.errorMessage ?? 'Request failed.';
    }
  }
  return '';
}

export function applyUnsupportedLayout(assistantPanel: HTMLElement): void {
  assistantPanel.classList.add('unsupported-layout');
  const traceSection = assistantPanel.querySelector('.trace-section');
  const sourcesSection = assistantPanel.querySelector('.sources-section');
  if (traceSection instanceof HTMLElement) {
    traceSection.hidden = true;
  }
  if (sourcesSection instanceof HTMLElement) {
    sourcesSection.hidden = true;
  }
}

function renderTurnAssistant(
  doc: Document,
  turn: SessionTurn,
  index: number,
  isActive: boolean,
): HTMLElement {
  const assistantPanel = doc.createElement('div');
  assistantPanel.className = 'turn-assistant';
  assistantPanel.dataset.turnAssistant = String(index);

  const traceSection = doc.createElement('section');
  traceSection.className = 'trace-section';
  traceSection.hidden = true;

  const traceHeading = doc.createElement('h2');
  traceHeading.textContent = 'Source-Usage Trace';
  traceSection.appendChild(traceHeading);

  const traceTimeline = doc.createElement('div');
  traceTimeline.className = 'trace-timeline-host';
  traceSection.appendChild(traceTimeline);

  if (turn.traceSteps.length > 0) {
    // Trust boundary: innerHTML via renderTraceTimeline; all values pass through escapeHtml.
    traceTimeline.innerHTML = renderTraceTimeline(turn.traceSteps);
    traceSection.hidden = false;
  }

  const answerHeading = doc.createElement('h2');
  answerHeading.textContent = 'Answer';

  const formingHint = doc.createElement('p');
  formingHint.className = 'forming-hint';
  formingHint.hidden = true;

  if (turn.status === 'aborted' || turn.status === 'error') {
    const label = errorLabel(turn);
    if (label) {
      formingHint.hidden = false;
      formingHint.textContent = label;
    }
  } else if (turn.sawTokens && turn.status === 'streaming') {
    formingHint.hidden = false;
    formingHint.textContent = 'Forming answer…';
  }

  const answerText = doc.createElement('p');
  answerText.className = 'answer-text';
  if (isActive) {
    answerText.setAttribute('aria-live', 'polite');
  }

  if (turn.provisionalAnswerText.length > 0) {
    reconcileAnswerText(answerText, turn.provisionalAnswerText);
  }

  const traceId = doc.createElement('p');
  traceId.className = 'trace-id';
  traceId.hidden = true;

  const sourcesSection = doc.createElement('section');
  sourcesSection.className = 'sources-section';

  const sourcesHeading = doc.createElement('h2');
  sourcesHeading.textContent = 'Sources';
  sourcesSection.appendChild(sourcesHeading);

  const sources = doc.createElement('div');
  sources.className = 'sources';
  sourcesSection.appendChild(sources);

  if (turn.status === 'complete' && turn.finalResponse) {
    const response = turn.finalResponse;
    formingHint.hidden = true;
    reconcileAnswerText(answerText, response.answerText);

    if (turn.traceSteps.length === 0 && response.sources.length === 0) {
      applyUnsupportedLayout(assistantPanel);
    } else {
      // Trust boundary: innerHTML via renderSources; all values pass through escapeHtml.
      sources.innerHTML = renderSources(response.sources);
    }

    if (response.traceCorrelationId) {
      traceId.hidden = false;
      traceId.textContent = `Trace: ${response.traceCorrelationId}`;
    }
  }

  assistantPanel.append(
    traceSection,
    answerHeading,
    formingHint,
    answerText,
    traceId,
    sourcesSection,
  );

  return assistantPanel;
}

function renderTurnArticle(
  doc: Document,
  turn: SessionTurn,
  index: number,
  isActive: boolean,
): HTMLElement {
  const article = doc.createElement('article');
  article.className = 'turn';
  article.dataset.turnIndex = String(index);

  const questionBlock = doc.createElement('div');
  questionBlock.className = 'turn-question';

  const questionLabel = doc.createElement('span');
  questionLabel.className = 'turn-question-label';
  questionLabel.textContent = 'Your question';

  const questionText = doc.createElement('p');
  questionText.className = 'user-question';
  questionText.textContent = turn.question;

  questionBlock.append(questionLabel, questionText);
  article.append(questionBlock, renderTurnAssistant(doc, turn, index, isActive));

  return article;
}

function isScrollPinned(thread: HTMLElement): boolean {
  return (
    thread.scrollHeight - thread.scrollTop - thread.clientHeight > SCROLL_PIN_THRESHOLD_PX
  );
}

function scrollThread(
  thread: HTMLElement,
  activeTurnIndex: number | null,
  mode: 'bottom' | 'active-turn',
  userPinnedScroll: boolean,
): void {
  if (userPinnedScroll) {
    return;
  }

  if (mode === 'bottom') {
    thread.scrollTop = thread.scrollHeight;
    return;
  }

  if (activeTurnIndex === null) {
    return;
  }

  const turnEl = thread.querySelector(`[data-turn-index="${activeTurnIndex}"]`);
  turnEl?.scrollIntoView({ block: 'end' });
}

export function createDomAdapter(elements: DomElements): DomAdapter {
  let userPinnedScroll = false;
  let lastTurnCount = 0;
  let lastActiveIndex: number | null = null;

  return {
    showValidationStatus(message: string): void {
      elements.status.hidden = false;
      elements.status.textContent = message;
    },

    setComposerEnabled(enabled: boolean): void {
      elements.submitButton.disabled = !enabled;
      elements.questionInput.disabled = !enabled;
    },

    setStopVisible(visible: boolean): void {
      elements.stopButton.hidden = !visible;
      elements.stopButton.disabled = !visible;
    },

    focusQuestion(): void {
      elements.questionInput.focus();
    },

    restoreComposer(): void {
      elements.submitButton.disabled = false;
      elements.questionInput.disabled = false;
      elements.stopButton.hidden = true;
      elements.stopButton.disabled = true;
      elements.questionInput.focus();
    },

    bindScrollBehavior(): void {
      elements.messageThread.addEventListener('scroll', () => {
        userPinnedScroll = isScrollPinned(elements.messageThread);
      });
    },

    bindComposerKeys(onSubmit: () => void): void {
      elements.questionInput.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' || event.shiftKey) {
          return;
        }
        event.preventDefault();
        onSubmit();
      });
    },

    renderSession(session: SessionDisplay, activeTurnIndex: number | null): void {
      elements.status.hidden = true;

      const doc = elements.messageThread.ownerDocument;
      const fragment = doc.createDocumentFragment();

      session.turns.forEach((turn, index) => {
        const isActive =
          index === activeTurnIndex &&
          (turn.status === 'pending' || turn.status === 'streaming');
        fragment.appendChild(renderTurnArticle(doc, turn, index, isActive));
      });

      elements.messageThread.replaceChildren(fragment);

      const turnCount = session.turns.length;
      const newUserMessage =
        turnCount > lastTurnCount && activeTurnIndex === turnCount - 1;
      const activeTurnUpdated =
        activeTurnIndex !== null &&
        activeTurnIndex === lastActiveIndex &&
        turnCount === lastTurnCount;

      if (newUserMessage) {
        scrollThread(elements.messageThread, activeTurnIndex, 'bottom', userPinnedScroll);
      } else if (activeTurnUpdated) {
        scrollThread(elements.messageThread, activeTurnIndex, 'active-turn', userPinnedScroll);
      }

      lastTurnCount = turnCount;
      lastActiveIndex = activeTurnIndex;
    },
  };
}

export function createDomScaffold(doc: Document = document): DomElements {
  const form = doc.createElement('form');
  form.id = 'chat-form';
  form.className = 'chat-form';

  const questionInput = doc.createElement('textarea');
  questionInput.id = 'question';

  const submitButton = doc.createElement('button');
  submitButton.id = 'submit-button';
  submitButton.type = 'submit';

  const stopButton = doc.createElement('button');
  stopButton.id = 'stop-button';
  stopButton.type = 'button';
  stopButton.hidden = true;

  const status = doc.createElement('p');
  status.id = 'status';
  status.hidden = true;

  const messageThread = doc.createElement('section');
  messageThread.id = 'message-thread';
  messageThread.className = 'message-thread';
  messageThread.setAttribute('role', 'log');
  messageThread.setAttribute('aria-label', 'Chat Interface session display');

  form.append(questionInput, submitButton, stopButton);

  return {
    form,
    questionInput,
    submitButton,
    stopButton,
    status,
    messageThread,
  };
}
