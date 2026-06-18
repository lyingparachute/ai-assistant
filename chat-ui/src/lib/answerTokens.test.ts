// @vitest-environment happy-dom

import { describe, expect, it } from 'vitest';

import { appendAnswerToken, clearAnswerText, reconcileAnswerText } from './answerTokens';

const XSS = `<script>alert("xss")</script>`;

describe('answerTokens', () => {
  it('appends streamed tokens as literal text nodes', () => {
    const element = document.createElement('p');
    appendAnswerToken(element, 'Hello ');
    appendAnswerToken(element, XSS);

    expect(element.childNodes).toHaveLength(2);
    expect(element.textContent).toBe(`Hello ${XSS}`);
    expect(element.innerHTML).toContain('&lt;script&gt;');
    expect(element.querySelector('script')).toBeNull();
  });

  it('reconciles to authoritative final answer text', () => {
    const element = document.createElement('p');
    appendAnswerToken(element, 'provisional ');
    appendAnswerToken(element, 'tokens');

    reconcileAnswerText(element, 'Authoritative answer.');

    expect(element.textContent).toBe('Authoritative answer.');
    expect(element.childNodes).toHaveLength(1);
  });

  it('clears provisional tokens before a new stream', () => {
    const element = document.createElement('p');
    appendAnswerToken(element, 'old');
    clearAnswerText(element);

    expect(element.textContent).toBe('');
    expect(element.childNodes).toHaveLength(0);
  });
});
