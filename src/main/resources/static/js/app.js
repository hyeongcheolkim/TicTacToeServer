'use strict';

const WEBSOCKET_URL = "http://localhost:8080/ws";

const nicknameScreen = document.getElementById('nickname-screen');
const lobbyScreen = document.getElementById('lobby-screen');
const gameScreen = document.getElementById('game-screen');
const nicknameInput = document.getElementById('nickname');
const enterLobbyButton = document.getElementById('enter-lobby');
const roomNameInput = document.getElementById('roomName');
const createRoomButton = document.getElementById('create-room');
const refreshRoomsButton = document.getElementById('refresh-rooms');
const roomList = document.getElementById('room-list');
const chatInput = document.getElementById('chat-message-input');
const sendChatButton = document.getElementById('send-chat-button');
const readyButton = document.getElementById('ready-button');
const kickButton = document.getElementById('kick-button');
const leaveRoomButton = document.getElementById('leave-room-button');
const userNicknameSpan = document.getElementById('user-nickname');
const gameRoomNameH2 = document.getElementById('game-room-name');
const gameStatusP = document.getElementById('game-status');
const boardDiv = document.getElementById('tic-tac-toe-board');
const chatMessagesDiv = document.getElementById('chat-messages');
const player1Label = document.getElementById('player1-label');
const player1InfoSpan = document.getElementById('player1-info');
const player2Label = document.getElementById('player2-label');
const player2InfoSpan = document.getElementById('player2-info');

const roomCodeInput = document.getElementById('roomCodeInput');
const joinByCodeButton = document.getElementById('join-by-code');
const copyRoomCodeButton = document.getElementById('copy-room-code');

let stompClient = null;
let currentNickname = null;
let currentRoomId = null;
let currentRoomSubscription = null;
let isReady = false;
let mySessionId = null;

function showScreen(screen) {
    nicknameScreen.style.display = 'none';
    lobbyScreen.style.display = 'none';
    gameScreen.style.display = 'none';
    screen.style.display = 'block';
}

function connect() {
    currentNickname = nicknameInput.value.trim();
    if (!currentNickname) { alert('닉네임을 입력해주세요.'); return; }
    if (currentNickname.length > 15) { alert('닉네임은 15자 이하로 입력해주세요.'); return; }
    userNicknameSpan.textContent = currentNickname;
    const socket = new SockJS(WEBSOCKET_URL);
    stompClient = Stomp.over(socket);
    stompClient.connect({}, onConnected, onError);
}

function onConnected(frame) {
    // 1. 서버로부터 세션 ID 응답을 받을 주소를 먼저 구독합니다.
    stompClient.subscribe('/user/queue/session', (payload) => {
        mySessionId = payload.body;
        console.log("세션 ID를 성공적으로 받았습니다:", mySessionId);

        // 3. 세션 ID를 받은 후에 나머지 초기화 로직을 실행합니다.
        showScreen(lobbyScreen);
        stompClient.subscribe('/user/queue/errors', onErrorMessage);
        stompClient.subscribe('/user/queue/room/created', onRoomCreated);
        stompClient.subscribe('/user/queue/room/joined', onRoomJoined);
        stompClient.subscribe('/user/queue/lobby/rooms', onRoomListUpdate);

        // 4. 이제 방 목록을 안전하게 요청할 수 있습니다.
        refreshRooms();
    });

    // 2. 세션 ID를 달라고 서버에 메시지를 보냅니다. (서버의 @MessageMapping("/requestSessionId") 호출)
    stompClient.send("/app/requestSessionId", {});
}

function onError(error) { alert('STOMP 연결에 실패했습니다. Java 서버가 실행 중인지 확인해주세요: ' + error); }
function refreshRooms() { if (stompClient && stompClient.connected) { stompClient.send("/app/lobby/rooms", {}); } }

