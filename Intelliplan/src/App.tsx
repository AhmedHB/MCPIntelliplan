import React, { FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import './App.css';

type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
};

const CHAT_API_URL = 'http://localhost:8080/api/chat';
const ASSIGNMENTS_API_URL = 'http://localhost:9090/api/calendar/assignments';
const createMessageId = (): string =>
  `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

type CalendarAssignmentItem = {
  assignment: {
    assignmentId: string;
    customerId: string;
    consultantId: string;
    service: string;
    date: string;
    startTime: string;
    endTime: string;
    status: string;
  };
};

type CalendarAssignmentsResponse = {
  calendarAssignments?: CalendarAssignmentItem[];
};

type AssignmentCalendarRow = {
  assignmentId: string;
  byDate: Record<
    string,
    {
      time: string;
      customerId: string;
      consultantId: string;
      service: string;
      status: string;
    }
  >;
};

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

function formatDateLabel(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);

  if (Number.isNaN(date.getTime())) {
    return isoDate;
  }

  return new Intl.DateTimeFormat('sv-SE', {
    weekday: 'short',
    day: '2-digit',
    month: '2-digit'
  }).format(date);
}

function getStatusClassName(status: string): string {
  switch (status) {
    case 'CONFIRMED':
      return 'status-confirmed';
    case 'LATE_REPORTED':
      return 'status-late-reported';
    case 'SICK_REPORTED':
      return 'status-sick-reported';
    case 'NO_SHOW':
      return 'status-no-show';
    default:
      return 'status-default';
  }
}

function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'tab-1' | 'tab-2'>('tab-1');
  const [calendarAssignments, setCalendarAssignments] = useState<CalendarAssignmentItem[]>(
    []
  );
  const [isAssignmentsLoading, setIsAssignmentsLoading] = useState(false);
  const [assignmentsError, setAssignmentsError] = useState<string | null>(null);
  const [serviceFilter, setServiceFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [regionFilter, setRegionFilter] = useState('');

  const assignmentDates = useMemo(
    () =>
      Array.from(
        new Set(calendarAssignments.map((item) => item.assignment.date).filter(Boolean))
      ).sort((a, b) => a.localeCompare(b)),
    [calendarAssignments]
  );

  const assignmentRows = useMemo(
    () => {
      const grouped = new Map<string, AssignmentCalendarRow>();

      for (const item of calendarAssignments) {
        const assignment = item.assignment;
        const existing = grouped.get(assignment.assignmentId) ?? {
          assignmentId: assignment.assignmentId,
          byDate: {}
        };

        existing.byDate[assignment.date] = {
          time: `${assignment.startTime}-${assignment.endTime}`,
          customerId: assignment.customerId,
          consultantId: assignment.consultantId,
          service: assignment.service,
          status: assignment.status
        };
        grouped.set(assignment.assignmentId, existing);
      }

      return Array.from(grouped.values()).sort((a, b) =>
        a.assignmentId.localeCompare(b.assignmentId)
      );
    },
    [calendarAssignments]
  );

  const loadAssignments = async (url: string): Promise<void> => {
    setIsAssignmentsLoading(true);
    setAssignmentsError(null);

    try {
      const response = await fetch(url);
      const payload = (await response.json()) as CalendarAssignmentsResponse;

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      setCalendarAssignments(payload.calendarAssignments ?? []);
    } catch (caughtError) {
      console.error(caughtError);
      setAssignmentsError(
        `Kunde inte hamta assignments fran ${url}. Kontrollera att API:t ar igang.`
      );
      setCalendarAssignments([]);
    } finally {
      setIsAssignmentsLoading(false);
    }
  };

  useEffect(() => {
    loadAssignments(ASSIGNMENTS_API_URL);
  }, []);

  const handleAssignmentsFilter = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const query = new URLSearchParams();
    if (serviceFilter.trim()) {
      query.append('service', serviceFilter.trim());
    }
    if (statusFilter.trim()) {
      query.append('status', statusFilter.trim());
    }

    const regions = regionFilter
      .split(',')
      .map((region) => region.trim())
      .filter(Boolean);
    for (const region of regions) {
      query.append('region', region);
    }

    const url = query.toString()
      ? `${ASSIGNMENTS_API_URL}?${query.toString()}`
      : ASSIGNMENTS_API_URL;
    await loadAssignments(url);
  };

  const handleAssignmentsReset = async (): Promise<void> => {
    setServiceFilter('');
    setStatusFilter('');
    setRegionFilter('');
    await loadAssignments(ASSIGNMENTS_API_URL);
  };

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
        <div className="chat-layout">
          <section className="chat-main">
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
          </section>

          <aside className="tab-panel" aria-label="Sidopanel med flikar">
            <div className="tab-header" role="tablist" aria-label="Flikar">
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'tab-1'}
                className={`tab-button ${activeTab === 'tab-1' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('tab-1')}
              >
                Assignments
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'tab-2'}
                className={`tab-button ${activeTab === 'tab-2' ? 'is-active' : ''}`}
                onClick={() => setActiveTab('tab-2')}
              >
                Staff
              </button>
            </div>

            <div className="tab-content" role="tabpanel">
              {activeTab === 'tab-1' ? (
                <div className="assignments-panel">
                  <form className="assignments-controls" onSubmit={handleAssignmentsFilter}>
                    <input
                      className="assignments-control-input"
                      type="text"
                      value={serviceFilter}
                      onChange={(event) => setServiceFilter(event.target.value)}
                      placeholder="Service (ex: ForkliftOperator)"
                    />
                    <select
                      className="assignments-control-select"
                      value={statusFilter}
                      onChange={(event) => setStatusFilter(event.target.value)}
                    >
                      <option value="">Alla statusar</option>
                      <option value="CONFIRMED">CONFIRMED</option>
                      <option value="LATE_REPORTED">LATE_REPORTED</option>
                      <option value="SICK_REPORTED">SICK_REPORTED</option>
                      <option value="NO_SHOW">NO_SHOW</option>
                    </select>
                    <input
                      className="assignments-control-input"
                      type="text"
                      value={regionFilter}
                      onChange={(event) => setRegionFilter(event.target.value)}
                      placeholder="Region(er), kommaseparerat (SE-STH,SE-MAL)"
                    />
                    <button className="assignments-btn assignments-btn-primary" type="submit">
                      Filter
                    </button>
                    <button
                      className="assignments-btn assignments-btn-secondary"
                      type="button"
                      onClick={handleAssignmentsReset}
                    >
                      Reset
                    </button>
                  </form>

                  {isAssignmentsLoading ? <p>Laddar assignments...</p> : null}

                  {assignmentsError ? (
                    <p className="assignments-error">{assignmentsError}</p>
                  ) : null}

                  {!isAssignmentsLoading && !assignmentsError ? (
                    assignmentRows.length > 0 && assignmentDates.length > 0 ? (
                      <>
                        <div className="assignment-legend">
                          <span className="legend-item legend-confirmed">Confirmed</span>
                          <span className="legend-item legend-late">Late reported</span>
                          <span className="legend-item legend-sick">Sick reported</span>
                          <span className="legend-item legend-no-show">No show</span>
                        </div>
                        <div className="assignments-table-wrap">
                          <table className="assignments-table">
                            <thead>
                              <tr>
                                <th scope="col">assignmentId</th>
                                {assignmentDates.map((date) => (
                                  <th scope="col" key={date}>
                                    {formatDateLabel(date)}
                                  </th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {assignmentRows.map((row) => (
                                <tr key={row.assignmentId}>
                                  <th scope="row">{row.assignmentId}</th>
                                  {assignmentDates.map((date) => {
                                    const cell = row.byDate[date];
                                    return (
                                      <td key={`${row.assignmentId}-${date}`}>
                                        {cell ? (
                                          <div
                                            className={`assignment-cell ${getStatusClassName(
                                              cell.status
                                            )}`}
                                            title={
                                              `customerId: ${cell.customerId}\n` +
                                              `consultantId: ${cell.consultantId}\n` +
                                              `time: ${cell.time}\n` +
                                              `service: ${cell.service}\n` +
                                              `status: ${cell.status}`
                                            }
                                          >
                                            <div className="assignment-cell-time">{cell.time}</div>
                                          </div>
                                        ) : (
                                          ''
                                        )}
                                      </td>
                                    );
                                  })}
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </>
                    ) : (
                      <p>Inga assignments att visa.</p>
                    )
                  ) : null}
                </div>
              ) : (
                <p>Staff visas har.</p>
              )}
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}

export default App;
