export function clearAnswerText(element: HTMLElement): void {
  element.replaceChildren();
}

export function appendAnswerToken(element: HTMLElement, tokenText: string): void {
  if (tokenText.length === 0) {
    return;
  }
  element.appendChild(document.createTextNode(tokenText));
}

export function reconcileAnswerText(element: HTMLElement, authoritativeText: string): void {
  element.textContent = authoritativeText;
}
