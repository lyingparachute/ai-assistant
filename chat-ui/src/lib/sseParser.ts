export interface SseEvent {
  name: string;
  data: string;
}

export function parseSseEvents(content: string): SseEvent[] {
  const events: SseEvent[] = [];
  let currentName: string | null = null;
  const dataLines: string[] = [];

  const commit = (): void => {
    if (currentName === null) {
      return;
    }
    events.push({ name: currentName, data: dataLines.join('\n') });
    currentName = null;
    dataLines.length = 0;
  };

  for (const line of content.split('\n')) {
    if (line.startsWith('event:')) {
      currentName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    } else if (line === '') {
      commit();
    }
  }

  commit();
  return events;
}

export function consumeSseEvents(buffer: string): { events: SseEvent[]; remainder: string } {
  const events: SseEvent[] = [];
  let start = 0;

  while (start < buffer.length) {
    const separatorIndex = buffer.indexOf('\n\n', start);
    if (separatorIndex === -1) {
      break;
    }

    const block = buffer.slice(start, separatorIndex);
    start = separatorIndex + 2;

    const event = parseSseBlock(block);
    if (event) {
      events.push(event);
    }
  }

  return { events, remainder: buffer.slice(start) };
}

function parseSseBlock(block: string): SseEvent | null {
  if (block.trim() === '') {
    return null;
  }

  let name: string | null = null;
  const dataLines: string[] = [];

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    }
  }

  if (name === null) {
    return null;
  }

  return { name, data: dataLines.join('\n') };
}