function onRoomListUpdate(payload) {
    const rooms = JSON.parse(payload.body);
    roomList.innerHTML = '';
    if (rooms.length === 0) {
        roomList.innerHTML = '<p style="text-align: center; color: #888;">생성된 방이 없습니다.</p>';
    } else {
        rooms.forEach(room => {
            const roomItem = document.createElement('div');
            roomItem.className = 'room-item';
            roomItem.innerHTML = `<span>${room.roomName} ( ${room.playerCount}/2 ) - 방장: ${room.hostNickname}</span><button class="join-room-btn" data-room-id="${room.roomId}" ${room.playerCount >= 2 ? 'disabled' : ''}>참여</button>`;
            roomList.appendChild(roomItem);
        });
    }
    document.querySelectorAll('.join-room-btn').forEach(btn => {
        btn.addEventListener('click', (event) => {
            const roomId = event.target.getAttribute('data-room-id');
            joinRoom(roomId);
        });
    });
}

function createRoom() {
    let roomName = roomNameInput.value.trim();
    if (!roomName) {
        roomName = `${currentNickname}님의 방`;
    }
    if (roomName.length > 50) {
        alert('방 제목은 50자 이하로 입력해주세요.');
        return;
    }
    if (currentNickname) {
        stompClient.send("/app/room/create", {}, JSON.stringify({ nickname: currentNickname, roomName: roomName }));
    }
}

function onRoomCreated(payload) { enterGameRoom(JSON.parse(payload.body)); }
function onRoomJoined(payload) { enterGameRoom(JSON.parse(payload.body)); }
function joinRoom(roomId) { if (currentNickname) { stompClient.send("/app/room/join", {}, JSON.stringify({ roomId: roomId, nickname: currentNickname })); } }

function enterGameRoom(room) {
    currentRoomId = room.roomId;
    showScreen(gameScreen);
    gameRoomNameH2.textContent = room.roomName;
    if (currentRoomSubscription) { currentRoomSubscription.unsubscribe(); }
    currentRoomSubscription = stompClient.subscribe('/topic/room/' + currentRoomId, onGameMessage);
    updateRoomState(room);
}

function onGameMessage(payload) {
    const message = JSON.parse(payload.body);
    switch (message.type) {
        case 'JOIN':
        case 'READY':
        case 'GAME_START':
        case 'GAME_UPDATE':
            if (message.content) addChatMessage(message.content, 'system');
            updateRoomState(message.roomState);
            break;
        case 'LEAVE':
        case 'KICK':
            if (message.content) addChatMessage(message.content, 'system');
            updateRoomState(message.roomState);
            isReady = false;
            readyButton.textContent = '준비';
            break;
        case 'CHAT':
            const roleText = message.senderRole === 'HOST' ? '방장' : '손님';
            addChatMessage(`[${roleText}]${message.sender}: ${message.content}`);
            break;
        case 'GAME_END':
            const room = message.roomState;
            const gameResult = room.game;
            let resultText = message.content;
            if (gameResult && gameResult.winnerSessionId) {
                if (gameResult.winnerSessionId === mySessionId) {
                    resultText = "이겼습니다!";
                } else {
                    resultText = "졌습니다!";
                }
            } else if (gameResult && gameResult.gameOver && !gameResult.winnerSessionId) {
                resultText = "무승부입니다!";
            }
            addChatMessage(`게임 종료: ${resultText}`, 'system');
            updateRoomState(room); // 1. 화면 업데이트 요청 (보드 포함)

            // 2. 브라우저가 화면을 그릴 시간을 잠깐 준 뒤에 alert를 실행
            setTimeout(() => {
                alert(resultText);
                readyButton.textContent = '준비';
                readyButton.disabled = false;
                isReady = false;
            }, 50); // 50ms = 0.05초 지연. 사람이 인지하기 어려운 짧은 시간입니다.

            break;
        case 'ERROR':
            alert(message.content);
            break;
    }
    if (message.type === 'LEAVE' && message.content.includes("방장이 나가서")) {
        alert(message.content);
        leaveRoom(true);
    }
}

