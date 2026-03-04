import React, { FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import './App.css';

type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
};

const CHAT_API_URL = 'http://localhost:8080/api/chat';
const ASSIGNMENTS_API_URL = 'http://localhost:9090/api/calendar/assignments';
const CONSULTANTS_API_URL = 'http://localhost:9090/api/calendar/consultants';
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

type StaffCalendarEntry = {
  consultantId: string;
  date: string;
  startTime: string;
  endTime: string;
  status: string;
};

type StaffCalendarRow = {
  consultantId: string;
  byDate: Record<string, StaffCalendarEntry[]>;
};

type StaffConsultantProfile = {
  firstName: string;
  lastName: string;
  employmentType: string;
  services: string;
  regions: string;
  pools: string;
  restrictions: string;
  customerExperience: string;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }

  return null;
}

function readString(record: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim();
    }
  }

  return '';
}

function readNullableString(record: Record<string, unknown>, key: string): string {
  const value = record[key];

  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }

  if (value === null) {
    return 'null';
  }

  return '-';
}

function readDateFromRecord(record: Record<string, unknown>): string {
  const directDate = readString(record, ['date', 'availabilityDate', 'day']);
  if (directDate) {
    return directDate;
  }

  const assignmentRecord = asRecord(record.assignment);
  if (assignmentRecord) {
    return readString(assignmentRecord, ['date']);
  }

  return '';
}

function readAvailabilityFromRecord(record: Record<string, unknown>): string {
  const directAvailability = readString(record, [
    'availability',
    'availabilityStatus',
    'status',
    'state'
  ]);

  if (directAvailability) {
    return directAvailability;
  }

  const assignmentRecord = asRecord(record.assignment);
  if (assignmentRecord) {
    return readString(assignmentRecord, ['status']);
  }

  return '';
}

function collectStaffEntries(
  source: unknown,
  fallbackConsultantId: string
): StaffCalendarEntry[] {
  const entries: StaffCalendarEntry[] = [];

  if (Array.isArray(source)) {
    for (const item of source) {
      entries.push(...collectStaffEntries(item, fallbackConsultantId));
    }
    return entries;
  }

  const record = asRecord(source);
  if (!record) {
    return entries;
  }

  const consultantRecord = asRecord(record.consultant);
  const consultantId =
    readString(record, ['consultantId']) ||
    (consultantRecord ? readString(consultantRecord, ['consultantId']) : '') ||
    fallbackConsultantId;

  const hasAvailabilityShape =
    typeof record.status === 'string' &&
    typeof record.date === 'string' &&
    (typeof record.availabilityId === 'string' ||
      typeof record.startTime === 'string' ||
      typeof record.endTime === 'string');

  const ownDate = readDateFromRecord(record);
  const ownAvailability = readAvailabilityFromRecord(record);
  if (hasAvailabilityShape && consultantId && ownDate && ownAvailability) {
    const startTime = readString(record, ['startTime']);
    const endTime = readString(record, ['endTime']);
    entries.push({
      consultantId,
      date: ownDate,
      startTime,
      endTime,
      status: ownAvailability
    });
  }

  const nestedKeys = [
    'calendarConsultantRow',
    'availabilities',
    'availability'
  ];

  for (const key of nestedKeys) {
    const nestedValue = record[key];
    if (Array.isArray(nestedValue)) {
      entries.push(...collectStaffEntries(nestedValue, consultantId));
    } else if (asRecord(nestedValue)) {
      entries.push(...collectStaffEntries(nestedValue, consultantId));
    }
  }

  return entries;
}

function normalizeStaffEntries(payload: unknown): StaffCalendarEntry[] {
  const rawEntries = collectStaffEntries(payload, '');
  const deduplicated = new Map<string, StaffCalendarEntry>();

  for (const entry of rawEntries) {
    const key = `${entry.consultantId}|${entry.date}|${entry.startTime}|${entry.endTime}|${entry.status}`;
    if (!deduplicated.has(key)) {
      deduplicated.set(key, entry);
    }
  }

  return Array.from(deduplicated.values());
}

