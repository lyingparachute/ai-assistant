import { describe, expect, it } from 'vitest';

import { consumeSseEvents, parseSseEvents } from './sseParser';

const SAMPLE_STREAM = `event:trace
data:{"type":"countries_facts","status":"USED"}

event:token
data:{"text":"Berlin "}

event:token
data:{"text":"is great."}

event:final
data:{"answerText":"Berlin is great.","sources":[{"type":"countries_facts","status":"USED"}],"traceCorrelationId":"trace-1"}

`;

describe('parseSseEvents', () => {
  it('parses a multi-event SSE body', () => {
    const events = parseSseEvents(SAMPLE_STREAM);

    expect(events.map((event) => event.name)).toEqual(['trace', 'token', 'token', 'final']);
    expect(JSON.parse(events[0].data)).toEqual({ type: 'countries_facts', status: 'USED' });
    expect(JSON.parse(events[1].data)).toEqual({ text: 'Berlin ' });
    expect(JSON.parse(events[3].data).answerText).toBe('Berlin is great.');
  });

  it('parses unsupported zero-trace streams', () => {
    const stream = `event:final
data:{"answerText":"I cannot answer this question.","sources":[],"traceCorrelationId":"trace-2"}

`;

    const events = parseSseEvents(stream);
    expect(events.map((event) => event.name)).toEqual(['final']);
    expect(JSON.parse(events[0].data).sources).toEqual([]);
  });

  it('parses error terminal events', () => {
    const stream = `event:error
data:{"error":"unexpected_failure","message":"Stream failed."}

`;

    const events = parseSseEvents(stream);
    expect(events).toHaveLength(1);
    expect(events[0].name).toBe('error');
    expect(JSON.parse(events[0].data).message).toBe('Stream failed.');
  });
});

describe('consumeSseEvents', () => {
  it('buffers partial chunks until a full event block arrives', () => {
    const first = consumeSseEvents('event:trace\ndata:{"type":"countries_facts","status":"USED"}\n');
    expect(first.events).toHaveLength(0);
    expect(first.remainder).toContain('event:trace');

    const second = consumeSseEvents(`${first.remainder}\n\nevent:final\ndata:{"answerText":"Done","sources":[]}\n\n`);
    expect(second.events.map((event) => event.name)).toEqual(['trace', 'final']);
    expect(second.remainder).toBe('');
  });
});
