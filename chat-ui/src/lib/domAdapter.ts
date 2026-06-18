import { reconcileAnswerText } from './answerTokens';
import { renderSources } from './sourceDisplay';
import { renderTraceTimeline } from './traceDisplay';
import type { SessionTurn } from './sessionDisplay';

export interface DomElements {
  form: HTMLFormElement;
  questionInput: HTMLTextAreaElement;
  submitButton: HTMLButtonElement;
  stopButton: HTMLButtonElement;
  status: HTMLParagraphElement;
  answerPanel: HTMLElement;
  traceSection: HTMLElement;
  traceTimeline: HTMLDivElement;
  formingHint: HTMLParagraphElement;
  answerText: HTMLParagraphElement;
  traceId: HTMLParagraphElement;
  sourcesSection: HTMLElement;
  sources: HTMLDivElement;
}

export interface DomAdapter {
  showValidationStatus(message: string): void;
  setComposerEnabled(enabled: boolean): void;
  setStopVisible(visible: boolean): void;
  focusQuestion(): void;
  restoreComposer(): void;
  renderTurn(turn: SessionTurn): void;
  hideAnswerPanel(): void;
}

function resetTurnPanel(elements: DomElements): void {
  elements.answerText.replaceChildren();
  elements.formingHint.hidden = true;
  elements.traceSection.hidden = true;
  elements.traceTimeline.innerHTML = '';
  elements.sources.innerHTML = '';
  elements.traceId.hidden = true;
  elements.traceId.textContent = '';
  elements.answerPanel.classList.remove('unsupported-layout');
  elements.sourcesSection.hidden = false;
}

export function applyUnsupportedLayout(elements: DomElements): void {
  elements.answerPanel.classList.add('unsupported-layout');
  elements.traceSection.hidden = true;
  elements.sourcesSection.hidden = true;
}

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

export function createDomAdapter(elements: DomElements): DomAdapter {
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

    hideAnswerPanel(): void {
      elements.answerPanel.hidden = true;
    },

    renderTurn(turn: SessionTurn): void {
      elements.status.hidden = true;
      elements.answerPanel.hidden = false;
      resetTurnPanel(elements);

      if (turn.traceSteps.length > 0) {
        // Trust boundary: innerHTML via renderTraceTimeline; all values pass through escapeHtml.
        elements.traceTimeline.innerHTML = renderTraceTimeline(turn.traceSteps);
        elements.traceSection.hidden = false;
      }

      if (turn.status === 'aborted' || turn.status === 'error') {
        const label = errorLabel(turn);
        if (label) {
          elements.formingHint.hidden = false;
          elements.formingHint.textContent = label;
        }
      } else if (turn.sawTokens && turn.status === 'streaming') {
        elements.formingHint.hidden = false;
        elements.formingHint.textContent = 'Forming answer…';
      }

      if (turn.provisionalAnswerText.length > 0) {
        reconcileAnswerText(elements.answerText, turn.provisionalAnswerText);
      }

      if (turn.status === 'complete' && turn.finalResponse) {
        const response = turn.finalResponse;
        elements.formingHint.hidden = true;
        reconcileAnswerText(elements.answerText, response.answerText);

        if (turn.traceSteps.length === 0 && response.sources.length === 0) {
          applyUnsupportedLayout(elements);
        } else {
          // Trust boundary: innerHTML via renderSources; all values pass through escapeHtml.
          elements.sources.innerHTML = renderSources(response.sources);
        }

        if (response.traceCorrelationId) {
          elements.traceId.hidden = false;
          elements.traceId.textContent = `Trace: ${response.traceCorrelationId}`;
        }
      }
    },
  };
}

export function createDomScaffold(doc: Document = document): DomElements {
  const form = doc.createElement('form');
  form.id = 'chat-form';

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

  const answerPanel = doc.createElement('section');
  answerPanel.id = 'answer-panel';
  answerPanel.hidden = true;

  const traceSection = doc.createElement('section');
  traceSection.id = 'trace-section';
  traceSection.hidden = true;

  const traceTimeline = doc.createElement('div');
  traceTimeline.id = 'trace-timeline';

  const formingHint = doc.createElement('p');
  formingHint.id = 'forming-hint';
  formingHint.hidden = true;

  const answerText = doc.createElement('p');
  answerText.id = 'answer-text';

  const traceId = doc.createElement('p');
  traceId.id = 'trace-id';
  traceId.hidden = true;

  const sourcesSection = doc.createElement('section');
  sourcesSection.id = 'sources-section';

  const sources = doc.createElement('div');
  sources.id = 'sources';

  traceSection.appendChild(traceTimeline);
  sourcesSection.appendChild(sources);
  answerPanel.append(traceSection, formingHint, answerText, traceId, sourcesSection);
  form.append(questionInput, submitButton, stopButton);

  return {
    form,
    questionInput,
    submitButton,
    stopButton,
    status,
    answerPanel,
    traceSection,
    traceTimeline,
    formingHint,
    answerText,
    traceId,
    sourcesSection,
    sources,
  };
}