function collectStaffConsultantProfiles(
  source: unknown,
  profileMap: Map<string, StaffConsultantProfile>
): void {
  if (Array.isArray(source)) {
    for (const item of source) {
      collectStaffConsultantProfiles(item, profileMap);
    }
    return;
  }

  const record = asRecord(source);
  if (!record) {
    return;
  }

  const consultantRecord = asRecord(record.consultant);
  if (consultantRecord) {
    const consultantId = readString(consultantRecord, ['consultantId']);
    if (consultantId) {
      profileMap.set(consultantId, {
        firstName: readNullableString(consultantRecord, 'firstName'),
        lastName: readNullableString(consultantRecord, 'lastName'),
        employmentType: readNullableString(consultantRecord, 'employmentType'),
        services: readNullableString(consultantRecord, 'services'),
        regions: readNullableString(consultantRecord, 'regions'),
        pools: readNullableString(consultantRecord, 'pools'),
        restrictions: readNullableString(consultantRecord, 'restrictions'),
        customerExperience: readNullableString(consultantRecord, 'customerExperience')
      });
    }
  }

  const nestedKeys = ['calendarConsultantRow', 'consultants', 'availabilities', 'availability'];
  for (const key of nestedKeys) {
    const nestedValue = record[key];
    if (nestedValue !== undefined) {
      collectStaffConsultantProfiles(nestedValue, profileMap);
    }
  }
}

function normalizeStaffProfiles(payload: unknown): Record<string, StaffConsultantProfile> {
  const profileMap = new Map<string, StaffConsultantProfile>();
  collectStaffConsultantProfiles(payload, profileMap);
  return Object.fromEntries(profileMap.entries());
}

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

