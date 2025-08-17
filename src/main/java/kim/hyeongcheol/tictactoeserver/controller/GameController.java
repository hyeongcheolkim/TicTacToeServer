package kim.hyeongcheol.tictactoeserver.controller;

import kim.hyeongcheol.tictactoeserver.dto.GameMessage;
import kim.hyeongcheol.tictactoeserver.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @MessageMapping("/requestSessionId")
    @SendToUser("/queue/session")
    public String getSessionId(SimpMessageHeaderAccessor headerAccessor) {
        return headerAccessor.getSessionId();
    }

    @MessageMapping("/lobby/rooms")
    public void getRoomList(SimpMessageHeaderAccessor headerAccessor) {
        gameService.getRoomList(headerAccessor.getSessionId());
    }

    @MessageMapping("/room/create")
    public void createRoom(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String roomName = payload.get("roomName");
        String nickname = payload.get("nickname");
        gameService.createRoom(roomName, nickname, headerAccessor.getSessionId());
    }

    @MessageMapping("/room/join")
    public void joinRoom(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String roomId = payload.get("roomId");
        String nickname = payload.get("nickname");
        gameService.joinRoom(roomId, nickname, headerAccessor.getSessionId());
    }

    @MessageMapping("/room/{roomId}")
    public void handleGameMessage(@DestinationVariable String roomId, @Payload GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        gameService.processMessage(roomId, message, headerAccessor.getSessionId());
    }
}