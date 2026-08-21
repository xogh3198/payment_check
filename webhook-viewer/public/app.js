const eventRows = document.querySelector('#eventRows');
const emptyState = document.querySelector('#emptyState');
const connectionStatus = document.querySelector('#connectionStatus');
const detailsPanel = document.querySelector('#detailsPanel');
const detailsList = document.querySelector('#detailsList');
const rawText = document.querySelector('#rawText');
const rawJson = document.querySelector('#rawJson');
const bankFilter = document.querySelector('#bankFilter');

let events = [];
let selectedEventId = null;

document.querySelector('#refreshButton').addEventListener('click', loadEvents);
document.querySelector('#clearButton').addEventListener('click', clearEvents);
bankFilter.addEventListener('change', () => {
  selectedEventId = null;
  detailsPanel.hidden = true;
  render();
});
document.querySelector('#closeDetailsButton').addEventListener('click', () => {
  selectedEventId = null;
  detailsPanel.hidden = true;
  renderRows();
});

await loadEvents();
setInterval(loadEvents, 3000);

async function loadEvents() {
  try {
    const response = await fetch('/api/events', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    events = payload.events;
    connectionStatus.textContent = '수신기 정상';
    connectionStatus.className = 'connection ok';
    render();
  } catch (error) {
    connectionStatus.textContent = '수신기 연결 실패';
    connectionStatus.className = 'connection error';
  }
}

async function clearEvents() {
  if (!window.confirm('Webhook Viewer에 저장된 PoC 이벤트를 모두 삭제할까요?')) return;
  const response = await fetch('/api/events', { method: 'DELETE' });
  if (!response.ok) {
    window.alert('이벤트를 삭제하지 못했습니다.');
    return;
  }
  selectedEventId = null;
  detailsPanel.hidden = true;
  await loadEvents();
}

function render() {
  const parsed = events.filter((event) => event.parseStatus === 'parsed').length;
  document.querySelector('#eventCount').textContent = String(events.length);
  document.querySelector('#kbCount').textContent = String(events.filter((event) => event.provider === 'KB').length);
  document.querySelector('#hanaCount').textContent = String(events.filter((event) => event.provider === 'HANA').length);
  document.querySelector('#nhCount').textContent = String(events.filter((event) => event.provider === 'NH').length);
  document.querySelector('#beepayCount').textContent = String(events.filter((event) => event.provider === 'BEEPAY').length);
  document.querySelector('#parsedCount').textContent = String(parsed);
  document.querySelector('#partialCount').textContent = String(events.length - parsed);
  document.querySelector('#lastReceived').textContent = events[0]
    ? formatDate(events[0].serverReceivedAt)
    : '-';
  const visibleEvents = filteredEvents();
  emptyState.hidden = visibleEvents.length > 0;
  emptyState.textContent = events.length === 0
    ? '아직 수신된 이벤트가 없습니다. 터미널이나 안드로이드 앱에서 은행별 샘플을 보내주세요.'
    : `${bankLabel(bankFilter.value)} 이벤트가 없습니다.`;
  renderRows();

  if (selectedEventId) {
    const selected = events.find((event) => event.eventId === selectedEventId);
    if (selected) renderDetails(selected);
  }
}

function renderRows() {
  eventRows.replaceChildren(...filteredEvents().map((event) => {
    const row = document.createElement('tr');
    if (event.eventId === selectedEventId) row.classList.add('selected');
    row.addEventListener('click', () => {
      selectedEventId = event.eventId;
      renderRows();
      renderDetails(event);
    });
    row.append(
      cell(formatDate(event.serverReceivedAt)),
      bankCell(event.provider),
      cell(formatAmount(event.amount)),
      cell(event.payerName || '확인 필요'),
      cell(event.accountMasked || '-'),
      statusCell(event.parseStatus),
      cell(event.paymentMethod === 'fishery_voucher' ? '수산상품권' : '계좌이체'),
    );
    return row;
  }));
}

function renderDetails(event) {
  detailsPanel.hidden = false;
  const entries = [
    ['이벤트 ID', event.eventId],
    ['제공자', `${bankLabel(event.provider)} (${event.provider || '-'})`],
    ['결제자', event.payerName || '확인 필요'],
    ['금액', formatAmount(event.amount)],
    ['거래 시각', formatDate(event.transactionAt)],
    ['알림 게시 시각', formatDate(event.postedAt)],
    ['서버 수신 시각', formatDate(event.serverReceivedAt)],
    ['알림 패키지', event.packageName || '-'],
  ];
  detailsList.replaceChildren(...entries.flatMap(([label, value]) => {
    const term = document.createElement('dt');
    const detail = document.createElement('dd');
    term.textContent = label;
    detail.textContent = value;
    return [term, detail];
  }));
  rawText.textContent = event.rawText || '';
  rawJson.textContent = JSON.stringify(event, null, 2);
  detailsPanel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function filteredEvents() {
  if (bankFilter.value === 'ALL') return events;
  return events.filter((event) => event.provider === bankFilter.value);
}

function cell(value) {
  const element = document.createElement('td');
  element.textContent = value;
  return element;
}

function statusCell(status) {
  const tableCell = document.createElement('td');
  const badge = document.createElement('span');
  badge.className = `status ${status === 'parsed' ? '' : 'partial'}`.trim();
  badge.textContent = status === 'parsed' ? '완료' : '확인 필요';
  tableCell.append(badge);
  return tableCell;
}

function bankCell(bank) {
  const tableCell = document.createElement('td');
  const badge = document.createElement('span');
  badge.className = `bank ${bankClass(bank)}`;
  badge.textContent = bankLabel(bank);
  tableCell.append(badge);
  return tableCell;
}

function bankClass(bank) {
  if (bank === 'HANA') return 'hana';
  if (bank === 'NH') return 'nh';
  if (bank === 'BEEPAY') return 'hana';
  return 'kb';
}

function bankLabel(bank) {
  if (bank === 'KB') return '국민은행';
  if (bank === 'HANA') return '하나은행';
  if (bank === 'NH') return '농협은행';
  if (bank === 'BEEPAY') return '비플페이';
  if (bank === 'ALL') return '선택한 은행의';
  return bank || '알 수 없는 은행';
}

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
}

function formatAmount(value) {
  return Number.isSafeInteger(value) ? `${new Intl.NumberFormat('ko-KR').format(value)}원` : '확인 필요';
}