function formatStaffTooltip(
  profile: StaffConsultantProfile | undefined,
  availability: StaffCalendarEntry
): string {
  if (!profile) {
    return `status: ${availability.status}\ntime: ${availability.startTime}-${availability.endTime}`;
  }

  return [
    `firstName: ${profile.firstName}`,
    `lastName: ${profile.lastName}`,
    `employmentType: ${profile.employmentType}`,
    `services: ${profile.services}`,
    `regions: ${profile.regions}`,
    `pools: ${profile.pools}`,
    `restrictions: ${profile.restrictions}`,
    `customerExperience: ${profile.customerExperience}`
  ].join('\n');
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
  const [staffEntries, setStaffEntries] = useState<StaffCalendarEntry[]>([]);
  const [staffConsultantProfiles, setStaffConsultantProfiles] = useState<
    Record<string, StaffConsultantProfile>
  >({});
  const [isStaffLoading, setIsStaffLoading] = useState(false);
  const [staffError, setStaffError] = useState<string | null>(null);
  const [staffConsultantIdFilter, setStaffConsultantIdFilter] = useState('');
  const [staffServiceFilter, setStaffServiceFilter] = useState('');
  const [staffStatusFilter, setStaffStatusFilter] = useState('');
  const [staffRegionFilter, setStaffRegionFilter] = useState('');
  const [staffFromDateFilter, setStaffFromDateFilter] = useState('');
  const [staffToDateFilter, setStaffToDateFilter] = useState('');

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

  const staffDates = useMemo(
    () =>
      Array.from(new Set(staffEntries.map((item) => item.date).filter(Boolean))).sort((a, b) =>
        a.localeCompare(b)
      ),
    [staffEntries]
  );

  const staffRows = useMemo(
    () => {
      const grouped = new Map<string, StaffCalendarRow>();

      for (const entry of staffEntries) {
        const existing = grouped.get(entry.consultantId) ?? {
          consultantId: entry.consultantId,
          byDate: {}
        };
        const values = existing.byDate[entry.date] ?? [];
        if (
          !values.some(
            (value) =>
              value.startTime === entry.startTime &&
              value.endTime === entry.endTime &&
              value.status === entry.status
          )
        ) {
          values.push(entry);
        }
        existing.byDate[entry.date] = values;
        grouped.set(entry.consultantId, existing);
      }

      return Array.from(grouped.values()).sort((a, b) =>
        a.consultantId.localeCompare(b.consultantId)
      );
    },
    [staffEntries]
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

  const loadStaff = async (url: string): Promise<void> => {
    setIsStaffLoading(true);
    setStaffError(null);

    try {
      const response = await fetch(url);
      const payload = (await response.json()) as unknown;

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      setStaffEntries(normalizeStaffEntries(payload));
      setStaffConsultantProfiles(normalizeStaffProfiles(payload));
    } catch (caughtError) {
      console.error(caughtError);
      setStaffError(`Could not load staff calendar from ${url}.`);
      setStaffEntries([]);
      setStaffConsultantProfiles({});
    } finally {
      setIsStaffLoading(false);
    }
  };

  useEffect(() => {
    loadStaff(CONSULTANTS_API_URL);
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

  const handleStaffFilter = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const consultantId = staffConsultantIdFilter.trim();
    const baseUrl = consultantId
      ? `${CONSULTANTS_API_URL}/${encodeURIComponent(consultantId)}`
      : CONSULTANTS_API_URL;

    const query = new URLSearchParams();
    if (staffServiceFilter.trim()) {
      query.append('service', staffServiceFilter.trim());
    }
    if (staffStatusFilter.trim()) {
      query.append('status', staffStatusFilter.trim());
    }
    if (staffFromDateFilter) {
      query.append('fromDate', staffFromDateFilter);
    }
    if (staffToDateFilter) {
      query.append('toDate', staffToDateFilter);
    }

    const regions = staffRegionFilter
      .split(',')
      .map((region) => region.trim())
      .filter(Boolean);
    for (const region of regions) {
      query.append('region', region);
    }

    const url = query.toString() ? `${baseUrl}?${query.toString()}` : baseUrl;
    await loadStaff(url);
  };

  const handleStaffReset = async (): Promise<void> => {
    setStaffConsultantIdFilter('');
    setStaffServiceFilter('');
    setStaffStatusFilter('');
    setStaffRegionFilter('');
    setStaffFromDateFilter('');
    setStaffToDateFilter('');
    await loadStaff(CONSULTANTS_API_URL);
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
                <div className="assignments-panel">
                  <form className="assignments-controls" onSubmit={handleStaffFilter}>
                    <input
                      className="assignments-control-input"
                      type="text"
                      value={staffConsultantIdFilter}
                      onChange={(event) => setStaffConsultantIdFilter(event.target.value)}
                      placeholder="ConsultantId (optional, ex: CONS_100071)"
                    />
                    <input
                      className="assignments-control-input"
                      type="text"
                      value={staffServiceFilter}
                      onChange={(event) => setStaffServiceFilter(event.target.value)}
                      placeholder="Service (ex: ForkliftOperator)"
                    />
                    <select
                      className="assignments-control-select"
                      value={staffStatusFilter}
                      onChange={(event) => setStaffStatusFilter(event.target.value)}
                    >
                      <option value="">All statuses</option>
                      <option value="CONFIRMED">CONFIRMED</option>
                      <option value="LATE_REPORTED">LATE_REPORTED</option>
                      <option value="SICK_REPORTED">SICK_REPORTED</option>
                      <option value="NO_SHOW">NO_SHOW</option>
                    </select>
                    <input
                      className="assignments-control-input"
                      type="text"
                      value={staffRegionFilter}
                      onChange={(event) => setStaffRegionFilter(event.target.value)}
                      placeholder="Regions (SE-STH,SE-MAL)"
                    />
                    <input
                      className="assignments-control-input"
                      type="date"
                      value={staffFromDateFilter}
                      onChange={(event) => setStaffFromDateFilter(event.target.value)}
                    />
                    <input
                      className="assignments-control-input"
                      type="date"
                      value={staffToDateFilter}
                      onChange={(event) => setStaffToDateFilter(event.target.value)}
                    />
                    <button className="assignments-btn assignments-btn-primary" type="submit">
                      Filter
                    </button>
                    <button
                      className="assignments-btn assignments-btn-secondary"
                      type="button"
                      onClick={handleStaffReset}
                    >
                      Reset
                    </button>
                  </form>

                  {isStaffLoading ? <p>Loading staff...</p> : null}

                  {staffError ? <p className="assignments-error">{staffError}</p> : null}

                  {!isStaffLoading && !staffError ? (
                    staffRows.length > 0 && staffDates.length > 0 ? (
                      <div className="assignments-table-wrap">
                        <table className="assignments-table">
                          <thead>
                            <tr>
                              <th scope="col">consultantId</th>
                              {staffDates.map((date) => (
                                <th scope="col" key={date}>
                                  {formatDateLabel(date)}
                                </th>
                              ))}
                            </tr>
                          </thead>
                          <tbody>
                            {staffRows.map((row) => (
                              <tr key={row.consultantId}>
                                <th scope="row">{row.consultantId}</th>
                                {staffDates.map((date) => (
                                  <td key={`${row.consultantId}-${date}`}>
                                    {row.byDate[date] && row.byDate[date].length > 0 ? (
                                      <div className="staff-cell-group">
                                        {row.byDate[date].map((availability) => (
                                          <div
                                            key={`${row.consultantId}-${date}-${availability.startTime}-${availability.endTime}-${availability.status}`}
                                            className={`staff-cell ${availability.status.toLowerCase()}`}
                                            title={formatStaffTooltip(
                                              staffConsultantProfiles[row.consultantId],
                                              availability
                                            )}
                                          >
                                            <span className="staff-cell-time">
                                              {availability.startTime && availability.endTime
                                                ? `${availability.startTime}-${availability.endTime}`
                                                : availability.status}
                                            </span>
                                          </div>
                                        ))}
                                      </div>
                                    ) : (
                                      ''
                                    )}
                                  </td>
                                ))}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <p>No staff calendar data to display.</p>
                    )
                  ) : null}
                </div>
              )}
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}

export default App;
