import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import { demoQuestions } from './demoQuestions';

const FIXTURE_PATH = resolve(
  import.meta.dirname,
  '../../../e2e-tests/src/test/resources/demo-questions.json',
);

describe('demoQuestions', () => {
  it('matches the shared e2e demo-questions fixture', () => {
    const fromDisk = JSON.parse(readFileSync(FIXTURE_PATH, 'utf8')) as typeof demoQuestions;
    expect(demoQuestions.questions).toEqual(fromDisk.questions);
  });
});
