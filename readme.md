# 실시간 틱택토 웹 애플리케이션 (Spring Boot & WebSocket)

이 프로젝트는 Java Spring Boot로 작성된 실시간 멀티플레이어 틱택토 게임입니다. 서버와 클라이언트 모델을 사용하며, 실시간 양방향 통신을 위해 WebSocket(STOMP)을 활용합니다. 로비, 게임방 생성 및 참여, 실시간 채팅 기능을 지원합니다.

## 시연영상
- 플레이시연
![play](https://github.com/user-attachments/assets/0c02020b-b723-4a0c-bcc5-840d5dd8ba5b)
- 채팅시연
![chat](https://github.com/user-attachments/assets/6ab6b1a2-b6e4-40db-a4ab-90d236707aca)
- 추방시연
![kick](https://github.com/user-attachments/assets/638b5577-25a4-45af-b214-fad7df11c741)

## 요구 사항
- **Backend**:
    - Java 21
    - Spring Boot 3.x
    - Spring WebSocket (with STOMP)
    - Gradle
- **Frontend**:
    - SockJS 라이브러리
    - Stomp.js 라이브러리

## 기능
- **실시간 멀티플레이어**: WebSocket을 통해 두 명의 사용자가 실시간으로 틱택토 게임을 진행합니다.
- **로비 시스템**: 현재 생성된 게임방 목록을 확인하고, 방 제목을 설정하여 새로운 방을 만들 수 있습니다.
- **게임방 관리**: 방 목록을 통해 참여하거나, 고유한 방 코드를 통해 특정 방에 참여할 수 있습니다.
- **실시간 채팅**: 게임방 내의 사용자들이 메시지를 주고받을 수 있습니다.
- **준비/시작 시스템**: 방 안의 모든 사용자가 '준비' 상태가 되어야 게임이 시작됩니다.
- **연결 관리**: 사용자의 예기치 않은 연결 종료를 감지하고 안정적으로 상황을 처리합니다.



## 코드 설명

### 클라이언트 (프론트엔드 측)

1.  **서버 연결 및 초기화**:
    - `SockJS`와 `Stomp.js` 라이브러리를 사용하여 서버의 WebSocket 엔드포인트(`/ws`)로 비동기 연결을 수립합니다.
    - 연결 성공 시, 서버에 세션 ID를 요청하고 받아와 클라이언트의 고유 식별자로 사용합니다.
2.  **화면 관리**:
    - 닉네임 입력, 로비, 게임방의 세 가지 주요 화면을 두고, `display` 속성을 제어하여 SPA(Single Page Application)처럼 동작하도록 구현했습니다.
3.  **상태 동기화**:
    - 게임방에 입장하면 해당 방의 고유 토픽 (`/topic/room/{roomId}`)을 구독합니다.
    - 서버에서 `RoomStateDto` 객체를 받을 때마다, 이 객체의 데이터를 기반으로 게임 보드, 플레이어 정보, 준비 상태, 채팅 등 화면 전체를 최신 상태로 갱신합니다.
4.  **사용자 입력 처리**:
    - 사용자가 게임 보드의 칸을 클릭하거나 '준비', '채팅 전송' 등의 버튼을 누르면, 해당 행동에 맞는 메시지를 JSON 형식으로 구성하여 `stompClient.send()`를 통해 서버의 `@MessageMapping` 주소로 전송합니다.

### 서버 (서버 측)

1.  **WebSocket 엔드포인트 설정**:
    - `WebSocketConfiguration`에서 STOMP 프로토콜 엔드포인트와 메시지 브로커를 설정합니다.
    - 클라이언트로부터의 메시지 발행 주소 Prefix는 `/app`으로, 구독 주소 Prefix는 `/topic`, `/queue`로 지정합니다.
2.  **메시지 핸들링**:
    - `@MessageMapping` 어노테이션을 사용하여 클라이언트의 메시지 발행 주소에 따라 `GameController`의 적절한 핸들러 메서드가 호출되도록 합니다.
    - Controller는 받은 메시지를 `GameService`로 전달하여 비즈니스 로직 처리를 위임합니다.
3.  **상태 및 데이터 관리**:
    - **Repository 패턴**을 적용하여 게임방 데이터 관리 로직을 `GameService`로부터 분리했습니다. `MemoryGameRoomRepository`가 메모리 상에서 게임방 목록과 사용자 세션 정보를 관리합니다.
4.  **핵심 도메인 로직**:
    - `domain` 패키지의 `GameRoom`과 `TicTacToeGame` 클래스가 핵심 비즈니스 로직을 담당합니다. `GameRoom`은 플레이어, 준비 상태 등 방의 상태를 관리하고, `TicTacToeGame`은 틱택토 게임의 규칙과 진행을 책임집니다.
5.  **연결 종료 감지**:
    - `WebSocketEventListener`가 Spring의 `SessionDisconnectEvent`를 감지하여 사용자의 연결 종료를 처리합니다. 방장과 손님을 구분하여 방을 삭제하거나 게임을 종료시키는 등 안정적인 후속 처리를 수행합니다.

## 빌드 및 실행 방법

1.  프로젝트를 Git clone 받습니다.
2.  IDE(IntelliJ 등)에서 Gradle 프로젝트로 빌드합니다.
3.  `TicTacToeServerApplication.java`의 `main` 메서드를 실행하여 서버를 구동합니다. (기본 포트: 8080)
4.  웹 브라우저에서 `resources/static/index.html` 파일을 엽니다.
5.  여러 개의 브라우저 창이나 탭을 열어 다중 클라이언트를 시뮬레이션할 수 있습니다.
6.  닉네임을 입력하고 로비에 입장하여 방을 만들거나 기존 방에 참여하여 게임을 시작합니다.