function updateRoomState(room) {
    if (!room) return;
    currentRoomId = room.roomId;
    const readyPlayerSessionIds = room.readyPlayerSessionIds || [];

    const buildDisplayName = (player) => {
        if (!player) return '대기중...';
        let displayName = player.nickname;
        if (player.role === 'HOST') displayName += ' (방장)'; else displayName += ' (손님)';
        if (player.sessionId === mySessionId) displayName += ' (나)';
        if (readyPlayerSessionIds.includes(player.sessionId)) displayName += ' (준비완료)';
        return displayName;
    };

    [player1InfoSpan, player2InfoSpan].forEach(span => {
        span.classList.remove('ready');
        span.dataset.sessionId = '';
        span.textContent = '대기중...';
    });

    const amIHost = room.players.some(p => p.sessionId === mySessionId && p.role === 'HOST');

    if (room.gameState === 'PLAYING' && room.game) {
        const { playerXSessionId, playerOSessionId, currentPlayerSessionId } = room.game;
        const playerX = room.players.find(p => p.sessionId === playerXSessionId);
        const playerO = room.players.find(p => p.sessionId === playerOSessionId);

        player1Label.textContent = "Player X:";
        player1InfoSpan.dataset.sessionId = playerX.sessionId;
        player1InfoSpan.textContent = buildDisplayName(playerX);
        player2Label.textContent = "Player O:";
        player2InfoSpan.dataset.sessionId = playerO.sessionId;
        player2InfoSpan.textContent = buildDisplayName(playerO);

        if (currentPlayerSessionId === mySessionId) {
            gameStatusP.textContent = "⚔️ 나의 턴입니다.";
            gameStatusP.style.color = "#28a745";
        } else {
            gameStatusP.textContent = "⏳ 상대방 턴입니다.";
            gameStatusP.style.color = "#dc3545";
        }
        readyButton.disabled = true;

    } else {
        const player1 = room.players.find(p => p.role === 'HOST') || room.players[0];
        const player2 = room.players.find(p => player1 && p.sessionId !== player1.sessionId);

        player1Label.textContent = "참가자 1:";
        if (player1) {
            player1InfoSpan.dataset.sessionId = player1.sessionId;
            player1InfoSpan.textContent = buildDisplayName(player1);
        }
        player2Label.textContent = "참가자 2:";
        if (player2) {
            player2InfoSpan.dataset.sessionId = player2.sessionId;
            player2InfoSpan.textContent = buildDisplayName(player2);
        }

        gameStatusP.textContent = "상대를 기다리거나, 준비 버튼을 눌러주세요.";
        gameStatusP.style.color = "#333";
        readyButton.disabled = room.players.length < 2;
    }

    [player1InfoSpan, player2InfoSpan].forEach(span => {
        if(readyPlayerSessionIds.includes(span.dataset.sessionId)) {
            span.classList.add('ready');
        }
    });

    kickButton.style.display = amIHost && room.players.length > 1 && room.gameState !== 'PLAYING' ? 'inline-block' : 'none';
    renderBoard(room);
}

function renderBoard(room) {
    boardDiv.innerHTML = '';
    const game = room.game;
    for (let i = 0; i < 9; i++) {
        const cell = document.createElement('div');
        cell.className = 'cell';
        const cellValue = game ? game.board[i] : null;
        cell.textContent = cellValue || '';
        if (room.gameState === 'PLAYING' && game && game.currentPlayerSessionId === mySessionId && !cellValue) {
            cell.classList.add('clickable');
            cell.addEventListener('click', () => makeMove(i));
        }
        boardDiv.appendChild(cell);
    }
}

