import React, { FormEvent, ReactNode, useState } from 'react';
import './App.css';

type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
};

const CHAT_API_URL = 'http://localhost:8080/api/chat';
const createMessageId = (): string =>
  `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

function readAssistantText(payload: unknown): string {
  if (typeof payload === 'string') {
    return payload;
  }

  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>;
    const commonKeys = ['message', 'response', 'reply', 'content', 'text'];

    for (const key of commonKeys) {
      if (typeof record[key] === 'string') {
        return record[key] as string;
      }
    }

    return JSON.stringify(record, null, 2);
  }

  return 'Tomt svar från servern.';
}

function normalizeAssistantText(text: string): string {
  const normalizedNewLines = text.replace(/\r\n/g, '\n').trim();
  const lines = normalizedNewLines.split('\n');
  const nonEmptyLines = lines.filter((line) => line.trim().length > 0);

  if (nonEmptyLines.length === 0) {
    return normalizedNewLines;
  }

  const smallestIndent = nonEmptyLines.reduce((smallest, line) => {
    const leadingSpaces = line.match(/^(\s*)/)?.[1].length ?? 0;
    return Math.min(smallest, leadingSpaces);
  }, Number.POSITIVE_INFINITY);

  return lines
    .map((line) => line.slice(Math.min(smallestIndent, line.length)))
    .join('\n')
    .trim();
}

function renderMarkdownLite(text: string): ReactNode {
  const lines = text.split('\n');

  return lines.map((line, lineIndex) => {
    const parts = line.split(/(\*\*[^*]+\*\*)/g);
    const renderedParts = parts.map((part, partIndex) => {
      if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
        return <strong key={`${lineIndex}-${partIndex}`}>{part.slice(2, -2)}</strong>;
      }

      return <React.Fragment key={`${lineIndex}-${partIndex}`}>{part}</React.Fragment>;
    });

    return (
      <React.Fragment key={lineIndex}>
        {renderedParts}
        {lineIndex < lines.length - 1 ? <br /> : null}
      </React.Fragment>
    );
  });
}

function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSend = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const message = input.trim();
    if (!message || isSending) {
      return;
    }

    const userMessage: ChatMessage = {
      id: createMessageId(),
      role: 'user',
      text: message
    };

    setMessages((previous) => [...previous, userMessage]);
    setInput('');
    setError(null);
    setIsSending(true);

    try {
      const response = await fetch(CHAT_API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ message })
      });

      const rawBody = await response.text();

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${rawBody}`);
      }

      const contentType = response.headers.get('content-type') ?? '';
      let payload: unknown = rawBody;

      if (contentType.includes('application/json')) {
        payload = rawBody ? (JSON.parse(rawBody) as unknown) : '';
      }

      const assistantMessage: ChatMessage = {
        id: createMessageId(),
        role: 'assistant',
        text: normalizeAssistantText(readAssistantText(payload))
      };

      setMessages((previous) => [...previous, assistantMessage]);
    } catch (caughtError) {
      setError(
        `Något gick fel vid chat-anropet till ${CHAT_API_URL}. ` +
          `Kontrollera backend/CORS och svarformat.`
      );
      console.error(caughtError);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="App">
      <main className="chat-shell">
        <h1 className="chat-title">IntelliPlan Chat</h1>
        <p className="chat-subtitle">Pratar med: {CHAT_API_URL}</p>

        <section className="chat-window" aria-label="Chat-konversation">
          {messages.length === 0 ? (
            <p className="chat-empty">Skriv ett meddelande för att starta chatten.</p>
          ) : (
            messages.map((message) => (
              <article
                key={message.id}
                className={`chat-message chat-message-${message.role}`}
              >
                <strong>{message.role === 'user' ? 'Du' : 'Assistent'}</strong>
                <div className="chat-message-body">
                  {message.role === 'assistant'
                    ? renderMarkdownLite(message.text)
                    : message.text}
                </div>
              </article>
            ))
          )}
        </section>

        {error ? <p className="chat-error">{error}</p> : null}

        <form className="chat-form" onSubmit={handleSend}>
          <label htmlFor="chat-input" className="sr-only">
            Ditt meddelande
          </label>
          <input
            id="chat-input"
            className="chat-input"
            type="text"
            placeholder="Skriv ditt meddelande..."
            value={input}
            onChange={(event) => setInput(event.target.value)}
            disabled={isSending}
          />
          <button className="chat-send" type="submit" disabled={isSending}>
            {isSending ? 'Skickar...' : 'Skicka'}
          </button>
        </form>
      </main>
    </div>
  );
}

export default App;
