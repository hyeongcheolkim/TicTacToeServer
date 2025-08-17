package kim.hyeongcheol.tictactoeserver.service;

import kim.hyeongcheol.tictactoeserver.dto.*;
import kim.hyeongcheol.tictactoeserver.model.GameRoom;
import kim.hyeongcheol.tictactoeserver.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameRoomRepository gameRoomRepository;

    private RoomStateDto mapToRoomStateDto(GameRoom room) {
        if (room == null) return null;

        List<PlayerInfo> playerInfos = room.getPlayers().entrySet().stream()
                .map(entry -> {
                    String sessionId = entry.getKey();
                    String nickname = entry.getValue();
                    PlayerRole role = sessionId.equals(room.getHostSessionId()) ? PlayerRole.HOST : PlayerRole.GUEST;
                    return PlayerInfo.builder()
                            .sessionId(sessionId)
                            .nickname(nickname)
                            .role(role)
                            .build();
                })
                .toList();

        return RoomStateDto.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .hostNickname(room.getHostNickname())
                .players(playerInfos)
                .readyPlayerSessionIds(room.getReadyPlayerSessionIds())
                .game(TicTacToeGameDto.of(room.getGame()))
                .gameState(room.getGameState())
                .build();
    }

    public void getRoomList(String sessionId) {
        List<RoomInfo> roomInfos = gameRoomRepository.findAll().stream()
                .map(room -> RoomInfo.builder()
                        .roomId(room.getRoomId())
                        .roomName(room.getRoomName())
                        .playerCount((long) room.getPlayers().size())
                        .hostNickname(room.getHostNickname())
                        .build())
                .toList();
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/lobby/rooms", roomInfos, createHeaders(sessionId));
    }

    public void createRoom(String roomName, String nickname, String sessionId) {
        if (nickname == null || nickname.trim().isEmpty() || nickname.length() > 15) {
            sendError(sessionId, "닉네임은 1자 이상 15자 이하로 입력해주세요.");
            return;
        }
        if (roomName == null || roomName.trim().isEmpty() || roomName.length() > 50) {
            sendError(sessionId, "방 제목은 1자 이상 50자 이하로 입력해주세요.");
            return;
        }
        if (gameRoomRepository.findRoomIdBySessionId(sessionId).isPresent()) {
            sendError(sessionId, "이미 참여 중인 방이 있습니다. 방을 나오신 후 다시 시도해주세요.");
            return;
        }
        String roomId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(roomId, roomName, nickname, sessionId);
        gameRoomRepository.save(room);
        gameRoomRepository.linkSessionToRoom(sessionId, roomId);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/room/created", mapToRoomStateDto(room), createHeaders(sessionId));
    }

    public void joinRoom(String roomId, String nickname, String sessionId) {
        if (nickname == null || nickname.trim().isEmpty() || nickname.length() > 15) {
            sendError(sessionId, "닉네임은 1자 이상 15자 이하로 입력해주세요.");
            return;
        }
        GameRoom room = gameRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            sendError(sessionId, "존재하지 않는 방입니다.");
            return;
        }
        if (gameRoomRepository.findRoomIdBySessionId(sessionId).isPresent()) {
            sendError(sessionId, "이미 참여 중인 방이 있습니다. 방을 나오신 후 다시 시도해주세요.");
            return;
        }
        if (room.getPlayers().size() >= 2) {
            sendError(sessionId, "방이 꽉 찼습니다.");
            return;
        }
        room.addPlayer(sessionId, nickname);
        gameRoomRepository.linkSessionToRoom(sessionId, roomId);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/room/joined", mapToRoomStateDto(room), createHeaders(sessionId));
        GameMessage broadcastMessage = GameMessage.builder()
                .type(GameMessage.MessageType.JOIN)
                .sender(nickname)
                .content(nickname + "님이 입장했습니다.")
                .roomState(mapToRoomStateDto(room))
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + roomId, broadcastMessage);
    }

    public void processMessage(String roomId, GameMessage message, String sessionId) {
        gameRoomRepository.findById(roomId).ifPresent(room -> {
            String nickname = room.getPlayers().get(sessionId);
            if (nickname == null) return;

            switch (message.getType()) {
                case CHAT -> handleChatMessage(room, message, nickname, sessionId);
                case READY, UNREADY -> handleReady(room, sessionId, message.getType());
                case MOVE -> handleMove(room, sessionId, message.getMove().getIndex());
                case KICK -> handleKick(room, nickname, message.getKickTargetSessionId(), sessionId);
            }
        });
    }

    public void handleDisconnect(String sessionId) {
        gameRoomRepository.findRoomIdBySessionId(sessionId).ifPresent(roomId -> {
            gameRoomRepository.unlinkSessionFromRoom(sessionId);
            GameRoom room = gameRoomRepository.findById(roomId).orElse(null);
            if (room == null) return;

            String nickname = room.getPlayers().get(sessionId);
            if (nickname == null) return;

            if (sessionId.equals(room.getHostSessionId())) {
                gameRoomRepository.deleteById(roomId);
                room.getPlayers().keySet().stream()
                        .filter(sid -> !sid.equals(sessionId))
                        .forEach(gameRoomRepository::unlinkSessionFromRoom);
                GameMessage message = GameMessage.builder()
                        .roomId(roomId)
                        .type(GameMessage.MessageType.LEAVE)
                        .content("방장이 나가서 방이 사라졌습니다.")
                        .sender("SYSTEM")
                        .build();
                messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
            } else {
                if (room.getGameState() == GameRoom.GameState.PLAYING) {
                    room.setGameState(GameRoom.GameState.FINISHED);
                    room.getReadyPlayerSessionIds().clear();
                    GameMessage winMessage = GameMessage.builder()
                            .roomId(roomId)
                            .type(GameMessage.MessageType.GAME_END)
                            .content("상대방이 나가서 승리했습니다!")
                            .roomState(mapToRoomStateDto(room))
                            .build();
                    messagingTemplate.convertAndSend("/topic/room/" + roomId, winMessage);
                }
                room.removePlayer(sessionId);
                room.resetForRematch();
                GameMessage leaveMessage = GameMessage.builder()
                        .roomId(roomId)
                        .type(GameMessage.MessageType.LEAVE)
                        .content(nickname + "님이 나갔습니다.")
                        .sender(nickname)
                        .roomState(mapToRoomStateDto(room))
                        .build();
                messagingTemplate.convertAndSend("/topic/room/" + roomId, leaveMessage);
            }
        });
    }

    private void handleChatMessage(GameRoom room, GameMessage message, String nickname, String sessionId) {
        if (message.getContent() == null || message.getContent().length() > 255) {
            sendError(sessionId, "채팅 메시지는 255자를 초과할 수 없습니다.");
            return;
        }
        PlayerRole senderRole = sessionId.equals(room.getHostSessionId()) ? PlayerRole.HOST : PlayerRole.GUEST;
        message.setSenderRole(senderRole);
        message.setSender(nickname);
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), message);
    }

    private void handleReady(GameRoom room, String sessionId, GameMessage.MessageType type) {
        if (type == GameMessage.MessageType.READY) {
            room.getReadyPlayerSessionIds().add(sessionId);
        } else {
            room.getReadyPlayerSessionIds().remove(sessionId);
        }
        String nickname = room.getPlayers().get(sessionId);
        GameMessage statusUpdate = GameMessage.builder()
                .roomId(room.getRoomId())
                .type(GameMessage.MessageType.READY)
                .sender(nickname)
                .roomState(mapToRoomStateDto(room))
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), statusUpdate);
        if (room.getPlayers().size() == 2 && room.getReadyPlayerSessionIds().size() == 2) {
            if (room.getGameState() == GameRoom.GameState.FINISHED) {
                room.resetForRematch();
            }
            room.startGame();
            String currentPlayerSessionId = room.getGame().getCurrentPlayerSessionId();
            String currentPlayerNickname = room.getPlayers().get(currentPlayerSessionId);
            PlayerRole startPlayerRole = currentPlayerSessionId.equals(room.getHostSessionId()) ? PlayerRole.HOST : PlayerRole.GUEST;
            String roleText = startPlayerRole == PlayerRole.HOST ? "방장" : "손님";
            String startContent = "게임이 시작됩니다! [" + roleText + "]" + currentPlayerNickname + "님부터 시작하세요.";
            GameMessage startMessage = GameMessage.builder()
                    .roomId(room.getRoomId())
                    .type(GameMessage.MessageType.GAME_START)
                    .content(startContent)
                    .roomState(mapToRoomStateDto(room))
                    .build();
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), startMessage);
        }
    }

    private void handleMove(GameRoom room, String sessionId, int index) {
        if (room.getGame() == null || room.getGameState() != GameRoom.GameState.PLAYING) return;
        boolean moveResult = room.getGame().makeMove(index, sessionId);
        if (moveResult) {
            GameMessage.GameMessageBuilder gameUpdateBuilder = GameMessage.builder().roomId(room.getRoomId());
            if (room.getGame().isGameOver()) {
                room.setGameState(GameRoom.GameState.FINISHED);
                room.getReadyPlayerSessionIds().clear();
                String winnerNickname = null;
                if (room.getGame().getWinnerSessionId() != null) {
                    winnerNickname = room.getPlayers().get(room.getGame().getWinnerSessionId());
                }
                gameUpdateBuilder.type(GameMessage.MessageType.GAME_END)
                        .content(winnerNickname != null ? winnerNickname + "님의 승리!" : "무승부입니다!");
            } else {
                gameUpdateBuilder.type(GameMessage.MessageType.GAME_UPDATE);
            }
            GameMessage gameUpdate = gameUpdateBuilder.roomState(mapToRoomStateDto(room)).build();
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), gameUpdate);
        } else {
            sendError(sessionId, "지금은 당신의 턴이 아니거나, 둘 수 없는 위치입니다.");
        }
    }

    private void handleKick(GameRoom room, String kickerNickname, String targetSessionId, String kickerSessionId) {
        if (room.getGameState() == GameRoom.GameState.PLAYING) {
            sendError(kickerSessionId, "게임 중에는 상대를 추방할 수 없습니다.");
            return;
        }
        if (!kickerSessionId.equals(room.getHostSessionId())) return;
        if (targetSessionId != null && room.getPlayers().containsKey(targetSessionId)) {
            String targetNickname = room.getPlayers().get(targetSessionId);
            room.removePlayer(targetSessionId);
            gameRoomRepository.unlinkSessionFromRoom(targetSessionId);
            room.resetForRematch();
            GameMessage kickMessage = GameMessage.builder()
                    .roomId(room.getRoomId())
                    .type(GameMessage.MessageType.KICK)
                    .sender(kickerNickname)
                    .content(targetNickname + "님이 방장에 의해 강퇴당했습니다.")
                    .roomState(mapToRoomStateDto(room))
                    .build();
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), kickMessage);
        }
    }

    private void sendError(String sessionId, String content) {
        GameMessage errorMsg = GameMessage.builder()
                .type(GameMessage.MessageType.ERROR)
                .content(content)
                .build();
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg, createHeaders(sessionId));
    }

    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        if (sessionId != null) {
            headerAccessor.setSessionId(sessionId);
        }
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }
}