function makeMove(index) { stompClient.send(`/app/room/${currentRoomId}`, {}, JSON.stringify({ type: 'MOVE', move: { index: index } })); }
function toggleReady() { isReady = !isReady; const type = isReady ? 'READY' : 'UNREADY'; readyButton.textContent = isReady ? '준비 취소' : '준비'; stompClient.send(`/app/room/${currentRoomId}`, {}, JSON.stringify({ type: type })); }
function sendChatMessage() { const messageContent = chatInput.value; if (messageContent.length > 255) { alert('채팅 메시지는 255자를 초과할 수 없습니다.'); return; } if (messageContent.trim() && stompClient) { stompClient.send(`/app/room/${currentRoomId}`, {}, JSON.stringify({ type: 'CHAT', content: messageContent, sender: currentNickname })); chatInput.value = ''; } }
function addChatMessage(message, type = 'normal') { const p = document.createElement('p'); p.textContent = message; if (type === 'system') { p.style.fontStyle = 'italic'; p.style.color = '#555'; } chatMessagesDiv.appendChild(p); chatMessagesDiv.scrollTop = chatMessagesDiv.scrollHeight; }
function onErrorMessage(payload) { const error = JSON.parse(payload.body); alert(error.content); }
function kickPlayer() {
    let opponentSessionId = null;
    if (player1InfoSpan.dataset.sessionId && player1InfoSpan.dataset.sessionId !== mySessionId) {
        opponentSessionId = player1InfoSpan.dataset.sessionId;
    } else if (player2InfoSpan.dataset.sessionId && player2InfoSpan.dataset.sessionId !== mySessionId) {
        opponentSessionId = player2InfoSpan.dataset.sessionId;
    }
    if (opponentSessionId) {
        const opponentInfoSpan = (player1InfoSpan.dataset.sessionId === opponentSessionId) ? player1InfoSpan : player2InfoSpan;
        const opponentNickname = opponentInfoSpan.textContent.split(' ')[0];
        if(confirm(`${opponentNickname}님을 추방하시겠습니까?`)) {
            stompClient.send(`/app/room/${currentRoomId}`, {}, JSON.stringify({ type: 'KICK', kickTargetSessionId: opponentSessionId }));
        }
    }
}
function leaveRoom(silent = false) { if (stompClient && stompClient.connected) { stompClient.disconnect(() => {}); } resetStateAndUI(silent); }
function resetStateAndUI(silent) {
    const previousNickname = currentNickname;
    if (!silent) { alert("방에서 나왔습니다."); }
    stompClient = null; currentRoomId = null; currentRoomSubscription = null; isReady = false; mySessionId = null;
    showScreen(lobbyScreen);
    chatMessagesDiv.innerHTML = ''; boardDiv.innerHTML = ''; readyButton.textContent = '준비'; kickButton.style.display = 'none';
    nicknameInput.value = previousNickname;
    connect();
}

function joinByCode() {
    const roomCode = roomCodeInput.value.trim();
    if (roomCode) {
        joinRoom(roomCode);
    } else {
        alert('방 코드를 입력해주세요.');
    }
}

function copyRoomCode() {
    if (currentRoomId) {
        navigator.clipboard.writeText(currentRoomId).then(() => {
            alert('방 코드가 클립보드에 복사되었습니다.');
        }).catch(err => {
            console.error('클립보드 복사 실패:', err);
            alert('클립보드 복사에 실패했습니다.');
        });
    }
}

enterLobbyButton.addEventListener('click', connect);
createRoomButton.addEventListener('click', createRoom);
refreshRoomsButton.addEventListener('click', refreshRooms);
sendChatButton.addEventListener('click', sendChatMessage);
readyButton.addEventListener('click', toggleReady);
kickButton.addEventListener('click', kickPlayer);
leaveRoomButton.addEventListener('click', () => leaveRoom(false));
joinByCodeButton.addEventListener('click', joinByCode);
copyRoomCodeButton.addEventListener('click', copyRoomCode);

chatInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') sendChatMessage(); });
nicknameInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') connect(); });
roomCodeInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') joinByCode(); });