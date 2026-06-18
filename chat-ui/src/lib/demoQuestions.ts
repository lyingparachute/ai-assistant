import fixture from '../../../e2e-tests/src/test/resources/demo-questions.json';

export interface DemoQuestion {
  key: string;
  question: string;
  sourcePathKey: string;
}

export interface DemoQuestionsFixture {
  questions: DemoQuestion[];
}

export const demoQuestions = fixture as DemoQuestionsFixture;
